# Controller Design Plan - JSimpleRag

## Overview

JSimpleRag é uma aplicação RAG (Retrieval Augmented Generation) com foco em **pesquisa híbrida** (semântica + textual). Os controllers devem refletir essa prioridade:

1. **Endpoint Principal**: Pesquisa RAG (híbrida, semântica, textual)
2. **Endpoints Utilitários**: CRUD minimalista para gerenciamento de dados

---

## Design Philosophy

### Princípios
- **RAG-First**: Endpoint de pesquisa é a operação crítica e mais complexa
- **Minimalismo**: Endpoints utilitários devem ser simples (save, soft delete)
- **DTOs**: Usar DTOs existentes para request/response
- **OpenAPI/Swagger**: Documentação automática com anotações
- **Error Handling**: Retornos padronizados com ResponseEntity

### API Structure
```
/api/v1/search              → RAG Search Controller (PRIORITÁRIO)
/api/v1/libraries           → Library Controller
/api/v1/users               → User Controller
/api/v1/projects            → Project Controller
/api/v1/chats               → Chat Controller
/api/v1/documents           → Document Controller (Phase 2)
```

---

## 1. RAG Search Controller (PRIORITÁRIO)

### Responsibility
Endpoint central da aplicação - executa pesquisa híbrida (semântica + textual) usando `DocEmbeddingJdbcRepository`.

### Endpoints

#### POST /api/v1/search/hybrid
Pesquisa híbrida (semântica + textual) em múltiplas bibliotecas

**Request DTO**:

```java
public class SearchRequest {
    @NotBlank String query;               // Texto da consulta
    @NotNull Integer[] libraryIds;        // IDs das bibliotecas
    Integer limit;                        // Opcional - default 10
    Float pesoSemantico;                  // Opcional - default 0.6
    Float pesoTextual;                    // Opcional - default 0.4
}
```

**Response DTO**:

```java
public class SearchResponse {
    String query;                         // Echo da query
    Integer[] libraryIds;                 // Bibliotecas pesquisadas
    Integer totalResults;                 // Total de resultados
    Float pesoSemantico;                  // Peso aplicado
    Float pesoTextual;                    // Peso aplicado
    List<SearchResultDTO> results;        // Resultados ordenados por score
}

public class SearchResultDTO {
    Integer embeddingId;                  // ID do embedding
    Integer documentoId;                  // ID do documento
    String documentoTitulo;               // Título do documento
    Integer capituloId;                   // ID do capítulo (nullable)
    String capituloTitulo;                // Título do capítulo (nullable)
    String trechoTexto;                   // Trecho de texto relevante
    TipoEmbedding tipoEmbedding;          // DOCUMENT/CHAPTER/CHUNK
    Float scoreSemantico;                 // Score semântico
    Float scoreTextual;                   // Score textual
    Float score;                          // Score combinado (final)
    Map<String, Object> metadados;        // Metadados do embedding
}
```

**Implementation Notes**:
- Gerar embedding do query usando `EmbeddingProcessorInterface`
- Chamar `docEmbeddingJdbcRepository.pesquisaHibrida()`
- Enriquecer resultados com informações de Documento/Capítulo
- Aplicar pesos configurados ou usar defaults das bibliotecas

---

#### POST /api/v1/search/semantic
Pesquisa semântica pura (apenas embeddings)

**Request DTO**:

```java
public class SemanticSearchRequest {
    @NotBlank String query;
    @NotNull Integer[] libraryIds;
    Integer limit;
}
```

**Response**: Mesma `SearchResponse` mas com `scoreTextual = 0`

**Implementation**:
- Gerar embedding
- Chamar `docEmbeddingJdbcRepository.pesquisaSemantica()`

---

#### POST /api/v1/search/textual
Pesquisa textual pura (apenas full-text search)

**Request DTO**:

```java
public class TextualSearchRequest {
    @NotBlank String query;
    @NotNull Integer[] libraryIds;
    Integer limit;
}
```

**Response**: Mesma `SearchResponse` mas com `scoreSemantico = 0`

**Implementation**:
- Chamar `docEmbeddingJdbcRepository.pesquisaTextual()`

---

### Controller Structure

```java
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RAG Search", description = "Hybrid search endpoints (semantic + textual)")
public class SearchController {

    private final DocEmbeddingJdbcRepository embeddingRepository;
    private final EmbeddingProcessorInterface embeddingProcessor;
    private final DocumentoRepository documentoRepository;
    private final CapituloRepository capituloRepository;

    @PostMapping("/hybrid")
    @Operation(summary = "Hybrid search (semantic + textual)")
    public ResponseEntity<SearchResponse> hybridSearch(@Valid @RequestBody SearchRequest request) {
        // 1. Generate query embedding
        // 2. Call embeddingRepository.pesquisaHibrida()
        // 3. Enrich results with document/chapter info
        // 4. Return SearchResponse
    }

    @PostMapping("/semantic")
    @Operation(summary = "Semantic search only")
    public ResponseEntity<SearchResponse> semanticSearch(@Valid @RequestBody SemanticSearchRequest request) {
        // Similar to hybrid but calls pesquisaSemantica()
    }

    @PostMapping("/textual")
    @Operation(summary = "Textual search only")
    public ResponseEntity<SearchResponse> textualSearch(@Valid @RequestBody TextualSearchRequest request) {
        // Calls pesquisaTextual()
    }
}
```

---

## 2. Library Controller

### Responsibility
Gerenciamento de bibliotecas (knowledge areas)

### Endpoints

#### POST /api/v1/libraries
Criar/atualizar biblioteca

**Request**: `LibraryDTO` (existente)
**Response**: `LibraryDTO`

**Business Rules**:
- Validar `pesoSemantico + pesoTextual = 1.0`
- Gerar UUID se não existir

---

#### DELETE /api/v1/libraries/{uuid}?hard=false
Soft/hard delete

**Query Param**: `hard` (boolean, default false)
**Response**: 204 No Content

**Business Rules**:
- Soft delete: Set `deletedAt`
- Hard delete: Remove biblioteca + cascata de UserLibrary

---

#### GET /api/v1/libraries/{uuid}
Buscar biblioteca por UUID

**Response**: `LibraryDTO`

---

#### GET /api/v1/libraries/{uuid}/with-users
Buscar biblioteca com usuários associados

**Response**:
```java
public class LibraryWithUsersDTO {
    LibraryDTO library;
    List<UserWithAssociationDTO> users;
}
```

---

### Controller Structure

```java
@RestController
@RequestMapping("/api/v1/libraries")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Libraries", description = "Library management")
public class LibraryController {

    private final LibraryService libraryService;

    @PostMapping
    @Operation(summary = "Create or update library")
    public ResponseEntity<LibraryDTO> save(@Valid @RequestBody LibraryDTO dto) {
        // Convert DTO → Entity
        // Call libraryService.save()
        // Convert Entity → DTO
    }

    @DeleteMapping("/{uuid}")
    @Operation(summary = "Delete library (soft or hard)")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
                                       @RequestParam(defaultValue = "false") boolean hard) {
        // Find by UUID
        // Call libraryService.delete(library, hard)
        // Return 204 No Content
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get library by UUID")
    public ResponseEntity<LibraryDTO> findByUuid(@PathVariable UUID uuid) {
        // Call libraryService.findByUuid()
        // Convert to DTO
    }

    @GetMapping("/{uuid}/with-users")
    @Operation(summary = "Get library with associated users")
    public ResponseEntity<LibraryWithUsersDTO> getWithUsers(@PathVariable UUID uuid) {
        // Call libraryService.loadLibraryWithUsers()
        // Convert to DTO
    }
}
```

---

## 3. User Controller

### Endpoints

#### POST /api/v1/users
Criar/atualizar usuário

**Request**: `UserDTO`
**Response**: `UserDTO`

---

#### DELETE /api/v1/users/{uuid}?hard=false
Soft/hard delete

---

#### GET /api/v1/users/{uuid}
Buscar usuário por UUID

---

#### GET /api/v1/users/{uuid}/with-libraries
Buscar usuário com bibliotecas associadas

**Response**:

```java
public class UserWithLibrariesDTO {
    UserDTO user;
    List<LibraryWithAssociationDTO> libraries;
}
```

---

### Controller Structure

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management")
public class UserController {
    // Estrutura similar ao LibraryController
    // Endpoints: POST, DELETE, GET, GET /with-libraries
}
```

---

## 4. Project Controller

### Endpoints

#### POST /api/v1/projects
Criar/atualizar projeto

**Request**: `ProjectDTO`
**Response**: `ProjectDTO`

**Business Rules**:
- Auto-increment `ordem` se não fornecido

---

#### DELETE /api/v1/projects/{uuid}?hard=false
Soft/hard delete

---

#### GET /api/v1/projects/{uuid}
Buscar projeto por UUID

---

#### GET /api/v1/projects/{uuid}/with-chats
Buscar projeto com chats (via MetaProject)

**Response**:

```java
public class ProjectWithChatsDTO {
    ProjectDTO project;
    List<ChatDTO> chats;
}
```

---

#### PUT /api/v1/projects/{projectId}/chats/{chatId}
Adicionar chat ao projeto (via MetaProject)

**Response**: `ProjectDTO` atualizado

---

#### DELETE /api/v1/projects/{projectId}/chats/{chatId}
Remover chat do projeto

**Response**: `ProjectDTO` atualizado

---

### Controller Structure

```java
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Projects", description = "Project management")
public class ProjectController {

    private final ProjectService projectService;

    // CRUD básico: POST, DELETE, GET

    @GetMapping("/{uuid}/with-chats")
    @Operation(summary = "Get project with associated chats")
    public ResponseEntity<ProjectWithChatsDTO> getWithChats(@PathVariable UUID uuid) {
        // Call projectService.loadProjectWithChats()
    }

    @PutMapping("/{projectId}/chats/{chatId}")
    @Operation(summary = "Add chat to project")
    public ResponseEntity<ProjectDTO> addChat(@PathVariable UUID projectId,
                                              @PathVariable UUID chatId) {
        // Call projectService.addChatToProject()
    }

    @DeleteMapping("/{projectId}/chats/{chatId}")
    @Operation(summary = "Remove chat from project")
    public ResponseEntity<ProjectDTO> removeChat(@PathVariable UUID projectId,
                                                  @PathVariable UUID chatId) {
        // Call projectService.removeChatFromProject()
    }
}
```

---

## 5. Chat Controller

### Endpoints

#### POST /api/v1/chats
Criar/atualizar chat

**Request**: `ChatDTO`
**Response**: `ChatDTO`

---

#### DELETE /api/v1/chats/{uuid}?hard=false
Soft/hard delete

**Note**: Hard delete também remove todas as mensagens (cascade)

---

#### GET /api/v1/chats/{uuid}
Buscar chat por UUID

---

#### GET /api/v1/chats/{uuid}/with-messages
Buscar chat com mensagens

**Response**:

```java
public class ChatWithMessagesDTO {
    ChatDTO chat;
    List<ChatMessageDTO> messages;
    Integer messageCount;
}
```

---

#### POST /api/v1/chats/{uuid}/messages
Criar nova mensagem no chat

**Request**:

```java
public class CreateMessageRequest {
    @NotBlank String mensagem;
    String response;  // Opcional
}
```

**Response**: `ChatMessageDTO`

---

#### PUT /api/v1/chats/{chatId}/messages/{messageId}/response
Atualizar resposta de uma mensagem

**Request**:

```java
public class UpdateResponseRequest {
    @NotBlank String response;
}
```

**Response**: `ChatMessageDTO`

---

### Controller Structure

```java
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chats", description = "Chat management")
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageService chatMessageService;

    // CRUD básico: POST, DELETE, GET

    @GetMapping("/{uuid}/with-messages")
    @Operation(summary = "Get chat with messages")
    public ResponseEntity<ChatWithMessagesDTO> getWithMessages(@PathVariable UUID uuid) {
        // Call chatService.loadChatWithMessages()
    }

    @PostMapping("/{uuid}/messages")
    @Operation(summary = "Create new message in chat")
    public ResponseEntity<ChatMessageDTO> createMessage(@PathVariable UUID uuid,
                                                         @Valid @RequestBody CreateMessageRequest request) {
        // Call chatMessageService.createMessage()
    }

    @PutMapping("/{chatId}/messages/{messageId}/response")
    @Operation(summary = "Update message response")
    public ResponseEntity<ChatMessageDTO> updateResponse(@PathVariable UUID chatId,
                                                          @PathVariable UUID messageId,
                                                          @Valid @RequestBody UpdateResponseRequest request) {
        // Call chatMessageService.updateResponse()
    }
}
```

---

## Phase 2 Controllers (Futura Implementação)

### DocumentController
- POST /api/v1/documents - Upload/criar documento
- POST /api/v1/documents/{id}/process - Processar documento (async)
- GET /api/v1/documents/{id}/status - Status de processamento
- DELETE /api/v1/documents/{id}

### ChapterController
- GET /api/v1/chapters/{id}
- GET /api/v1/chapters/{id}/embeddings

---

## Global Error Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    // Outros handlers...
}

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String message;
    private LocalDateTime timestamp = LocalDateTime.now();

    public ErrorResponse(String message) {
        this.message = message;
    }
}
```

---

## DTOs to Create

### Search DTOs

- `SearchRequest`
- `SemanticSearchRequest`
- `TextualSearchRequest`
- `SearchResponse`
- `SearchResultDTO`

### Composite DTOs
- `LibraryWithUsersDTO`
- `UserWithLibrariesDTO`
- `ProjectWithChatsDTO`
- `ChatWithMessagesDTO`
- `UserWithAssociationDTO`
- `LibraryWithAssociationDTO`

### Request DTOs
- `CreateMessageRequest`
- `UpdateResponseRequest`

### Response DTOs
- `ErrorResponse`

---

## Implementation Priority

### Phase 1 (IMMEDIATE)
1. ✅ **SearchController** - Endpoint principal da aplicação RAG
2. ✅ LibraryController - Gerenciamento de bibliotecas
3. ✅ UserController - Gerenciamento de usuários
4. ✅ ProjectController - Gerenciamento de projetos
5. ✅ ChatController - Gerenciamento de chats

### Phase 2 (AFTER)
- DocumentController - Upload e processamento de documentos
- ChapterController - Gerenciamento de capítulos

---

## Configuration Notes

### OpenAPI/Swagger
- Configurado em `application.properties`
- URL: `http://localhost:8080/swagger-ui.html`
- API Docs: `http://localhost:8080/api-docs`

### CORS (se necessário)

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }
}
```

---

## Testing Strategy

### Integration Tests

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testHybridSearch() throws Exception {
        mockMvc.perform(post("/api/v1/search/hybrid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "query": "test query",
                        "libraryIds": [1, 2],
                        "limit": 10
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").exists());
    }
}
```

---

## Summary

Este plano define controllers **minimalistas** focados na operação principal da aplicação RAG:

1. **SearchController** é o endpoint crítico (pesquisa híbrida)
2. Demais controllers são **utilitários** com CRUD básico (save, delete, find)
3. Endpoints de **associação granular** (`/with-users`, `/with-chats`) para carregar entidades relacionadas
4. **DTOs** para request/response desacoplados das entidades
5. **OpenAPI/Swagger** para documentação automática
6. **Error handling** global para respostas padronizadas

**Next Steps**: Implementar DTOs faltantes e iniciar com SearchController (prioritário).
