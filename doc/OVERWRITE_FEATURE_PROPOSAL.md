# Feature `overwrite` - Proposta de Desenvolvimento

**Data:** 2025-11-03
**Versão:** 1.0
**Status:** Proposta para aprovação

---

## 📋 Resumo Executivo

Adicionar nova feature `overwrite` ao endpoint de processamento de documentos, permitindo controle sobre como lidar com Chapters e DocEmbeddings existentes durante reprocessamento.

### Endpoint Afetado

```
POST /api/v1/documents/{documentId}/process
```

### Novos Parâmetros

| Parâmetro | Tipo | Default | Descrição |
|-----------|------|---------|-----------|
| `includeQA` | boolean | false | ✅ **Existente** - Gera embeddings de Q&A |
| `includeSummary` | boolean | false | ✅ **Existente** - Gera embeddings de resumo |
| **`overwrite`** | **boolean** | **false** | ⭐ **NOVO** - Controla reprocessamento |

---

## 🎯 Objetivo da Feature

Permitir que o sistema:

1. **Preservar** processamento existente quando `overwrite=false` (comportamento padrão)
2. **Reprocessar completamente** quando `overwrite=true`:
   - Excluir Chapters e DocEmbeddings existentes (via CASCADE)
   - Em seguida, criar novos Chapters e DocEmbeddings a partir do Documento existente
   - Processamento normal (ETAPA 2.1, 2.2, 2.3)

---

## 🔄 Fluxo Visual do `overwrite=true`

```
┌─────────────────────────────────────────────────────────────────┐
│  POST /api/v1/documents/123/process?overwrite=true             │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
        ┌────────────────────────────────────┐
        │ Verifica Chapters/Embeddings       │
        │ existentes para documento 123      │
        └────────────┬───────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────────┐
        │ DELETE FROM chapter                │
        │ WHERE documento_id = 123           │
        │                                    │
        │ ↓ (CASCADE automático)             │
        │                                    │
        │ DELETE FROM doc_embedding          │
        │ WHERE documento_id = 123           │
        └────────────┬───────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────────┐
        │ 🔄 REPROCESSAMENTO                 │
        │                                    │
        │ 1. Lê Documento.conteudoMarkdown   │
        │ 2. Cria NOVOS Chapters             │
        │ 3. Cria NOVOS DocEmbeddings        │
        │ 4. Gera embeddings vectors         │
        └────────────┬───────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────────┐
        │ HTTP 202 Accepted                  │
        │ {status: "PROCESSING"}             │
        └────────────────────────────────────┘
```

---

## 📐 Comportamento Detalhado

### Cenário 1: `overwrite=false` (Default)

#### 1.1. Documento **COM** Chapters e DocEmbeddings
```
✅ Chapters existentes → Preservados
✅ DocEmbeddings existentes → Preservados
📝 Log: INFO "Document {id} already processed. Skipping. Use overwrite=true to reprocess."
❌ Não cria novos Chapters
❌ Não cria novos DocEmbeddings
✅ Retorna HTTP 200 com status "ALREADY_PROCESSED"
```

#### 1.2. Documento **COM** Chapters mas **SEM** DocEmbeddings
```
✅ Chapters existentes → Preservados
❌ DocEmbeddings não existem
📝 Log: INFO "Found {n} chapters without embeddings. Generating embeddings..."
✅ Cria DocEmbeddings a partir dos Chapters existentes
✅ Retorna HTTP 202 com status "PROCESSING"
```

#### 1.3. Documento **SEM** Chapters
```
❌ Nada para preservar
✅ Processa normalmente (ETAPA 2.1, 2.2, 2.3)
✅ Cria Chapters e DocEmbeddings
✅ Retorna HTTP 202 com status "PROCESSING"
```

---

### Cenário 2: `overwrite=true`

```
⚠️ Verifica se existem Chapters ou DocEmbeddings associados ao Documento
   ↓
📝 Log: WARN "Overwrite enabled. Deleting {n} chapters and {m} embeddings for document {id}"
   ↓
🗑️ DELETE FROM chapter WHERE documento_id = {id}
   ↓ (CASCADE automático)
🗑️ DELETE FROM doc_embedding WHERE documento_id = {id}
   ↓
✅ EM SEGUIDA: Processa normalmente (ETAPA 2.1, 2.2, 2.3)
   ↓
✅ Cria NOVOS Chapters a partir do Documento existente (conteudoMarkdown)
   ↓
✅ Cria NOVOS DocEmbeddings a partir dos Chapters recém-criados
   ↓
✅ Retorna HTTP 202 com status "PROCESSING"
```

**Importante:**
- O `ON DELETE CASCADE` no banco de dados garante que ao excluir Chapters → DocEmbeddings são excluídos automaticamente
- Apenas 1 DELETE é necessário: `DELETE FROM chapter WHERE documento_id = ?`
- **Após a exclusão, o processamento continua normalmente** criando novos Chapters e DocEmbeddings a partir do Documento existente
- O Documento (entidade) **NÃO é excluído**, apenas seus Chapters e DocEmbeddings associados

---

## 🏗️ Arquitetura da Implementação

### 1. Modificações no `DocumentController.java`

**Linha:** 346-419 (método `processDocument`)

#### Alterações:

```java
// ANTES
public ResponseEntity<Map<String, Object>> processDocument(
        @PathVariable Integer documentId,
        @RequestParam(defaultValue = "false") boolean includeQA,
        @RequestParam(defaultValue = "false") boolean includeSummary) {

// DEPOIS
public ResponseEntity<Map<String, Object>> processDocument(
        @PathVariable Integer documentId,
        @RequestParam(defaultValue = "false") boolean includeQA,
        @RequestParam(defaultValue = "false") boolean includeSummary,
        @RequestParam(defaultValue = "false") boolean overwrite) {  // ⭐ NOVO
```

#### Lógica Adicional:

```java
try {
    // ... código existente de verificação de documento ...

    // ⭐ NOVA VERIFICAÇÃO: Check existing processing
    ProcessingCheckResult checkResult = documentoService.checkExistingProcessing(documentId);

    if (checkResult.hasChapters() && !overwrite) {
        // Documento já processado e overwrite=false
        log.info("Document {} already processed. Skipping. Use overwrite=true to reprocess.", documentId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Document already processed");
        response.put("documentId", documentId);
        response.put("status", "ALREADY_PROCESSED");
        response.put("chaptersCount", checkResult.getChaptersCount());
        response.put("embeddingsCount", checkResult.getEmbeddingsCount());
        response.put("hint", "Use overwrite=true to reprocess");

        return ResponseEntity.ok(response);  // HTTP 200
    }

    if (overwrite && checkResult.hasChapters()) {
        // ⭐ OVERWRITE HABILITADO: Delete existing THEN reprocess
        log.warn("Overwrite enabled. Deleting {} chapters and {} embeddings for document {}",
                checkResult.getChaptersCount(), checkResult.getEmbeddingsCount(), documentId);

        documentoService.deleteExistingProcessing(documentId);

        log.info("Existing processing deleted. Will now reprocess document {} from scratch", documentId);
    }

    // ⭐ Continua processamento normalmente (criar novos Chapters/DocEmbeddings)
    // ... código existente de processamento ...

} catch (Exception e) {
    // ... tratamento de erros ...
}
```

---

### 2. Modificações no `DocumentoService.java`

#### 2.1. Novo Método: `checkExistingProcessing`

**Linha:** ~872 (após método `ProcessingStatus`)

```java
/**
 * Checks if document has existing Chapters and DocEmbeddings.
 *
 * @param documentId Document ID
 * @return Result with counts
 */
public ProcessingCheckResult checkExistingProcessing(Integer documentId) {
    log.debug("Checking existing processing for document: {}", documentId);

    // Count chapters
    int chaptersCount = chapterRepository.countByDocumentoId(documentId);

    // Count embeddings
    int embeddingsCount = 0;
    try {
        embeddingsCount = embeddingRepository.countByDocumentoId(documentId);
    } catch (Exception e) {
        log.warn("Failed to count embeddings for document {}: {}", documentId, e.getMessage());
    }

    return ProcessingCheckResult.builder()
            .documentId(documentId)
            .chaptersCount(chaptersCount)
            .embeddingsCount(embeddingsCount)
            .hasChapters(chaptersCount > 0)
            .hasEmbeddings(embeddingsCount > 0)
            .build();
}
```

#### 2.2. Novo Método: `deleteExistingProcessing`

**Linha:** ~900

```java
/**
 * Deletes all Chapters and DocEmbeddings for a document.
 *
 * Uses ON DELETE CASCADE to automatically delete related DocEmbeddings.
 *
 * IMPORTANT: This method ONLY deletes existing data. The caller (DocumentController)
 * is responsible for continuing the processing flow to create NEW Chapters and
 * DocEmbeddings from the existing Documento.conteudoMarkdown.
 *
 * @param documentId Document ID
 */
@Transactional
public void deleteExistingProcessing(Integer documentId) {
    log.info("Deleting existing processing data for document: {}", documentId);

    try {
        // Count before deletion (for logging)
        int chaptersCount = chapterRepository.countByDocumentoId(documentId);
        int embeddingsCount = embeddingRepository.countByDocumentoId(documentId);

        // Delete chapters (CASCADE will delete embeddings automatically)
        int deletedChapters = chapterRepository.deleteByDocumentoId(documentId);

        log.info("Deleted {} chapters and {} embeddings (via CASCADE) for document {}",
                deletedChapters, embeddingsCount, documentId);

        // Note: Processing will continue in DocumentController to create NEW entities

    } catch (Exception e) {
        log.error("Failed to delete processing data for document {}: {}", documentId, e.getMessage(), e);
        throw new RuntimeException("Failed to delete existing processing data", e);
    }
}
```

#### 2.3. Nova Classe DTO: `ProcessingCheckResult`

**Linha:** ~845 (após `ProcessingStatus`)

```java
/**
 * Result of checking existing processing data.
 */
@Data
@Builder
public static class ProcessingCheckResult {
    private Integer documentId;
    private int chaptersCount;
    private int embeddingsCount;
    private boolean hasChapters;
    private boolean hasEmbeddings;
}
```

---

### 3. Modificações em `ChapterRepository.java`

**Arquivo:** `src/main/java/bor/tools/simplerag/repository/ChapterRepository.java`

#### Novos Métodos:

```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Integer> {

    // ✅ Métodos existentes...

    // ⭐ NOVO: Count chapters by document
    int countByDocumentoId(Integer documentoId);

    // ⭐ NOVO: Delete chapters by document (returns count)
    @Modifying
    @Transactional
    int deleteByDocumentoId(Integer documentoId);
}
```

---

### 4. Modificações em `DocEmbeddingJdbcRepository.java`

**Arquivo:** `src/main/java/bor/tools/simplerag/repository/DocEmbeddingJdbcRepository.java`

#### Novo Método:

```java
/**
 * Counts embeddings for a document.
 *
 * @param documentoId Document ID
 * @return Count of embeddings
 * @throws SQLException if query fails
 */
public int countByDocumentoId(Integer documentoId) throws SQLException {
    String sql = "SELECT COUNT(*) FROM doc_embedding WHERE documento_id = ?";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, documentoId);

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
}
```

---

### 5. Atualização da Documentação OpenAPI

**Arquivo:** `DocumentController.java` linha 317

```java
@Operation(
    summary = "Process document asynchronously (Phase 1 + optional Phase 2)",
    description = """
        Initiates asynchronous document processing:

        **Phase 1 (Required):**
        1. Splits document into chapters (~8k tokens each)
        2. Splits chapters into chunks (~2k tokens each)
        3. Generates embeddings for chunks (tipo=CAPITULO/TRECHO)
        4. Persists embeddings to database for search

        **Phase 2 (Optional - if includeQA or includeSummary are enabled):**
        5. Generates Q&A embeddings (tipo=PERGUNTAS_RESPOSTAS)
        6. Generates summary embeddings (tipo=RESUMO)

        **Overwrite Behavior (NEW):** ⭐
        - overwrite=false (default): Preserves existing Chapters/DocEmbeddings
          - If already processed: Returns 200 with status ALREADY_PROCESSED
          - If Chapters exist but no embeddings: Generates embeddings only
        - overwrite=true: Deletes ALL existing Chapters and DocEmbeddings before reprocessing
          - WARNING: This is destructive and cannot be undone!

        **Processing time:**
        - Phase 1 only: 1-10 minutes
        - Phase 1 + Phase 2: 3-30 minutes (depends on document size and options)

        **Returns immediately** with 202 Accepted status (or 200 if already processed)
        **Monitor progress:** Use GET /api/v1/documents/{id}/status

        **Optional Parameters:**
        - includeQA: Generate Q&A pairs from content (uses completion model, more expensive)
        - includeSummary: Generate chapter summaries (uses completion model, more expensive)
        - overwrite: Delete existing processing and reprocess from scratch (default: false)
        """
)
```

---

## 🗄️ Banco de Dados

### Constraints CASCADE Existentes

✅ **Já implementado** no changelog `003-create-tables.xml`:

```xml
<!-- Linha 110-116: chapter → documento -->
<addForeignKeyConstraint
    baseTableName="chapter"
    baseColumnNames="documento_id"
    constraintName="fk_chapter_documento"
    referencedTableName="documento"
    referencedColumnNames="id"
    onDelete="CASCADE"/>

<!-- Linha 155-157: doc_embedding → documento e chapter -->
CONSTRAINT fk_embedding_documento FOREIGN KEY (documento_id)
    REFERENCES documento(id) ON DELETE CASCADE,
CONSTRAINT fk_embedding_chapter FOREIGN KEY (chapter_id)
    REFERENCES chapter(id) ON DELETE CASCADE
```

**Conclusão:** Nenhuma migration necessária! 🎉

---

## 🧪 Plano de Testes

### 1. Testes Unitários

#### `DocumentoServiceTest.java`

```java
@Nested
@DisplayName("Overwrite Feature Tests")
class OverwriteFeatureTests {

    @Test
    @DisplayName("checkExistingProcessing - Should return correct counts")
    void checkExistingProcessing_WithChaptersAndEmbeddings_ReturnsCorrectCounts() {
        // Given: Document with 4 chapters and 30 embeddings
        when(chapterRepository.countByDocumentoId(testDocumentId)).thenReturn(4);
        when(embeddingRepository.countByDocumentoId(testDocumentId)).thenReturn(30);

        // When
        ProcessingCheckResult result = documentoService.checkExistingProcessing(testDocumentId);

        // Then
        assertThat(result.getDocumentId()).isEqualTo(testDocumentId);
        assertThat(result.getChaptersCount()).isEqualTo(4);
        assertThat(result.getEmbeddingsCount()).isEqualTo(30);
        assertThat(result.isHasChapters()).isTrue();
        assertThat(result.isHasEmbeddings()).isTrue();
    }

    @Test
    @DisplayName("deleteExistingProcessing - Should delete all chapters")
    void deleteExistingProcessing_Success() {
        // Given
        when(chapterRepository.countByDocumentoId(testDocumentId)).thenReturn(4);
        when(embeddingRepository.countByDocumentoId(testDocumentId)).thenReturn(30);
        when(chapterRepository.deleteByDocumentoId(testDocumentId)).thenReturn(4);

        // When
        documentoService.deleteExistingProcessing(testDocumentId);

        // Then
        verify(chapterRepository).deleteByDocumentoId(testDocumentId);
        // CASCADE will handle embeddings automatically
    }
}
```

#### `DocumentControllerTest.java`

```java
@Nested
@DisplayName("POST /process with overwrite parameter")
class ProcessWithOverwriteTests {

    @Test
    @DisplayName("overwrite=false and already processed - Should return 200 ALREADY_PROCESSED")
    void processDocument_AlreadyProcessedNoOverwrite_Returns200() throws Exception {
        // Given: Document exists and is already processed
        DocumentoDTO documento = DocumentoDTO.builder()
                .id(testDocumentId)
                .titulo("Test Doc")
                .build();

        ProcessingCheckResult checkResult = ProcessingCheckResult.builder()
                .documentId(testDocumentId)
                .chaptersCount(4)
                .embeddingsCount(30)
                .hasChapters(true)
                .hasEmbeddings(true)
                .build();

        when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(documento));
        when(documentoService.checkExistingProcessing(testDocumentId)).thenReturn(checkResult);

        // When/Then
        mockMvc.perform(post("/api/v1/documents/{id}/process", testDocumentId)
                .param("overwrite", "false"))
                .andExpect(status().isOk())  // 200!
                .andExpect(jsonPath("$.status").value("ALREADY_PROCESSED"))
                .andExpect(jsonPath("$.chaptersCount").value(4))
                .andExpect(jsonPath("$.embeddingsCount").value(30))
                .andExpect(jsonPath("$.hint").value("Use overwrite=true to reprocess"));

        // Verify processing was NOT started
        verify(documentoService, never()).processDocumentAsyncV2(any());
    }

    @Test
    @DisplayName("overwrite=true - Should delete and reprocess")
    void processDocument_WithOverwriteTrue_DeletesAndReprocesses() throws Exception {
        // Given
        DocumentoDTO documento = DocumentoDTO.builder()
                .id(testDocumentId)
                .titulo("Test Doc")
                .build();

        ProcessingCheckResult checkResult = ProcessingCheckResult.builder()
                .documentId(testDocumentId)
                .chaptersCount(4)
                .embeddingsCount(30)
                .hasChapters(true)
                .build();

        when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(documento));
        when(documentoService.checkExistingProcessing(testDocumentId)).thenReturn(checkResult);
        when(documentoService.processDocumentAsyncV2(testDocumentId))
                .thenReturn(CompletableFuture.completedFuture(new ProcessingStatus()));

        // When/Then
        mockMvc.perform(post("/api/v1/documents/{id}/process", testDocumentId)
                .param("overwrite", "true"))
                .andExpect(status().isAccepted());  // 202

        // Verify deletion happened
        verify(documentoService).deleteExistingProcessing(testDocumentId);

        // Verify processing started
        verify(documentoService).processDocumentAsyncV2(testDocumentId);
    }
}
```

---

### 2. Testes de Integração

#### `DocumentProcessingOverwriteIntegrationTest.java`

```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class DocumentProcessingOverwriteIntegrationTest {

    @Autowired
    private DocumentoService documentoService;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private DocEmbeddingJdbcRepository embeddingRepository;

    @Test
    @DisplayName("Integration: overwrite=true should delete chapters and embeddings via CASCADE")
    void overwrite_DeletesCascade() throws Exception {
        // 1. Setup: Create document with chapters and embeddings
        Integer docId = createTestDocumentWithProcessing();

        // 2. Verify initial state
        int initialChapters = chapterRepository.countByDocumentoId(docId);
        int initialEmbeddings = embeddingRepository.countByDocumentoId(docId);
        assertThat(initialChapters).isGreaterThan(0);
        assertThat(initialEmbeddings).isGreaterThan(0);

        // 3. Delete via service
        documentoService.deleteExistingProcessing(docId);

        // 4. Verify CASCADE deletion
        assertThat(chapterRepository.countByDocumentoId(docId)).isEqualTo(0);
        assertThat(embeddingRepository.countByDocumentoId(docId)).isEqualTo(0);
    }
}
```

---

## 📝 Checklist de Implementação

### Fase 1: Repositórios
- [ ] Adicionar `countByDocumentoId()` em `ChapterRepository`
- [ ] Adicionar `deleteByDocumentoId()` em `ChapterRepository`
- [ ] Adicionar `countByDocumentoId()` em `DocEmbeddingJdbcRepository`
- [ ] Testar métodos de repositório isoladamente

### Fase 2: Service Layer
- [ ] Criar `ProcessingCheckResult` DTO em `DocumentoService`
- [ ] Implementar `checkExistingProcessing()` em `DocumentoService`
- [ ] Implementar `deleteExistingProcessing()` em `DocumentoService`
- [ ] Criar testes unitários para novos métodos

### Fase 3: Controller Layer
- [ ] Adicionar parâmetro `overwrite` em `processDocument()`
- [ ] Implementar lógica de verificação de processamento existente
- [ ] Implementar lógica de deleção quando `overwrite=true`
- [ ] Atualizar anotação `@Operation` com nova documentação
- [ ] Criar testes de controller para novos cenários

### Fase 4: Testes de Integração
- [ ] Criar `DocumentProcessingOverwriteIntegrationTest`
- [ ] Testar CASCADE deletion
- [ ] Testar fluxo completo: overwrite → delete → reprocess

### Fase 5: Documentação
- [ ] Atualizar README.md com exemplos da feature
- [ ] Atualizar documentação OpenAPI/Swagger
- [ ] Criar guia de uso da feature overwrite

---

## 🚀 Exemplos de Uso

### Exemplo 1: Primeiro Processamento

```bash
POST /api/v1/documents/123/process
Content-Type: application/json

# Sem parâmetros - usa defaults
```

**Response:** `202 Accepted`
```json
{
  "message": "Document processing started",
  "documentId": 123,
  "statusUrl": "/api/v1/documents/123/status"
}
```

---

### Exemplo 2: Tentativa de Reprocessar (Sem Overwrite)

```bash
POST /api/v1/documents/123/process?overwrite=false
```

**Response:** `200 OK`
```json
{
  "message": "Document already processed",
  "documentId": 123,
  "status": "ALREADY_PROCESSED",
  "chaptersCount": 4,
  "embeddingsCount": 30,
  "hint": "Use overwrite=true to reprocess"
}
```

---

### Exemplo 3: Reprocessamento Forçado

```bash
POST /api/v1/documents/123/process?overwrite=true&includeQA=true
```

**Response:** `202 Accepted`
```json
{
  "message": "Document processing started",
  "documentId": 123,
  "titulo": "Test Document",
  "phase1": "Splitting and generating embeddings",
  "phase2": "Q&A and/or summary enrichment will start after Phase 1",
  "enrichmentOptions": {
    "includeQA": true,
    "includeSummary": false
  },
  "statusUrl": "/api/v1/documents/123/status",
  "estimatedTime": "3-30 minutes"
}
```

**Logs:**
```
WARN  - Overwrite enabled. Deleting 4 chapters and 30 embeddings for document 123
INFO  - Deleted 4 chapters and 30 embeddings (via CASCADE) for document 123
INFO  - Existing processing deleted. Will now reprocess document 123 from scratch
INFO  - Starting document processing: docId=123, library=Technical
INFO  - Creating LLM and Embedding contexts...
INFO  - Splitting document into chapters and chunks...
INFO  - Document split into 5 chapters (NEW!)
INFO  - Embeddings processed: 35/35 successful (NEW!)
INFO  - Document processing completed successfully
```

---

## ⚠️ Avisos e Precauções

### 1. Operação Destrutiva
- `overwrite=true` **EXCLUI PERMANENTEMENTE** todos os Chapters e DocEmbeddings
- Não há "undo" ou "restore"
- Considerar adicionar confirmação na UI

### 2. Custos de Reprocessamento
- Reprocessar documentos grandes consome:
  - Tokens LLM (custo financeiro)
  - Tempo de CPU/GPU
  - Espaço em banco de dados temporariamente duplicado

### 3. Impacto em Buscas
- Durante o reprocessamento, o documento fica temporariamente sem embeddings
- Buscas podem retornar menos resultados
- Considerar fazer overwrite em horários de baixo tráfego

---

## 📊 Métricas e Monitoramento

### Logs Importantes

```java
// Quando overwrite=false e já processado
log.info("Document {} already processed. Skipping. Use overwrite=true to reprocess.", documentId);

// Quando overwrite=true
log.warn("Overwrite enabled. Deleting {} chapters and {} embeddings for document {}",
         chaptersCount, embeddingsCount, documentId);

// Após deleção
log.info("Deleted {} chapters and {} embeddings (via CASCADE) for document {}",
         deletedChapters, embeddingsCount, documentId);
```

### Possíveis Métricas (Futuro)

- `documents_reprocessed_total` (counter)
- `documents_overwrite_requested_total` (counter)
- `chapters_deleted_total` (counter)
- `embeddings_deleted_total` (counter)

---

## ✅ Critérios de Aceitação

1. ✅ Endpoint aceita parâmetro `overwrite` (boolean, default: false)
2. ✅ `overwrite=false` preserva processamento existente
3. ✅ `overwrite=false` com documento já processado retorna HTTP 200
4. ✅ `overwrite=true` exclui Chapters e DocEmbeddings via CASCADE
5. ✅ Logs WARN são emitidos antes de exclusão
6. ✅ Documentação OpenAPI atualizada
7. ✅ Testes unitários passam (cobertura > 80%)
8. ✅ Testes de integração passam
9. ✅ Sem regressões em funcionalidade existente

---

## 🔗 Referências

- **Fluxo de Processamento:** `doc/NEW_PROCESSING_FLOW_PROPOSAL.md`
- **Testes Existentes:** `doc/DOCUMENT_PROCESSING_TESTS.md`
- **Database Schema:** `src/main/resources/db/changelog/003-create-tables.xml`
- **Controller Atual:** `src/main/java/bor/tools/simplerag/controller/DocumentController.java:346`

---

**Autor:** Claude Code
**Revisado por:** _Pendente_
**Aprovado por:** _Pendente_
