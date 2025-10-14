# Plan Correction: Use Existing JDBC Repository

**Date**: 2025-10-13
**Status**: CRITICAL CORRECTION

---

## ⚠️ Important Correction to Implementation Plan

### Issue Identified

The DOCUMENT_LOADING_IMPLEMENTATION_PLAN.md **incorrectly proposes** creating a new JPA repository `DocumentEmbeddingRepository` when a **sophisticated JDBC repository already exists**.

###  Existing Repository

**File**: `src/main/java/bor/tools/simplerag/repository/DocEmbeddingJdbcRepository.java`

**Key Features**:
- ✅ Custom SQL control for PGVector operations
- ✅ Full-text search integration with `websearch_to_tsquery`
- ✅ Hybrid search (semantic + textual)
- ✅ Semantic-only search with cosine similarity
- ✅ Textual-only search with ts_rank_cd
- ✅ CRUD operations with PGVector support
- ✅ Batch save capabilities
- ✅ Library configuration caching
- ✅ Query processing and normalization

**Critical Methods**:

```java
// CRUD
Integer save(DocumentEmbedding doc)
int update(DocumentEmbedding doc)
Optional<DocumentEmbedding> findById(Integer id)
List<DocumentEmbedding> findByDocumentoId(Integer documentoId)
List<DocumentEmbedding> findByBibliotecaId(Integer bibliotecaId)
List<DocumentEmbedding> findByCapituloId(Integer capituloId)

// Advanced Search
List<DocumentEmbedding> pesquisaHibrida(float[] embedding, String query, Integer[] bibliotecaIds, Integer k, Float pesoSemantico, Float pesoTextual)
List<DocumentEmbedding> pesquisaSemantica(float[] vec, Integer[] bibliotecaIds, Integer k)
List<DocumentEmbedding> pesquisaTextual(String queryString, Integer[] bibliotecaIds, Integer k)

// Query Processing
String query_phraseto_websearch(String frase, boolean pesquisaAmpla)
```

---

## Corrected Implementation

### Task 1.2: DocumentoService.java

**WRONG** (from original plan):

```java
@Service
public class DocumentoService {
    // ❌ DO NOT DO THIS
    private final DocumentEmbeddingRepository embeddingRepository;  // JPA repository

    // Wrong: Using saveAll
    List<DocumentEmbedding> savedEmbeddings = embeddingRepository.saveAll(embeddings);
}
```

**CORRECT** (use JDBC repository):

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentoService {

    // Dependencies
    private final DocumentoRepository documentoRepository;
    private final ChapterRepository chapterRepository;

    // ✅ USE THE EXISTING JDBC REPOSITORY
    private final DocEmbeddingJdbcRepository embeddingRepository;

    private final LibraryService libraryService;
    private final TikaDocumentConverter documentConverter;
    private final DocumentRouter documentRouter;
    private final AsyncSplitterService asyncSplitterService;
    private final EmbeddingProcessorImpl embeddingProcessor;

    /**
     * Persist processing results (chapters + embeddings)
     * Implements Fluxo_carga_documents.md steps (e) and (g)
     */
    @Transactional
    protected void persistProcessingResult(ProcessingResult result, Documento documento) {
        log.debug("Persisting processing result for document: {}", documento.getId());

        // 1. Save chapters (using JPA repository - this is fine)
        List<Chapter> chapters = result.getCapitulos().stream()
            .map(dto -> toEntity(dto, documento))
            .collect(Collectors.toList());

        List<Chapter> savedChapters = chapterRepository.saveAll(chapters);
        log.debug("Saved {} chapters", savedChapters.size());

        // 2. Map chapter DTOs to saved entities for embedding association
        Map<String, Integer> chapterIdMap = new HashMap<>();
        for (int i = 0; i < result.getCapitulos().size(); i++) {
            ChapterDTO dto = result.getCapitulos().get(i);
            Chapter saved = savedChapters.get(i);
            chapterIdMap.put(dto.getTitulo(), saved.getId());
        }

        // 3. Save embeddings - ✅ USE JDBC REPOSITORY ONE BY ONE
        List<DocumentEmbedding> embeddings = result.getAllEmbeddings().stream()
            .map(dto -> toEntity(dto, documento, chapterIdMap))
            .collect(Collectors.toList());

        // ✅ CORRECT WAY: Save using JDBC repository
        List<Integer> savedIds = new ArrayList<>();
        for (DocumentEmbedding emb : embeddings) {
            try {
                Integer id = embeddingRepository.save(emb);  // Returns generated ID
                savedIds.add(id);
            } catch (Exception e) {
                log.error("Failed to save embedding: {}", e.getMessage(), e);
                throw new RuntimeException("Embedding save failed", e);
            }
        }

        log.debug("Saved {} embeddings with IDs: {}", savedIds.size(), savedIds);

        // 4. Update document status
        documento.setProcessado(true);
        documento.setDataProcessamento(LocalDateTime.now());
        documentoRepository.save(documento);

        log.debug("Document {} fully processed and persisted", documento.getId());
    }

    /**
     * Convert DTO to Entity for embedding
     */
    private DocumentEmbedding toEntity(DocumentEmbeddingDTO dto, Documento documento,
                                       Map<String, Integer> chapterIdMap) {
        DocumentEmbedding emb = new DocumentEmbedding();

        // Set library and document IDs
        emb.setLibraryId(documento.getBibliotecaId());
        emb.setDocumentoId(documento.getId());

        // Try to find chapter ID from metadata
        String chapterTitle = (String) dto.getMetadados().get("capitulo_titulo");
        if (chapterTitle != null && chapterIdMap.containsKey(chapterTitle)) {
            emb.setChapterId(chapterIdMap.get(chapterTitle));
        }

        // Set text content (campo 'texto' no banco, não 'trecho_texto')
        emb.setTexto(dto.getTrechoTexto());

        // Set embedding vector
        emb.setEmbeddingVector(dto.getEmbeddingVector());

        // Set embedding type
        emb.setTipoEmbedding(dto.getTipoEmbedding());

        // Set metadata
        emb.setMetadados(dto.getMetadados());

        // Set order if present
        if (dto.getMetadados().containsKey("ordem_cap")) {
            emb.setOrderChapter((Integer) dto.getMetadados().get("ordem_cap"));
        }

        return emb;
    }
}
```

---

### Task 2.2: AsyncSplitterService.java Correction

**WRONG** (from original plan):

```java
@Service
public class AsyncSplitterService {
    // ❌ DO NOT DO THIS
    private final DocumentEmbeddingRepository embeddingRepository;  // JPA

    List<DocumentEmbedding> saved = embeddingRepository.saveAll(embeddings);
}
```

**CORRECT** (use JDBC repository):

```java
@Service
public class AsyncSplitterService {

    // Existing dependencies
    private final SplitterFactory splitterFactory;
    private final EmbeddingProcessorImpl embeddingProcessor;
    private final DocumentSummarizerImpl documentSummarizer;
    private final Executor taskExecutor;

    // NEW: Repository dependencies
    private final ChapterRepository chapterRepository;

    // ✅ USE THE EXISTING JDBC REPOSITORY
    private final DocEmbeddingJdbcRepository embeddingRepository;

    private final DocumentoRepository documentoRepository;

    /**
     * Persist embeddings using JDBC repository
     */
    @Transactional
    protected List<Integer> persistEmbeddings(List<DocumentEmbeddingDTO> embeddingDTOs,
                                              DocumentoDTO documento,
                                              List<Chapter> chapters) throws SQLException {
        logger.debug("Persisting {} embeddings for document {}", embeddingDTOs.size(), documento.getId());

        // Create chapter title -> ID mapping
        Map<String, Integer> chapterIdMap = chapters.stream()
            .collect(Collectors.toMap(Chapter::getTitulo, Chapter::getId));

        List<Integer> savedIds = new ArrayList<>();

        // ✅ CORRECT: Save one by one using JDBC repository
        for (DocumentEmbeddingDTO dto : embeddingDTOs) {
            DocumentEmbedding emb = new DocumentEmbedding();
            emb.setLibraryId(documento.getLibraryId());
            emb.setDocumentoId(documento.getId());

            // Try to find chapter ID
            String chapterTitle = (String) dto.getMetadados().get("capitulo_titulo");
            if (chapterTitle != null && chapterIdMap.containsKey(chapterTitle)) {
                emb.setChapterId(chapterIdMap.get(chapterTitle));
            }

            emb.setTexto(dto.getTrechoTexto());
            emb.setEmbeddingVector(dto.getEmbeddingVector());
            emb.setTipoEmbedding(dto.getTipoEmbedding());
            emb.setMetadados(dto.getMetadados());

            // Save and collect ID
            Integer id = embeddingRepository.save(emb);
            savedIds.add(id);
        }

        logger.debug("Successfully persisted {} embeddings with IDs", savedIds.size());
        return savedIds;
    }
}
```

---

## Why JDBC Repository is Critical

### 1. **PGVector Integration**

The JDBC repository properly handles PGVector types:

```java
// Correctly wraps float[] as PGvector for PostgreSQL
ps.setObject(7, new PGvector(doc.getEmbeddingVector()));
```

### 2. **Full-Text Search Integration**
The repository generates and maintains the `text_search_tsv` column:

```sql
-- Trigger automatically populates this from 'texto' column
text_search_tsv TSVECTOR GENERATED ALWAYS AS (
    to_tsvector('portuguese'::regconfig, texto)
) STORED
```

### 3. **Custom Search Queries**
The repository provides optimized hybrid search:

```sql
-- Combines semantic similarity and text ranking
WITH semantic_search AS (...),
     text_search AS (...)
SELECT d.*,
       (semantic_score * peso_semantico + text_score * peso_textual) AS score
FROM doc_embedding d
...
```

### 4. **Library Configuration Caching**
The repository caches embedding dimensions per library:

```java
private Map<Integer, Integer> mapBibliotecaId2VecLen = new HashMap<>();
private Map<Integer, MetaBiblioteca> mapBibliotecaConfig = new HashMap<>();
```

---

## Updated Repository Requirements

### ✅ Keep These (from original plan)

**ChapterRepository.java** (JPA is fine for chapters):

```java
public interface ChapterRepository extends JpaRepository<Chapter, Integer> {
    List<Chapter> findByDocumentoIdOrderByOrdemDoc(Integer documentoId);
    long countByDocumentoId(Integer documentoId);
}
```

### ❌ REMOVE This (from original plan)

**~~DocumentEmbeddingRepository.java~~** ← DELETE THIS FROM PLAN

**DO NOT CREATE** a JPA repository for DocumentEmbedding!

### ✅ USE This (already exists)

**DocEmbeddingJdbcRepository.java** ← ALREADY EXISTS

Use it directly in all services!

---

## Action Items

### For DocumentoService.java:
1. ✅ Inject `DocEmbeddingJdbcRepository` (not DocumentEmbeddingRepository)
2. ✅ Save embeddings one by one using `embeddingRepository.save(emb)`
3. ✅ Handle SQLException (JDBC throws checked exceptions)
4. ✅ Map DTO fields correctly (`trechoTexto` → `texto`)

### For AsyncSplitterService.java:
1. ✅ Inject `DocEmbeddingJdbcRepository` (not DocumentEmbeddingRepository)
2. ✅ Return List<Integer> (saved IDs) instead of List<DocumentEmbedding>
3. ✅ Handle SQLException properly
4. ✅ Log saved IDs for tracking

### For Testing:
1. ✅ Mock `DocEmbeddingJdbcRepository` in unit tests
2. ✅ Test with actual PostgreSQL + PGVector in integration tests
3. ✅ Verify `text_search_tsv` is populated via trigger
4. ✅ Test hybrid search after document processing

---

## Example Usage Pattern

```java
// CORRECT PATTERN for saving embeddings
@Transactional
public void saveEmbeddings(List<DocumentEmbedding> embeddings) {
    List<Integer> savedIds = new ArrayList<>();

    for (DocumentEmbedding emb : embeddings) {
        try {
            // JDBC repository returns generated ID
            Integer id = embeddingRepository.save(emb);
            savedIds.add(id);

            log.debug("Saved embedding with ID: {}", id);
        } catch (SQLException e) {
            log.error("Failed to save embedding: {}", e.getMessage());
            throw new RuntimeException("Database error", e);
        }
    }

    log.info("Successfully saved {} embeddings", savedIds.size());
}
```

---

## Summary

| Component | Original Plan (WRONG) | Corrected Plan (RIGHT) |
|-----------|----------------------|------------------------|
| Embedding Repository | Create new JPA `DocumentEmbeddingRepository` | ✅ Use existing `DocEmbeddingJdbcRepository` |
| Save Method | `saveAll(List<DocumentEmbedding>)` | ✅ Loop with `save(DocumentEmbedding)` |
| Return Type | `List<DocumentEmbedding>` | ✅ `List<Integer>` (IDs) or void |
| Exception Handling | None | ✅ Handle `SQLException` |
| Vector Handling | Simple JPA | ✅ PGvector wrapper |
| Text Search | Not integrated | ✅ Automatic via trigger |

---

**Status**: ✅ CORRECTION COMPLETE
**Impact**: Critical - affects persistence layer implementation
**Action Required**: Update DocumentoService and AsyncSplitterService to use JDBC repository

---

**Prepared by**: Claude Code
**Date**: 2025-10-13
