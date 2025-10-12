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
import bor.tools.splitter.EmbeddingProcessorInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for RAG search operations.
 * Primary endpoint of the application - executes hybrid search (semantic + textual).
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RAG Search", description = "Hybrid search endpoints (semantic + textual)")
public class SearchController {

    private final DocEmbeddingJdbcRepository embeddingRepository;
    private final EmbeddingProcessorInterface embeddingProcessor;
    private final DocumentoRepository documentoRepository;
    private final ChapterRepository chapterRepository;
    private final LibraryService libraryService;

    /**
     * Hybrid search combining semantic (embedding-based) and textual (full-text) search
     */
    @PostMapping("/hybrid")
    @Operation(summary = "Hybrid search (semantic + textual)",
               description = "Executes combined semantic and textual search across specified libraries")
    public ResponseEntity<SearchResponse> hybridSearch(@Valid @RequestBody SearchRequest request) {
        log.info("Hybrid search: query='{}', libraries={}, limit={}",
                request.getQuery(), request.getLibraryIds(), request.getLimit());

        long startTime = System.currentTimeMillis();

        try {
            // Validate weights
            if (!request.isWeightValid()) {
                throw new IllegalArgumentException(
                        String.format("Pesos inválidos: semântico=%.2f + textual=%.2f != 1.0",
                                request.getPesoSemantico(), request.getPesoTextual())
                );
            }

            LibraryDTO library = loadLibrary(request.getLibraryIds());
	    // Generate query embedding
            float[] queryEmbedding = embeddingProcessor.createSearchEmbeddings(request.getQuery(), library );

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
     * Semantic search (embedding-based only)
     */
    @PostMapping("/semantic")
    @Operation(summary = "Semantic search only",
               description = "Executes pure semantic search using embeddings")
    public ResponseEntity<SearchResponse> semanticSearch(@Valid @RequestBody SemanticSearchRequest request) {
        log.info("Semantic search: query='{}', libraries={}, limit={}",
                request.getQuery(), request.getLibraryIds(), request.getLimit());

        long startTime = System.currentTimeMillis();

        try {
            // Generate query embedding
            LibraryDTO library = loadLibrary(request.getLibraryIds());
	    // Generate query embedding
            float[] queryEmbedding = embeddingProcessor.createSearchEmbeddings(request.getQuery(), library );

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
    @Operation(summary = "Textual search only",
               description = "Executes pure textual search using PostgreSQL full-text search")
    public ResponseEntity<SearchResponse> textualSearch(@Valid @RequestBody TextualSearchRequest request) {
        log.info("Textual search: query='{}', libraries={}, limit={}",
                request.getQuery(), request.getLibraryIds(), request.getLimit());

        long startTime = System.currentTimeMillis();

        try {
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
