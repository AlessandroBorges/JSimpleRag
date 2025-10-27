package bor.tools.simplerag.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.dto.SearchRequest;
import bor.tools.simplerag.dto.SearchResponse;
import bor.tools.simplerag.dto.SearchResultDTO;
import bor.tools.simplerag.dto.SemanticSearchRequest;
import bor.tools.simplerag.dto.TextualSearchRequest;
import bor.tools.simplerag.entity.Chapter;
import bor.tools.simplerag.entity.DocumentEmbedding;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.repository.ChapterRepository;
import bor.tools.simplerag.repository.DocEmbeddingJdbcRepository;
import bor.tools.simplerag.repository.DocumentoRepository;
import bor.tools.simplerag.service.LibraryService;
import bor.tools.simplerag.service.embedding.EmbeddingService;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for RAG search operations.
 * Primary endpoint of the application - executes hybrid search (semantic + textual).
 *
 * <h2>Query Syntax Guide</h2>
 *
 * <p>Text queries use PostgreSQL's <code>websearch_to_tsquery</code> with Portuguese
 * language support and custom OR-expansion for broader results.</p>
 *
 * <h3>Basic Syntax</h3>
 * <table border="1">
 *   <tr>
 *     <th>User Query</th>
 *     <th>Meaning</th>
 *     <th>Example Results</th>
 *   </tr>
 *   <tr>
 *     <td><code>café leite</code></td>
 *     <td>OR search (any word)</td>
 *     <td>Documents with "café" OR "leite"</td>
 *   </tr>
 *   <tr>
 *     <td><code>"pão quente"</code></td>
 *     <td>Exact phrase</td>
 *     <td>Only "pão quente" adjacent</td>
 *   </tr>
 *   <tr>
 *     <td><code>café -açúcar</code></td>
 *     <td>Exclusion</td>
 *     <td>"café" WITHOUT "açúcar"</td>
 *   </tr>
 * </table>
 *
 * <h3>Language Features</h3>
 * <ul>
 *   <li><b>Accent-insensitive</b>: "café" matches "cafe", "açúcar" matches "acucar"</li>
 *   <li><b>Stemming</b>: "trabalho" matches "trabalhar", "trabalhando", etc.</li>
 *   <li><b>Case-insensitive</b>: "CAFÉ" matches "café" matches "Café"</li>
 * </ul>
 *
 * <h3>Ranking Logic</h3>
 * <p>Results are ranked by PostgreSQL's <code>ts_rank_cd</code> which considers:</p>
 * <ol>
 *   <li>Number of matching terms (more = higher score)</li>
 *   <li>Term frequency in document</li>
 *   <li>Position weighting (metadata fields have different weights)</li>
 *   <li>Term proximity (closer terms = higher score)</li>
 * </ol>
 *
 * <h3>Metadata Weighting</h3>
 * <table border="1">
 *   <tr><th>Field</th><th>Weight</th><th>Example</th></tr>
 *   <tr><td>Title, Chapter name</td><td>A (highest)</td><td>Document title</td></tr>
 *   <tr><td>Description</td><td>B</td><td>Document summary</td></tr>
 *   <tr><td>Keywords, Content</td><td>C</td><td>Main text, tags</td></tr>
 *   <tr><td>Author, Other metadata</td><td>D (lowest)</td><td>Author name</td></tr>
 * </table>
 *
 * <h3>Search Weights</h3>
 * <p>Hybrid search combines semantic and textual scores:</p>
 * <pre>
 * final_score = (semantic_score × pesoSemantico) + (textual_score × pesoTextual)
 *
 * where: pesoSemantico + pesoTextual = 1.0
 * </pre>
 *
 * <p><b>Recommendations</b>:</p>
 * <ul>
 *   <li>Technical content: 70% semantic, 30% textual (favor meaning over keywords)</li>
 *   <li>Legal documents: 40% semantic, 60% textual (favor exact terminology)</li>
 *   <li>General content: 60% semantic, 40% textual (balanced approach)</li>
 * </ul>
 *
 * @see DocEmbeddingJdbcRepository#pesquisaHibrida
 * @see DocEmbeddingJdbcRepository#query_phraseto_websearch
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RAG Search", description = "Hybrid search endpoints (semantic + textual)")
public class SearchController {

    private final DocEmbeddingJdbcRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    private final DocumentoRepository documentoRepository;
    private final ChapterRepository chapterRepository;
    private final LibraryService libraryService;

    /**
     * Hybrid search combining semantic (embedding-based) and textual (full-text) search
     */
    @PostMapping("/hybrid")
    @Operation(
        summary = "Hybrid search (semantic + textual)",
        description = """
            Executes combined semantic (embedding-based) and textual (full-text) search.

            **Query Syntax** (powered by websearch_to_tsquery + Portuguese stemming):
            - `café leite` → Searches for documents with ANY word (OR logic)
            - `"pão quente"` → Searches for exact phrase
            - `café -açúcar` → Searches for café WITHOUT açúcar (exclusion)

            **Language Features**:
            - Accent-insensitive: café = cafe, açúcar = acucar
            - Portuguese stemming: trabalho = trabalhar = trabalhando
            - Weighted by metadata: titles > descriptions > content

            **Performance**: Typical response < 50ms for 10k documents
            """,
        tags = {"Search"}
    )
    public ResponseEntity<SearchResponse> hybridSearch(@Valid @RequestBody SearchRequest request) {
        log.info("Hybrid search: query='{}', libraries={}, limit={}",
                request.getQuery(), request.getLibraryIds(), request.getLimit());

        long startTime = System.currentTimeMillis();

        try {
            // Validate query
            validateTextQuery(request.getQuery());

            // Validate weights
            if (!request.isWeightValid()) {
                throw new IllegalArgumentException(
                        String.format("Pesos inválidos: semântico=%.2f + textual=%.2f != 1.0",
                                request.getPesoSemantico(), request.getPesoTextual())
                );
            }

            LibraryDTO library = loadLibrary(request.getLibraryIds());

	    // Create embedding context with library defaults
	    EmbeddingContext context = EmbeddingContext.fromLibrary(library);

	    // Generate query embedding using new service
            float[] queryEmbedding = embeddingService.generateQueryEmbedding(request.getQuery(), context);

            // Execute hybrid search
            List<DocumentEmbedding> embeddings = embeddingRepository.pesquisaHibrida(
                    queryEmbedding,
                    request.getQuery(),
                    request.getLibraryIds(),
                    request.getLimit(),
                    request.getPesoSemantico(),
                    request.getPesoTextual()
            );

            // Enrich results with document/chapter information
            List<SearchResultDTO> results = enrichResults(embeddings);

            // Build response
            SearchResponse response = SearchResponse.from(
                    request.getQuery(),
                    request.getLibraryIds(),
                    request.getPesoSemantico(),
                    request.getPesoTextual(),
                    results
            );

            long executionTime = System.currentTimeMillis() - startTime;
            response.setExecutionTimeMs(executionTime);

            log.info("Hybrid search completed: {} results in {}ms", results.size(), executionTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in hybrid search: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao executar pesquisa híbrida: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the library details for the first library ID specified.
     * Throws exception if no libraries specified or library not found.
     */	
    private LibraryDTO loadLibrary(Integer[] libraryIds) {
	if (libraryIds == null || libraryIds.length == 0)
	    throw new IllegalArgumentException("Nenhuma biblioteca especificada para a pesquisa");
	    
	var opt = libraryService.findById(libraryIds[0]);
	Library lib = opt.orElseThrow(
		() -> new IllegalArgumentException("Biblioteca não encontrada: " + libraryIds[0])
		);
	
	return LibraryDTO.from(lib);
    }

    /**
     * Validates text query and provides user-friendly error messages.
     *
     * @param query - user query string
     * @throws IllegalArgumentException with helpful message if invalid
     *
     * @TODO Add comprehensive unit tests for query validation (Phase 4)
     *       - Test empty/null queries
     *       - Test SQL operator detection (AND/OR/NOT)
     *       - Test query length validation (min 2, max 500 chars)
     *       - Test special characters handling
     */
    private void validateTextQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Query não pode ser vazia. " +
                "Exemplos válidos: 'café leite', '\"pão quente\"', 'café -açúcar'"
            );
        }

        // Check for common mistakes - SQL operators
        if (query.contains("AND") || query.contains("OR") || query.contains("NOT")) {
            log.warn("Query contains SQL operators (will be normalized): {}", query);
            throw new IllegalArgumentException(
                "Não use operadores SQL (AND/OR/NOT). Use sintaxe web: " +
                "\n- Para OR: 'café leite' (busca qualquer palavra)" +
                "\n- Para AND: '\"café com leite\"' (frase exata)" +
                "\n- Para NOT: 'café -açúcar' (exclusão)"
            );
        }

        // Check if query is too short
        if (query.trim().length() < 2) {
            throw new IllegalArgumentException(
                "Query muito curta. Use pelo menos 2 caracteres."
            );
        }

        // Check if query is too long (> 500 chars)
        if (query.length() > 500) {
            throw new IllegalArgumentException(
                "Query muito longa (máximo 500 caracteres). " +
                "Use termos mais específicos ou divida em múltiplas pesquisas."
            );
        }
    }

    /**
     * Semantic search (embedding-based only)
     */
    @PostMapping("/semantic")
    @Operation(
        summary = "Semantic search only",
        description = """
            Executes pure semantic search using vector embeddings (no text search).

            **When to use**:
            - Looking for conceptual/semantic similarity
            - Want to find paraphrases or synonyms
            - Query is a question or description (not keywords)

            **How it works**:
            1. Query text is converted to embedding vector
            2. Vector similarity search finds closest embeddings
            3. Results ranked by cosine similarity

            **Advantages over textual**:
            - Finds semantically similar content (not just keywords)
            - Language-agnostic (works across languages with multilingual models)
            - Better for natural language questions

            **Disadvantages**:
            - Slower (requires embedding generation)
            - May miss exact keyword matches
            - Requires embedding model

            **Note**: Query syntax doesn't apply here (no text search operators)
            """,
        tags = {"Search"}
    )
    public ResponseEntity<SearchResponse> semanticSearch(@Valid @RequestBody SemanticSearchRequest request) {
        log.info("Semantic search: query='{}', libraries={}, limit={}",
                request.getQuery(), request.getLibraryIds(), request.getLimit());

        long startTime = System.currentTimeMillis();

        try {
            // Generate query embedding
            LibraryDTO library = loadLibrary(request.getLibraryIds());

	    // Create embedding context with library defaults
	    EmbeddingContext context = EmbeddingContext.fromLibrary(library);

	    // Generate query embedding using new service
            float[] queryEmbedding = embeddingService.generateQueryEmbedding(request.getQuery(), context);

            // Execute semantic search
            List<DocumentEmbedding> embeddings = embeddingRepository.pesquisaSemantica(
                    queryEmbedding,
                    request.getLibraryIds(),
                    request.getLimit()
            );

            // Enrich results
            List<SearchResultDTO> results = enrichResults(embeddings);

            // Build response
            SearchResponse response = SearchResponse.from(
                    request.getQuery(),
                    request.getLibraryIds(),
                    1.0f, // pesoSemantico
                    0.0f, // pesoTextual
                    results
            );

            long executionTime = System.currentTimeMillis() - startTime;
            response.setExecutionTimeMs(executionTime);

            log.info("Semantic search completed: {} results in {}ms", results.size(), executionTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in semantic search: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao executar pesquisa semântica: " + e.getMessage(), e);
        }
    }

    /**
     * Textual search (full-text search only)
     */
    @PostMapping("/textual")
    @Operation(
        summary = "Textual search only",
        description = """
            Executes pure textual (full-text) search using PostgreSQL's text search features.

            **When to use**:
            - Looking for specific keywords or phrases
            - Don't have embedding model available
            - Want exact terminology matching

            **Query Syntax**: Same as hybrid search
            - `café leite` → OR search (any word)
            - `"pão quente"` → Exact phrase
            - `café -açúcar` → Exclusion

            **Advantages over semantic**:
            - Faster (no embedding generation)
            - Exact keyword matching
            - Better for acronyms and proper nouns

            **Disadvantages**:
            - No semantic understanding (synonyms, paraphrases)
            - Language-dependent (Portuguese stemming only)
            """,
        tags = {"Search"}
    )
    public ResponseEntity<SearchResponse> textualSearch(@Valid @RequestBody TextualSearchRequest request) {
        log.info("Textual search: query='{}', libraries={}, limit={}",
                request.getQuery(), request.getLibraryIds(), request.getLimit());

        long startTime = System.currentTimeMillis();

        try {
            // Validate query
            validateTextQuery(request.getQuery());

            // Execute textual search
            List<DocumentEmbedding> embeddings = embeddingRepository.pesquisaTextual(
                    request.getQuery(),
                    request.getLibraryIds(),
                    request.getLimit()
            );

            // Enrich results
            List<SearchResultDTO> results = enrichResults(embeddings);

            // Build response
            SearchResponse response = SearchResponse.from(
                    request.getQuery(),
                    request.getLibraryIds(),
                    0.0f, // pesoSemantico
                    1.0f, // pesoTextual
                    results
            );

            long executionTime = System.currentTimeMillis() - startTime;
            response.setExecutionTimeMs(executionTime);

            log.info("Textual search completed: {} results in {}ms", results.size(), executionTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in textual search: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao executar pesquisa textual: " + e.getMessage(), e);
        }
    }

    /**
     * Enriches search results with document and chapter information
     */
    private List<SearchResultDTO> enrichResults(List<DocumentEmbedding> embeddings) {
        // Collect all unique documento IDs and chapter IDs
        List<Integer> documentoIds = embeddings.stream()
                .map(DocumentEmbedding::getDocumentoId)
                .distinct()
                .collect(Collectors.toList());

        List<Integer> chapterIds = embeddings.stream()
                .map(DocumentEmbedding::getChapterId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        // Load all documents and chapters in batch
        Map<Integer, Documento> documentoMap = documentoRepository.findAllById(documentoIds).stream()
                .collect(Collectors.toMap(Documento::getId, doc -> doc));

        Map<Integer, Chapter> chapterMap = new HashMap<>();
        if (!chapterIds.isEmpty()) {
            chapterMap = chapterRepository.findAllById(chapterIds).stream()
                    .collect(Collectors.toMap(Chapter::getId, cap -> cap));
        }

        // Build enriched results
        final Map<Integer, Chapter> finalChapterMap = chapterMap;
        return embeddings.stream()
                .map(emb -> {
                    SearchResultDTO result = SearchResultDTO.from(emb);

                    // Add document information
                    Documento doc = documentoMap.get(emb.getDocumentoId());
                    String docTitulo = doc != null ? doc.getTitulo() : null;

                    // Add chapter information
                    String capTitulo = null;
                    if (emb.getChapterId() != null) {
                        Chapter chapter = finalChapterMap.get(emb.getChapterId());
                        capTitulo = chapter != null ? chapter.getTitulo() : null;
                    }

                    // Enrich and extract scores
                    return result.enrich(docTitulo, capTitulo).extractScores();
                })
                .collect(Collectors.toList());
    }
}
