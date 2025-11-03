# Testes de Processamento de Documentos

**Data:** 2025-11-02
**Autor:** Claude Code
**Status:** ✅ Implementado e Compilando

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Testes Criados](#testes-criados)
3. [Cobertura de Testes](#cobertura-de-testes)
4. [Como Executar](#como-executar)
5. [Estrutura dos Testes](#estrutura-dos-testes)

---

## Visão Geral

Este documento descreve a suíte abrangente de testes JUnit criada para validar o fluxo de processamento de documentos conforme especificado em:

- **NEW_PROCESSING_FLOW_PROPOSAL.md** (v1.1)
- **Fluxo_carga_documents.md**

Os testes cobrem desde a camada de controller (endpoints REST) até a camada de serviço, incluindo testes de integração do fluxo completo.

---

## Testes Criados

### 1. DocumentoServiceTest.java

**Localização:** `src/test/java/bor/tools/simplerag/service/DocumentoServiceTest.java`

**Objetivo:** Testes unitários para `DocumentoService` focados nos novos métodos v2.

**Grupos de Testes:**

#### Upload Operations
- ✅ `uploadFromText_ValidData_Success` - Upload bem-sucedido de documento de texto
- ✅ `uploadFromText_LibraryNotFound_ThrowsException` - Validação de biblioteca existente
- ✅ `uploadFromText_ShouldStoreChecksum` - Verificação de armazenamento de checksum
- ✅ `uploadFromUrl_ValidUrl_Success` - Upload de documento via URL
- ✅ `uploadFromFile_ValidFile_Success` - Upload de arquivo multipart

#### Processing Flow v2 (Sequential)
- ✅ `processDocumentAsyncV2_Success` - Processamento bem-sucedido usando novo fluxo
- ✅ `processDocumentAsyncV2_DocumentNotFound_ThrowsException` - Erro quando documento não existe
- ✅ `processDocumentAsyncV2_LibraryNotFound_ThrowsException` - Erro quando biblioteca não existe
- ✅ `processDocumentAsyncV2_ProcessingError_ReturnsFailedStatus` - Tratamento de erros

#### Document Enrichment (Phase 2)
- ✅ `enrichDocumentAsync_WithQAAndSummary_Success` - Enriquecimento com Q&A e resumos
- ✅ `enrichDocumentAsync_WithQAOnly_Success` - Enriquecimento apenas com Q&A
- ✅ `enrichDocumentAsync_DocumentNotFound_ReturnsFailure` - Validação de documento existente
- ✅ `enrichDocumentAsync_EnrichmentError_ReturnsFailure` - Tratamento de erros

#### Retrieval Operations
- ✅ `findAll_ShouldReturnAllDocuments` - Busca todos os documentos
- ✅ `findById_DocumentExists_ReturnsDocument` - Busca por ID
- ✅ `findById_DocumentNotFound_ReturnsEmpty` - Documento não encontrado
- ✅ `findByLibraryId_ShouldReturnDocumentsForLibrary` - Busca por biblioteca
- ✅ `findActiveByLibraryId_ShouldReturnOnlyActiveDocuments` - Busca apenas ativos

#### Status Management
- ✅ `updateStatus_ValidDocument_Success` - Atualização de status
- ✅ `updateStatus_DocumentNotFound_ThrowsException` - Validação de documento
- ✅ `delete_ValidDocument_SoftDelete` - Soft delete (flagVigente=false)

**Total:** 18 testes

---

### 2. DocumentControllerTest.java

**Localização:** `src/test/java/bor/tools/simplerag/controller/DocumentControllerTest.java`

**Objetivo:** Testes de integração para endpoints REST do `DocumentController`.

**Grupos de Testes:**

#### POST /api/v1/documents/upload/text
- ✅ `uploadFromText_ValidRequest_ReturnsCreated` - Upload bem-sucedido (201 Created)
- ✅ `uploadFromText_EmptyTitle_ReturnsBadRequest` - Validação de título obrigatório
- ✅ `uploadFromText_MissingLibraryId_ReturnsBadRequest` - Validação de libraryId
- ✅ `uploadFromText_LibraryNotFound_ThrowsException` - Biblioteca inexistente

#### POST /api/v1/documents/upload/url
- ✅ `uploadFromUrl_ValidRequest_ReturnsCreated` - Upload de URL bem-sucedido
- ✅ `uploadFromUrl_InvalidUrl_ReturnsBadRequest` - Validação de formato de URL

#### POST /api/v1/documents/upload/file
- ✅ `uploadFromFile_ValidFile_ReturnsCreated` - Upload de arquivo bem-sucedido
- ✅ `uploadFromFile_WithCustomTitle_UsesProvidedTitle` - Uso de título customizado
- ✅ `uploadFromFile_WithMetadata_ParsesCorrectly` - Parse de metadata JSON

#### POST /api/v1/documents/{documentId}/process
- ✅ `processDocument_ValidDocument_ReturnsAccepted` - Início de processamento (202 Accepted)
- ✅ `processDocument_WithEnrichment_ReturnsAccepted` - Processamento com enriquecimento
- ✅ `processDocument_DocumentNotFound_ThrowsException` - Documento não encontrado

#### POST /api/v1/documents/{documentId}/enrich
- ✅ `enrichDocument_ValidOptions_ReturnsAccepted` - Enriquecimento bem-sucedido
- ✅ `enrichDocument_QAOnly_ReturnsAccepted` - Enriquecimento apenas com Q&A
- ✅ `enrichDocument_DocumentNotFound_ThrowsException` - Documento não encontrado

#### GET /api/v1/documents/{documentId}/status
- ✅ `getStatus_ValidDocument_ReturnsStatus` - Status de processamento
- ✅ `getStatus_CompletedProcessing_ReturnsCompletedStatus` - Status completado
- ✅ `getStatus_FailedProcessing_ReturnsFailedStatus` - Status com erro

#### Retrieval Endpoints
- ✅ `findAll_ReturnsAllDocuments` - GET /api/v1/documents
- ✅ `getDocument_ValidId_ReturnsDocument` - GET /api/v1/documents/{id}
- ✅ `getDocument_NotFound_ThrowsException` - Documento não encontrado (404)
- ✅ `getDocumentsByLibrary_ReturnsDocuments` - GET /api/v1/documents/library/{libraryId}
- ✅ `getDocumentsByLibrary_ActiveOnly_ReturnsActiveDocuments` - Filtro activeOnly=true

#### Management Endpoints
- ✅ `updateStatus_ValidRequest_ReturnsOk` - POST /api/v1/documents/{id}/status
- ✅ `deleteDocument_ValidId_ReturnsNoContent` - DELETE /api/v1/documents/{id}
- ✅ `deleteDocument_NotFound_ThrowsException` - Delete de documento inexistente

**Total:** 24 testes

---

### 3. DocumentProcessingIntegrationTest.java

**Localização:** `src/test/java/bor/tools/simplerag/integration/DocumentProcessingIntegrationTest.java`

**Objetivo:** Testes de integração do fluxo completo de processamento conforme NEW_PROCESSING_FLOW_PROPOSAL.md.

**Cenários Testados:**

#### Phase 1: Complete Document Processing

##### Documento Pequeno (1 capítulo, sem resumo)
- ✅ `processDocument_SmallDocument_Success`
  - **Input:** Documento com ~1500 tokens
  - **Expected Output:**
    - 1 capítulo criado
    - 1 embedding tipo TRECHO (sem resumo, capítulo pequeno)
    - Todos embeddings processados com sucesso
    - Contextos LLM e Embedding criados corretamente

##### Documento Grande (4 capítulos, com resumos)
- ✅ `processDocument_LargeDocument_Success`
  - **Input:** Documento com ~15,000 tokens
  - **Expected Output:**
    - 4 capítulos criados
    - 3 embeddings tipo RESUMO (capítulos 1, 2, 4 > 2500 tokens)
    - 8 embeddings tipo TRECHO (chunks dos capítulos)
    - Total: ≥8 embeddings
    - Batch processing de embeddings (até 10 por batch)

##### Tolerância a Falhas
- ✅ `processDocument_WithEmbeddingFailures_ContinuesProcessing`
  - **Scenario:** Falha ao atualizar vetores de embedding
  - **Expected Behavior:**
    - Processamento continua apesar de falhas individuais
    - Relatório de embeddings processados vs falhados
    - Status success=true mesmo com falhas parciais

##### Textos Grandes (Oversized Text Handling)
- ✅ `processDocument_WithOversizedText_HandlesCorrectly`
  - **Scenario:** Texto excede contextLength do modelo
  - **Expected Behavior:**
    - Se excedente > 2%: Gera resumo via LLM
    - Se excedente ≤ 2%: Trunca texto
    - Metadata armazenada indicando processamento

**Total:** 4 testes de integração

**Fluxo Validado:**

```
ETAPA 2.1: Criação de Contextos (ANTES do split!)
    ├─ LLMContext.create(library, llmServiceManager)
    └─ EmbeddingContext.create(library, llmServiceManager)
         ↓
ETAPA 2.2: Split e Persistência
    ├─ DocumentRouter.detectContentType()
    ├─ SplitterFactory.createSplitter()
    ├─ splitter.splitDocumento()
    ├─ createChapterEmbeddings() (para cada capítulo)
    │   ├─ Se capítulo > 2500 tokens: cria RESUMO
    │   └─ Split em chunks (~2048 tokens cada)
    ├─ chapterRepository.saveAll()
    └─ embeddingRepository.saveAll() [vectors=NULL]
         ↓
ETAPA 2.3: Cálculo de Embeddings
    ├─ Agrupa em batches (até 10 textos)
    ├─ Para cada batch:
    │   ├─ handleOversizedText() (se necessário)
    │   ├─ embeddingContext.generateEmbeddingsBatch()
    │   └─ embeddingRepository.updateEmbeddingVector()
    └─ Relatório de processamento (sucesso/falhas)
```

---

## Cobertura de Testes

### Cobertura por Componente

| Componente | Testes | Cenários Cobertos |
|-----------|---------|-------------------|
| **DocumentoService** | 18 | Upload (text/url/file), Processing v2, Enrichment, Retrieval, Status |
| **DocumentController** | 24 | Todos os endpoints REST, Validações, Error handling |
| **DocumentProcessingService** | 4 (integration) | Fluxo completo Phase 1, Tolerância a falhas |
| **TOTAL** | **46 testes** | - |

### Cobertura por Funcionalidade

#### Upload de Documentos
- ✅ Upload de texto (markdown/plain)
- ✅ Upload via URL (download + conversão)
- ✅ Upload de arquivo (multipart)
- ✅ Validações de entrada (título, biblioteca, formato)
- ✅ Detecção de duplicados (checksum)
- ✅ Parse de metadata (JSON, key=value, keywords)

#### Processamento (Phase 1)
- ✅ Criação de contextos LLM e Embedding (ANTES do split)
- ✅ Detecção de tipo de conteúdo
- ✅ Split em capítulos e chunks
- ✅ Geração de resumos (capítulos > 2500 tokens)
- ✅ Cálculo de embeddings em batches
- ✅ Tratamento de textos grandes (resumo vs truncamento)
- ✅ Tolerância a falhas (continua processando)

#### Enriquecimento (Phase 2)
- ✅ Geração de Q&A embeddings
- ✅ Geração de summary embeddings
- ✅ Configuração de parâmetros (numberOfQAPairs, maxSummaryLength)
- ✅ Modo fault-tolerant (continueOnError)

#### Consulta e Gerenciamento
- ✅ Busca por ID, biblioteca, status
- ✅ Filtros (activeOnly)
- ✅ Monitoramento de status de processamento
- ✅ Atualização de status (ativar/desativar)
- ✅ Soft delete

### Casos de Erro Cobertos
- ✅ Biblioteca não encontrada
- ✅ Documento não encontrado
- ✅ Validações de entrada (título vazio, URL inválida)
- ✅ Falhas no processamento LLM
- ✅ Falhas na persistência de embeddings
- ✅ Textos que excedem contextLength

---

## Como Executar

### Executar Todos os Testes

```bash
mvn test
```

### Executar Testes de uma Classe Específica

```bash
# DocumentoService
mvn test -Dtest=DocumentoServiceTest

# DocumentController
mvn test -Dtest=DocumentControllerTest

# Integration Tests
mvn test -Dtest=DocumentProcessingIntegrationTest
```

### Executar um Teste Específico

```bash
mvn test -Dtest=DocumentoServiceTest#processDocumentAsyncV2_Success
```

### Executar com Perfil de Integração

```bash
mvn test -P integration-tests
```

### Ver Cobertura de Código

```bash
mvn clean verify
# Relatório em: target/site/jacoco/index.html
```

---

## Estrutura dos Testes

### Padrão de Organização

Todos os testes seguem o padrão:

```java
@Nested
@DisplayName("Grupo de Testes")
class TestGroup {

    @Test
    @DisplayName("Should do something when condition is met")
    void testMethod_Scenario_ExpectedBehavior() {
        // Arrange - Setup

        // Act - Execute

        // Assert - Verify
    }
}
```

### Nomenclatura

- **Método de teste:** `methodName_scenario_expectedBehavior`
- **Display name:** Descrição clara e legível do teste
- **Grupos:** Organizados por funcionalidade usando `@Nested`

### Mocks e Stubs

**DocumentoServiceTest:**
- Mocks: Repositories, Services (Library, Processing, Orchestrator)
- Foco: Lógica de negócio isolada

**DocumentControllerTest:**
- Mocks: DocumentoService, StatusTracker
- Uso: MockMvc para simular requisições HTTP
- Validações: Status codes, JSON responses

**DocumentProcessingIntegrationTest:**
- Mocks: Componentes externos (LLMService, Repositories)
- Foco: Fluxo completo end-to-end
- Validações: Sequência de operações, resultados finais

---

## Próximos Passos

### Melhorias Sugeridas

1. **Adicionar Testes de Performance**
   - Processar documento muito grande (>100k tokens)
   - Benchmark de tempo de processamento
   - Teste de throughput (múltiplos documentos)

2. **Testes de Concorrência**
   - Processar múltiplos documentos simultaneamente
   - Verificar isolamento de transações
   - Testar race conditions

3. **Testes de Banco de Dados**
   - Testes com banco real (Testcontainers)
   - Validar índices e performance de queries
   - Testar migração de dados

4. **Testes de Resiliência**
   - Simular falhas de rede
   - Timeout de LLM services
   - Recuperação de processamento interrompido

5. **Aumentar Cobertura**
   - Testar mais combinações de parâmetros
   - Edge cases (documentos vazios, muito pequenos)
   - Diferentes formatos de arquivo

---

## Referências

- [NEW_PROCESSING_FLOW_PROPOSAL.md](./NEW_PROCESSING_FLOW_PROPOSAL.md) - Especificação do novo fluxo (v1.1)
- [Fluxo_carga_documents.md](./Fluxo_carga_documents.md) - Fluxo original de carga
- [TESTING_FRAMEWORK_SUMMARY.md](./TESTING_FRAMEWORK_SUMMARY.md) - Framework de testes do projeto

---

**Documento gerado por:** Claude Code
**Data:** 2025-11-02
**Status:** ✅ Testes implementados e compilando corretamente
