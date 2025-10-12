# Services DTO Refactoring

## Objetivo

Refatorar todos os services para usar DTOs nas interfaces públicas, ao invés de entidades JPA.

## Benefícios

✅ **Desacoplamento** - Controllers não dependem de entidades JPA
✅ **Segurança** - Evita exposição de campos internos (@ManyToOne, etc)
✅ **Versionamento API** - DTOs podem evoluir independentemente
✅ **Validação** - Bean Validation nos DTOs
✅ **Simplicidade** - Controllers não fazem conversões Entity ↔ DTO

## Padrão de Refatoração

### ANTES (usando Entity)
```java
@Service
public class LibraryService {
    public Library save(Library library) { ... }
    public void delete(Library library, boolean hard) { ... }
    public Optional<Library> findByUuid(UUID uuid) { ... }
}

@RestController
public class LibraryController {
    public ResponseEntity<LibraryDTO> save(@RequestBody LibraryDTO dto) {
        Library entity = toEntity(dto);  // Controller faz conversão
        Library saved = libraryService.save(entity);
        return ResponseEntity.ok(LibraryDTO.from(saved));
    }
}
```

### DEPOIS (usando DTO)
```java
@Service
public class LibraryService {
    public LibraryDTO save(LibraryDTO dto) { ... }
    public void delete(UUID uuid, boolean hard) { ... }
    public Optional<LibraryDTO> findByUuid(UUID uuid) { ... }

    private Library toEntity(LibraryDTO dto) { ... }  // Service faz conversão
}

@RestController
public class LibraryController {
    public ResponseEntity<LibraryDTO> save(@RequestBody LibraryDTO dto) {
        LibraryDTO saved = libraryService.save(dto);  // Direto!
        return ResponseEntity.ok(saved);
    }
}
```

## Services Refatorados

### ✅ LibraryService (COMPLETO)

**Mudanças**:
- `save(Library)` → `save(LibraryDTO)`
- `delete(Library, boolean)` → `delete(UUID, boolean)`
- `findByUuid(UUID)` → retorna `Optional<LibraryDTO>`
- `findByNome(String)` → retorna `Optional<LibraryDTO>`
- Método privado `toEntity(LibraryDTO)` para conversões internas

**LibraryController atualizado**:
- Remove método `toEntity()` do controller
- Chama diretamente `libraryService.save(dto)`
- Não precisa mais fazer conversões

## Services Pendentes

### 🔄 UserService

**Interface atual**:
```java
public User save(User user)
public void delete(User user, boolean isHardDelete)
public Optional<User> findByUuid(UUID uuid)
public Optional<User> findByEmail(String email)
```

**Interface nova (proposta)**:
```java
public UserDTO save(UserDTO dto)
public void delete(UUID uuid, boolean isHardDelete)
public Optional<UserDTO> findByUuid(UUID uuid)
public Optional<UserDTO> findByEmail(String email)
private User toEntity(UserDTO dto)
```

**UserController**: Remover método `toEntity()` (linhas 152-157)

---

### 🔄 ProjectService

**Interface atual**:
```java
public Project save(Project project)
public void delete(Project project, boolean isHardDelete)
public Optional<Project> findById(UUID id)
public Optional<Project> addChatToProject(UUID projectId, UUID chatId)
```

**Interface nova (proposta)**:
```java
public ProjectDTO save(ProjectDTO dto)
public void delete(UUID id, boolean isHardDelete)
public Optional<ProjectDTO> findById(UUID id)
public Optional<ProjectDTO> addChatToProject(UUID projectId, UUID chatId)
private Project toEntity(ProjectDTO dto)
```

**ProjectController**: Remover método `toEntity()` (linhas 184-194)

---

### 🔄 ChatService

**Interface atual**:
```java
public Chat save(Chat chat)
public void delete(Chat chat, boolean isHardDelete)
public Optional<Chat> findById(UUID id)
```

**Interface nova (proposta)**:
```java
public ChatDTO save(ChatDTO dto)
public void delete(UUID id, boolean isHardDelete)
public Optional<ChatDTO> findById(UUID id)
private Chat toEntity(ChatDTO dto)
```

**ChatController**: Remover método `toEntity()` (linhas 191-198)

---

### 🔄 ChatMessageService

**Interface atual**:
```java
public ChatMessage save(ChatMessage chatMessage)
public ChatMessage createMessage(UUID chatId, String mensagem, String response)
public Optional<ChatMessage> updateResponse(UUID messageId, String response)
```

**Interface nova (proposta)**:
```java
public ChatMessageDTO save(ChatMessageDTO dto)
public ChatMessageDTO createMessage(UUID chatId, String mensagem, String response)
public Optional<ChatMessageDTO> updateResponse(UUID messageId, String response)
private ChatMessage toEntity(ChatMessageDTO dto)
```

**ChatController**: Atualizar para receber/retornar DTOs diretamente

---

## Métodos Compostos (WITH-Relations)

Estes métodos retornam **wrapper DTOs** customizados e **já estão corretos**:

- `UserService.loadUserWithLibraries()` → `UserWithLibrariesDTO`
- `LibraryService.loadLibraryWithUsers()` → `LibraryWithUsersDTO`
- `ProjectService.loadProjectWithChats()` → `ProjectWithChatsDTO`
- `ChatService.loadChatWithMessages()` → `ChatWithMessagesDTO`

Estes wrappers são **internos aos services** e já retornam DTOs.

---

## Ordem de Refatoração Sugerida

1. ✅ **LibraryService** (COMPLETO)
2. **UserService** (250 linhas)
3. **ProjectService** (296 linhas)
4. **ChatService** (258 linhas)
5. **ChatMessageService** (337 linhas)

---

## Checklist por Service

Para cada service, fazer:

- [ ] Mudar assinatura `save()` para receber/retornar DTO
- [ ] Mudar assinatura `delete()` para receber UUID
- [ ] Mudar assinatura `findBy*()` para retornar `Optional<DTO>`
- [ ] Criar método privado `toEntity(DTO)` para conversões
- [ ] Atualizar Controller para remover conversões
- [ ] Testar endpoints via Swagger

---

## Impacto nos Controllers

Após refatoração, **todos os controllers ficam minimalistas**:

```java
@PostMapping
public ResponseEntity<DTO> save(@Valid @RequestBody DTO dto) {
    DTO saved = service.save(dto);
    return ResponseEntity.ok(saved);
}

@DeleteMapping("/{uuid}")
public ResponseEntity<Void> delete(@PathVariable UUID uuid,
                                   @RequestParam boolean hard) {
    service.delete(uuid, hard);
    return ResponseEntity.noContent().build();
}

@GetMapping("/{uuid}")
public ResponseEntity<DTO> findByUuid(@PathVariable UUID uuid) {
    DTO result = service.findByUuid(uuid)
        .orElseThrow(() -> new IllegalArgumentException("Not found"));
    return ResponseEntity.ok(result);
}
```

**Nenhuma conversão manual DTO ↔ Entity nos controllers!**

---

## Status Atual

- ✅ LibraryService: **COMPLETO**
- ✅ LibraryController: **COMPLETO**
- 🔄 UserService: Pendente
- 🔄 ProjectService: Pendente
- 🔄 ChatService: Pendente
- 🔄 ChatMessageService: Pendente
