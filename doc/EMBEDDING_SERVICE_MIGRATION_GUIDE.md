# Embedding Service Migration Guide

## Overview

This guide helps you migrate from the old embedding processing architecture (`EmbeddingProcessorInterface`, `AsyncSplitterService`) to the new `EmbeddingService` architecture.

The new architecture provides:
- ✅ **Better separation of concerns** via Strategy pattern
- ✅ **Retry logic** for LLM failures (2 min delay, 2 retries)
- ✅ **LLMServiceManager integration** for multi-provider support
- ✅ **Hierarchical model resolution** (request → library → global)
- ✅ **Async processing** with `EmbeddingOrchestrator`
- ✅ **Type-safe configuration** with `ProcessingOptions`

---

## Quick Migration Reference

| Old Code | New Code |
|----------|----------|
| `EmbeddingProcessorInterface` | `EmbeddingService` |
| `EmbeddingProcessorImpl` | `EmbeddingServiceImpl` |
| `AsyncSplitterService` | `EmbeddingOrchestrator` |
| `LibraryDTO` parameter | `EmbeddingContext` |
| `boolean includeQA, includeSummary` | `ProcessingOptions` |

---

## Migration Scenarios

### Scenario 1: Generate Query Embeddings (SearchController)

**OLD CODE:**
```java
@Autowired
private EmbeddingProcessorInterface embeddingProcessor;

public float[] generateQueryEmbedding(String query, LibraryDTO library) {
    return embeddingProcessor.createSearchEmbeddings(query, library);
}
```

**NEW CODE:**
```java
@Autowired
private EmbeddingService embeddingService;

public float[] generateQueryEmbedding(String query, LibraryDTO library) {
    // Create embedding context
    EmbeddingContext context = EmbeddingContext.builder()
            .library(library)
            .build();

    // Generate query embedding
    return embeddingService.generateQueryEmbedding(query, context);
}
```

**What changed:**
1. Inject `EmbeddingService` instead of `EmbeddingProcessorInterface`
2. Wrap `LibraryDTO` in `EmbeddingContext`
3. Call `generateQueryEmbedding()` with context

---

### Scenario 2: Process Document Asynchronously (DocumentoService)

**OLD CODE:**
```java
@Autowired
private AsyncSplitterService asyncSplitterService;

public CompletableFuture<ProcessingStatus> processDocument(
        Integer documentId,
        boolean includeQA,
        boolean includeSummary) {

    // Load document and library...

    AsyncSplitterService.ProcessingResult result = asyncSplitterService
            .fullProcessingAsync(documento, biblioteca, tipoConteudo, includeQA, includeSummary)
            .get();

    // Persist results...
}
```

**NEW CODE:**
```java
@Autowired
private EmbeddingOrchestrator embeddingOrchestrator;

public CompletableFuture<ProcessingStatus> processDocument(
        Integer documentId,
        boolean includeQA,
        boolean includeSummary) {

    // Load document and library...

    // Create embedding context
    EmbeddingContext context = EmbeddingContext.builder()
            .library(biblioteca)
            .build();

    // Create processing options
    ProcessingOptions options = ProcessingOptions.builder()
            .includeQA(includeQA)
            .includeSummary(includeSummary)
            .build();

    // Process with retry logic
    EmbeddingOrchestrator.ProcessingResult result = embeddingOrchestrator
            .processDocumentFull(documento, context, options)
            .get();

    // Persist results...
}
```

**What changed:**
1. Inject `EmbeddingOrchestrator` instead of `AsyncSplitterService`
2. Create `EmbeddingContext` with library
3. Use `ProcessingOptions` builder instead of boolean parameters
4. Call `processDocumentFull()` - includes automatic retry logic

---

### Scenario 3: Generate Chapter Embeddings

**OLD CODE:**
```java
@Autowired
private EmbeddingProcessorInterface embeddingProcessor;

public List<DocumentEmbeddingDTO> generateChapterEmbeddings(
        ChapterDTO chapter,
        LibraryDTO library) {

    return embeddingProcessor.createChapterEmbeddings(
            chapter,
            library,
            EmbeddingProcessorInterface.FLAG_AUTO
    );
}
```

**NEW CODE:**
```java
@Autowired
private EmbeddingService embeddingService;

public List<DocumentEmbeddingDTO> generateChapterEmbeddings(
        ChapterDTO chapter,
        LibraryDTO library) {

    // Create embedding context
    EmbeddingContext context = EmbeddingContext.builder()
            .library(library)
            .build();

    // Generate with automatic strategy selection
    return embeddingService.generateChapterEmbeddings(
            chapter,
            context,
            ChapterEmbeddingStrategy.FLAG_AUTO  // Optional, defaults to AUTO
    );
}
```

**What changed:**
1. Inject `EmbeddingService` instead of `EmbeddingProcessorInterface`
2. Wrap library in `EmbeddingContext`
3. Flags moved to `ChapterEmbeddingStrategy` constants

---

### Scenario 4: Generate Q&A Embeddings

**OLD CODE:**
```java
@Autowired
private EmbeddingProcessorInterface embeddingProcessor;

public List<DocumentEmbeddingDTO> generateQA(ChapterDTO chapter, LibraryDTO library) {
    return embeddingProcessor.createQAEmbeddings(chapter, library, 3);
}
```

**NEW CODE:**
```java
@Autowired
private EmbeddingService embeddingService;

public List<DocumentEmbeddingDTO> generateQA(ChapterDTO chapter, LibraryDTO library) {
    EmbeddingContext context = EmbeddingContext.builder()
            .library(library)
            .build();

    return embeddingService.generateQAEmbeddings(chapter, context, 3);
}
```

---

### Scenario 5: Generate Summary Embeddings

**OLD CODE:**
```java
@Autowired
private EmbeddingProcessorInterface embeddingProcessor;

public List<DocumentEmbeddingDTO> generateSummary(ChapterDTO chapter, LibraryDTO library) {
    return embeddingProcessor.createSummaryEmbeddings(chapter, library, 500, null);
}
```

**NEW CODE:**
```java
@Autowired
private EmbeddingService embeddingService;

public List<DocumentEmbeddingDTO> generateSummary(ChapterDTO chapter, LibraryDTO library) {
    EmbeddingContext context = EmbeddingContext.builder()
            .library(library)
            .build();

    return embeddingService.generateSummaryEmbeddings(chapter, context, 500, null);
}
```

---

## Advanced Features

### 1. Hierarchical Model Resolution

The new architecture supports overriding embedding/completion models at three levels:

```java
// Global default (from application.properties)
// rag.embedding.default-model=nomic-embed-text
// rag.completion.default-model=llama3.2

// Library level (in LibraryDTO metadata)
LibraryDTO library = ...;
library.getMetadados().setDefaultEmbeddingModel("custom-embed-model");

// Request level (explicit override)
EmbeddingContext context = EmbeddingContext.builder()
        .library(library)
        .embeddingModelName("specific-model-for-this-request")  // Highest priority
        .build();
```

**Resolution hierarchy:** Request → Library → Global

---

### 2. Processing Options Configuration

Use `ProcessingOptions` for fine-grained control:

```java
// Full processing (all features)
ProcessingOptions options = ProcessingOptions.fullProcessing();

// Basic only (no Q&A or summaries)
ProcessingOptions options = ProcessingOptions.basicOnly();

// Q&A only
ProcessingOptions options = ProcessingOptions.withQA(5);  // 5 Q&A pairs

// Summary only
ProcessingOptions options = ProcessingOptions.withSummary(300);  // 300 chars

// Custom configuration
ProcessingOptions options = ProcessingOptions.builder()
        .includeQA(true)
        .qaCount(3)
        .includeSummary(true)
        .maxSummaryLength(500)
        .summaryInstructions("Focus on technical details")
        .build();
```

---

### 3. Retry Logic

The orchestrator automatically retries on failure:

```java
// Async with retry (default behavior)
CompletableFuture<ProcessingResult> future =
    embeddingOrchestrator.processDocumentFull(document, context, options);

// Sync with retry
ProcessingResult result =
    embeddingOrchestrator.processDocumentSync(document, context, options);

// Without retry (testing/debugging)
ProcessingResult result =
    embeddingOrchestrator.processDocumentWithoutRetry(document, context, options);
```

**Retry configuration:**
- **Delay:** 2 minutes between attempts
- **Retries:** 2 (3 total attempts)
- **Behavior:** Fails after all retries exhausted

---

### 4. Low-Level Embedding Generation

For custom scenarios:

```java
@Autowired
private EmbeddingService embeddingService;

public float[] customEmbedding(String text, LibraryDTO library) {
    EmbeddingContext context = EmbeddingContext.builder()
            .library(library)
            .build();

    // Direct embedding generation with custom operation
    return embeddingService.generateEmbedding(
            Embeddings_Op.CLUSTERING,  // Custom operation type
            text,
            context,
            "specific-model-name"      // Optional model override
    );
}
```

---

## Testing Considerations

### Unit Tests

**OLD:**
```java
@Mock
private EmbeddingProcessorInterface embeddingProcessor;

@Test
void testSearch() {
    when(embeddingProcessor.createSearchEmbeddings(anyString(), any()))
        .thenReturn(mockEmbedding);
    // ...
}
```

**NEW:**
```java
@Mock
private EmbeddingService embeddingService;

@Test
void testSearch() {
    when(embeddingService.generateQueryEmbedding(anyString(), any(EmbeddingContext.class)))
        .thenReturn(mockEmbedding);
    // ...
}
```

### Integration Tests

See `EmbeddingOrchestratorIntegrationTest` for examples:

```java
@SpringBootTest
@ActiveProfiles("test")
class MyIntegrationTest {

    @Autowired
    private EmbeddingOrchestrator embeddingOrchestrator;

    @Test
    void testDocumentProcessing() {
        // Create test data
        EmbeddingContext context = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        ProcessingOptions options = ProcessingOptions.basicOnly();

        // Process
        ProcessingResult result = embeddingOrchestrator
                .processDocumentWithoutRetry(testDocument, context, options);

        // Assert
        assertTrue(result.isSuccessful());
        assertTrue(result.getAllEmbeddings().size() > 0);
    }
}
```

---

## Deprecated Code Removal Timeline

| Version | Status | Action |
|---------|--------|--------|
| 0.0.2 | **Current** | Deprecated classes marked with `@Deprecated` |
| 0.0.3 | Transition | Both old and new APIs available |
| 0.0.4 | Migration | Old APIs emit warnings |
| 0.1.0 | **Removal** | Old APIs removed entirely |

**Recommendation:** Migrate all code to new APIs before version 0.1.0

---

## Migration Checklist

- [ ] Replace `EmbeddingProcessorInterface` with `EmbeddingService`
- [ ] Replace `AsyncSplitterService` with `EmbeddingOrchestrator`
- [ ] Update all `LibraryDTO` parameters to `EmbeddingContext`
- [ ] Replace boolean flags with `ProcessingOptions`
- [ ] Update unit tests to mock new services
- [ ] Run integration tests to verify behavior
- [ ] Review logs for deprecation warnings
- [ ] Update documentation and comments

---

## Troubleshooting

### Issue: "No LLM service found for model"

**Cause:** Model not registered in LLMServiceManager pool

**Solution:**
1. Check model name in `application.properties`:
   ```properties
   rag.embedding.default-model=nomic-embed-text
   ```
2. Verify model is registered in provider configuration
3. Check `LLMServiceManager.getRegisteredModelNames()` for available models

---

### Issue: Processing fails without retry

**Cause:** Using `processDocumentWithoutRetry()` or old API

**Solution:** Use `processDocumentFull()` or `processDocumentSync()` for automatic retry

---

### Issue: Different embedding results

**Cause:** Model resolution hierarchy changed

**Solution:**
1. Check which model is being used via logs
2. Explicitly set model in `EmbeddingContext` if needed:
   ```java
   EmbeddingContext context = EmbeddingContext.builder()
           .library(library)
           .embeddingModelName("nomic-embed-text")  // Explicit
           .build();
   ```

---

## Support

For questions or issues:
1. Check this migration guide
2. Review `splitting_and_embedding_generation_FINAL.md`
3. Examine unit tests in `service/embedding/strategy/`
4. Create issue on GitHub

---

## Summary

The new embedding service architecture provides significant improvements:

| Feature | Old | New |
|---------|-----|-----|
| Retry Logic | ❌ | ✅ 2 min, 2 retries |
| Model Pool | ❌ | ✅ LLMServiceManager |
| Strategy Pattern | ❌ | ✅ 4 specialized strategies |
| Type Safety | ⚠️ Partial | ✅ Full with builders |
| Async Support | ✅ | ✅ Enhanced |
| Hierarchical Config | ❌ | ✅ 3-level resolution |

**Migration effort:** Low to Medium (mostly mechanical substitutions)

**Breaking changes:** None (old APIs remain functional with deprecation warnings)

**Recommended timeline:** Migrate within 2-3 development cycles
