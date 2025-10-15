# Service Layer Implementation Plan

## Architecture Overview

### Core Hierarchy
```
Biblioteca (Knowledge Area/Library)
├── Documento (Document - books, articles, manuals)
│   ├── Capítulo (Chapter - ~8k tokens)
│   │   └── DocEmbedding (Chunks - ~2k tokens)
│   └── DocEmbedding (Chapter-level embeddings)
└── DocEmbedding (Document-level embeddings)
```

### Entity Relationships Map

```
┌─────────────────────────────────────────────────────────┐
│                    CORE ENTITIES                        │
└─────────────────────────────────────────────────────────┘

1. User (PK: Integer id, UK: UUID uuid)
   └─── UserLibrary (N:N bridge) ──── Library (PK: Integer id, UK: UUID uuid)
                                          ├── Documento (1:N)
                                          │   ├── Chapter (1:N)
                                          │   │   └── DocumentEmbedding (1:N)
                                          │   └── DocumentEmbedding (1:N)
                                          └── DocumentEmbedding (1:N)

2. User (UUID uuid)
   └── Project (1:N, FK: user_id UUID)
       └── [via metadata.chatIds] Chat[] (N:N via MetaProject)

3. User/Client (UUID)
   └── Chat (1:N, FK: client_uuid UUID)
       ├── ChatMessage (1:N, FK: chat_id UUID, ordered by 'ordem')
       └── [optional] biblioteca_privativa (FK: UUID)

4. Library (UUID uuid)
   └── [optional reference from] Chat.biblioteca_privativa
   └── [optional reference from] Project.biblioteca_privativa
```

**Note**: Project → Chat relationship is managed via `Project.metadata` (instance of `MetaProject` extending `Metadata`) containing chat IDs.

---

## Entity Operations Analysis

### **1. User** (Authentication & Profile)

**Basic Operations:**
- ✅ `save(User)` - Create/update user
- ✅ `delete(User, boolean)` - Soft/hard delete
- ✅ `findById(Integer)` - Find by ID
- ✅ `findByUuid(UUID)` - Find by UUID
- ✅ `findByEmail(String)` - Login/validation

**Granular Loading:**
- `loadUserWithLibraries(UUID userUuid)` → User + UserLibrary[] + Library[]
- `loadUserLibraries(UUID userUuid)` → UserLibrary[] (with TipoAssociacao)
- `loadUserProjects(UUID userUuid)` → Project[]
- `loadUserChats(UUID userUuid)` → Chat[]

---

### **2. Library** (Knowledge Area)

**Basic Operations:**
- ✅ `save(Library)` - Create/update library
- ✅ `delete(Library, boolean)` - Soft/hard delete
- ✅ `findById(Integer)` - Find by ID
- ✅ `findByUuid(UUID)` - Find by UUID

**Granular Loading:**
- `loadLibraryWithDocuments(UUID libraryUuid)` → Library + Documento[]
- `loadLibraryDocuments(UUID libraryUuid, boolean onlyVigente)` → Documento[]
- `loadLibraryUsers(UUID libraryUuid)` → UserLibrary[] + User[]
- `loadLibraryEmbeddings(UUID libraryUuid, TipoEmbedding tipo)` → DocumentEmbedding[]

**Specific Operations:**
- `validateWeights(Library)` - Ensure pesoSemantico + pesoTextual = 1.0
- `getLibraryStats(UUID libraryUuid)` → {docCount, tokenCount, embeddingCount}

---

### **3. UserLibrary** (Bridge User ↔ Library)

**Basic Operations:**
- ✅ `save(UserLibrary)` - Create/update association
- ✅ `delete(UserLibrary, boolean)` - Remove association
- ✅ `findByUserIdAndLibraryId(Integer, Integer)` - Check association

**Granular Loading:**
- `loadUserLibrariesWithDetails(Integer userId)` → UserLibrary[] + Library[]
- `loadLibraryUsersWithDetails(Integer libraryId)` → UserLibrary[] + User[]
- `findByTipoAssociacao(Integer userId, TipoAssociacao tipo)` → UserLibrary[]

---

### **4. Documento** (Complete Content)

**Basic Operations:**
- ✅ `save(Documento)` - Create/update document
- ✅ `delete(Documento, boolean)` - Soft/hard delete
- ✅ `findById(Integer)` - Find by ID

**Granular Loading:**
- `loadDocumentWithChapters(Integer docId)` → Documento + Chapter[]
- `loadDocumentChapters(Integer docId)` → Chapter[] (ordered by ordemDoc)
- `loadDocumentEmbeddings(Integer docId, TipoEmbedding tipo)` → DocumentEmbedding[]
- `loadDocumentHierarchy(Integer docId)` → Documento + Chapter[] + DocumentEmbedding[]

**Specific Operations:**
- `setFlagVigente(Integer docId, boolean vigente)` - Activate/deactivate version
- `findVigenteByLibrary(Integer libraryId)` → Documento[] (only flagVigente=true)

---

### **5. Chapter** (Chapter ~8k tokens)

**Basic Operations:**
- ✅ `save(Chapter)` - Create/update chapter
- ✅ `delete(Chapter, boolean)` - Soft/hard delete
- ✅ `findById(Integer)` - Find by ID

**Granular Loading:**
- `loadChapterWithEmbeddings(Integer chapterId)` → Chapter + DocumentEmbedding[]
- `loadChaptersByDocument(Integer docId)` → Chapter[] (ordered)
- `loadChapterEmbeddings(Integer chapterId, TipoEmbedding tipo)` → DocumentEmbedding[]

**Specific Operations:**
- `calculateTokensTotal(Chapter)` - Auto-calculate tokensTotal (tokenFim - tokenInicio)

---

### **6. DocumentEmbedding** (Search Vectors)

**Basic Operations:**
- ✅ `save(DocumentEmbedding)` - Create/update embedding
- ✅ `delete(DocumentEmbedding, boolean)` - Soft/hard delete
- ✅ `findById(Integer)` - Find by ID

**Granular Loading:**
- `loadEmbeddingsByLibrary(Integer libraryId, TipoEmbedding tipo)` → DocumentEmbedding[]
- `loadEmbeddingsByDocument(Integer docId, TipoEmbedding tipo)` → DocumentEmbedding[]
- `loadEmbeddingsByChapter(Integer chapterId)` → DocumentEmbedding[]

**Specific Operations (JDBC Repository):**
- `searchSimilar(float[] queryVector, Integer libraryId, int limit)` → Semantic search
- `hybridSearch(String query, float[] vector, Integer libraryId)` → Hybrid search
- `validateConsistency(DocumentEmbedding)` - Validate type vs chapter/order

---

### **7. Project** (Groups Chats)

**Basic Operations:**
- ✅ `save(Project)` - Create/update project
- ✅ `delete(Project, boolean)` - Soft/hard delete
- ✅ `findById(UUID)` - Find by UUID

**Granular Loading:**
- `loadProjectWithChats(UUID projectId)` → Project + Chat[] (via metadata.chatIds)
- `loadUserProjects(UUID userId)` → Project[] (ordered by ordem)
- `loadProjectLibrary(UUID projectId)` → Library (if bibliotecaPrivativa)

**Specific Operations:**
- `getNextOrdem(UUID userId)` - Next order number
- `reorderProjects(UUID userId, Integer[] newOrder)` - Reorder projects
- `addChatToProject(UUID projectId, UUID chatId)` - Add chat to metadata.chatIds
- `removeChatFromProject(UUID projectId, UUID chatId)` - Remove chat from metadata.chatIds

---

### **8. Chat** (AI Conversation)

**Basic Operations:**
- ✅ `save(Chat)` - Create/update chat
- ✅ `delete(Chat, boolean)` - Soft/hard delete
- ✅ `findById(UUID)` - Find by UUID

**Granular Loading:**
- `loadChatWithMessages(UUID chatId)` → Chat + ChatMessage[] (ordered)
- `loadChatMessages(UUID chatId, Integer fromOrdem, Integer toOrdem)` → ChatMessage[]
- `loadChatLibrary(UUID chatId)` → Library (if bibliotecaPrivativa)
- `loadUserChats(UUID clientUuid)` → Chat[] (ordered by updatedAt)

**Specific Operations:**
- `generateResumo(UUID chatId)` - Auto-generate chat summary
- `findRecentChats(UUID clientUuid, int limit)` → Chat[] (most recent)

---

### **9. ChatMessage** (Message + Response)

**Basic Operations:**
- ✅ `save(ChatMessage)` - Create/update message
- ✅ `delete(ChatMessage, boolean)` - Soft/hard delete
- ✅ `findById(UUID)` - Find by UUID

**Granular Loading:**
- `loadMessagesByChat(UUID chatId)` → ChatMessage[] (ordered by ordem)
- `loadMessagesBetweenOrdem(UUID chatId, Integer from, Integer to)` → ChatMessage[]
- `loadLastMessage(UUID chatId)` → ChatMessage (last message)

**Specific Operations:**
- `getNextOrdem(UUID chatId)` - Next order number
- `deleteMessagesFromOrdem(UUID chatId, Integer fromOrdem)` - Truncate history
- `searchInMessages(UUID chatId, String text)` - Text search

---

## Implementation Phases

### **PHASE 1 - Core Services (IMPLEMENT NOW)**

| Entity | Priority Operations | Rationale |
|--------|-------------------|-----------|
| **User** | `save()`, `delete(boolean)`, `findByUuid()`, `findByEmail()`, `loadUserWithLibraries()` | Authentication & authorization base |
| **Library** | `save()`, `delete(boolean)`, `findByUuid()`, `validateWeights()`, `loadLibraryWithDocuments()` | RAG core - organizes knowledge |
| **UserLibrary** | `save()`, `delete(boolean)`, `loadUserLibrariesWithDetails()`, `loadLibraryUsersWithDetails()` | Library access control |
| **Project** | `save()`, `delete(boolean)`, `loadUserProjects()`, `getNextOrdem()`, `loadProjectWithChats()` | User work organization |
| **Chat** | `save()`, `delete(boolean)`, `loadChatWithMessages()`, `loadUserChats()` | Main AI interface |
| **ChatMessage** | `save()`, `delete(boolean)`, `loadMessagesByChat()`, `getNextOrdem()` | Conversation history |

### **PHASE 2 - RAG Services (IMPLEMENT LATER)**

| Entity | Priority Operations | Rationale |
|--------|-------------------|-----------|
| **Documento** | `save()`, `delete(boolean)`, `loadDocumentWithChapters()`, `setFlagVigente()` | Async content processing |
| **Chapter** | `save()`, `delete(boolean)`, `loadChaptersByDocument()`, `calculateTokensTotal()` | Content structuring |
| **DocumentEmbedding** | `save()`, `delete(boolean)`, `searchSimilar()`, `hybridSearch()` | Semantic search (JDBC wrapper) |

---

## Standard Operations Pattern

### Interface Template

```java
/**
 * Basic service operations template
 */
public interface BasicService<E, ID> {

    // (a) Create/Update
    E save(E entity);

    // (b) Soft Delete (deletedAt != null) and Hard Delete (physical DELETE)
    void delete(E entity, boolean isHardDelete);

    // (c) Find by ID/UUID
    Optional<E> findById(ID id);

    // (d) Load with associations (granular)
    E loadWithAssociations(ID id, LoadStrategy strategy);
}
```

### Granular Loading Strategy

```java
/**
 * Enum to control loading depth
 */
public enum LoadStrategy {
    BASIC,           // Entity only
    WITH_PARENT,     // Entity + direct parent
    WITH_CHILDREN,   // Entity + direct children
    FULL_HIERARCHY   // Entity + full tree
}
```

---

## Implementation Order

### Phase 1 - 6 Services (IMMEDIATE):

1. **UserService** - Authentication and profile
2. **LibraryService** - Library management
3. **UserLibraryService** - Access control
4. **ProjectService** - Project organization (with MetaProject support)
5. **ChatService** - Conversation management
6. **ChatMessageService** - Message history

### Phase 2 - 3 Services (LATER):

7. **DocumentoService** - Document processing
8. **ChapterService** - Chapter structuring
9. **DocumentEmbeddingService** - Semantic search (JDBC wrapper)

### Service Implementation Pattern:

Each service will have:
- ✅ `save(Entity)` - Create/update with validations
- ✅ `delete(Entity, boolean isHardDelete)` - Soft/hard delete
- ✅ `findById(ID)` - Basic search
- ✅ `loadWith...()` - Granular association loading
- ✅ Domain-specific methods (e.g., `validateWeights()`, `getNextOrdem()`)

---

## Key Implementation Notes

1. **Project → Chat Relationship**: Managed via `Project.metadata` (MetaProject instance) containing `chatIds` array
2. **Soft Delete**: Sets `deletedAt` timestamp in Updatable base class
3. **Hard Delete**: Physical DELETE from database
4. **UUID Strategy**:
   - User, Library: Integer PK + UUID unique key
   - Project, Chat, ChatMessage: UUID as PK
5. **Granular Loading**: Avoids N+1 queries using JOIN FETCH or batch loading
6. **Transaction Management**: All save/delete operations are @Transactional
7. **Validation**: Business rules validated before persistence (e.g., weight sum, flag_vigente uniqueness)
