# API Usage Examples - JSimpleRag

Complete guide with practical examples for using the JSimpleRag API.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Library Management](#library-management)
3. [Document Management](#document-management)
4. [User-Library Associations](#user-library-associations)
5. [Search Operations](#search-operations)
6. [Common Workflows](#common-workflows)
7. [Error Handling](#error-handling)

---

## Quick Start

### Prerequisites

- JSimpleRag running at `http://localhost:8080`
- Swagger UI available at `http://localhost:8080/swagger-ui.html`
- PostgreSQL with PGVector extension
- LLM service configured (for embeddings)

### Complete Workflow (5 Minutes)

```bash
# 1. Create a library
LIBRARY_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/libraries \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Software Engineering",
    "areaConhecimento": "Technology",
    "pesoSemantico": 0.70,
    "pesoTextual": 0.30
  }')

LIBRARY_UUID=$(echo $LIBRARY_RESPONSE | jq -r '.uuid')
echo "Library created: $LIBRARY_UUID"

# 2. Upload a document
DOC_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/documents/upload/text \
  -H "Content-Type: application/json" \
  -d "{
    \"titulo\": \"Clean Code Principles\",
    \"conteudo\": \"# Clean Code\\n\\n## Introduction\\nClean code is code that is easy to understand and easy to change...\\n\\n## Naming Conventions\\nUse meaningful names that reveal intent...\",
    \"libraryId\": 1,
    \"metadados\": {
      \"autor\": \"Robert C. Martin\",
      \"tipo_conteudo\": \"1\"
    }
  }")

DOC_ID=$(echo $DOC_RESPONSE | jq -r '.id')
echo "Document created: $DOC_ID"

# 3. Process the document
curl -X POST http://localhost:8080/api/v1/documents/$DOC_ID/process

# 4. Check processing status (repeat until COMPLETED)
curl http://localhost:8080/api/v1/documents/$DOC_ID/status

# 5. Search for content
curl -X POST http://localhost:8080/api/v1/search/hybrid \
  -H "Content-Type: application/json" \
  -d '{
    "query": "naming conventions in clean code",
    "libraryIds": [1],
    "limit": 5
  }'
```

---

## Library Management

### Understanding Search Weight Configuration

Libraries in JSimpleRag use a hybrid search approach combining:
- **Semantic Search**: Vector-based similarity (embeddings)
- **Textual Search**: Traditional full-text search with PostgreSQL

The weights determine the balance between these two approaches and **must sum to 1.0**.

#### Weight Configuration Guide

| Content Type | Semantic | Textual | Rationale |
|-------------|----------|---------|-----------|
| **Legal/Regulatory** | 0.4 | 0.6 | Precise term matching (article numbers, law names) is critical |
| **Technical Docs** | 0.7 | 0.3 | Conceptual understanding more important than exact terms |
| **Scientific Articles** | 0.6 | 0.4 | Balance between concepts and precise terminology |
| **General Knowledge** | 0.6 | 0.4 | Default balanced configuration |
| **News/Blog Posts** | 0.5 | 0.5 | Equal importance |

### Create Library

```bash
curl -X POST http://localhost:8080/api/v1/libraries \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Brazilian Legislation",
    "areaConhecimento": "Legal",
    "pesoSemantico": 0.4,
    "pesoTextual": 0.6,
    "metadados": {
      "description": "Federal and state legislation database",
      "coverage": "2000-present"
    }
  }'
```

**Response:**
```json
{
  "id": 1,
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "Brazilian Legislation",
  "areaConhecimento": "Legal",
  "pesoSemantico": 0.4,
  "pesoTextual": 0.6,
  "createdAt": "2025-10-17T10:00:00",
  "updatedAt": "2025-10-17T10:00:00"
}
```

### Get Library by UUID

```bash
curl http://localhost:8080/api/v1/libraries/550e8400-e29b-41d4-a716-446655440000
```

### Get Library with Users

```bash
curl http://localhost:8080/api/v1/libraries/550e8400-e29b-41d4-a716-446655440000/with-users
```

**Response:**
```json
{
  "library": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "nome": "Brazilian Legislation",
    ...
  },
  "users": [
    {
      "user": {
        "uuid": "a60e9500-f39c-52e5-b827-557766551111",
        "nome": "João Silva"
      },
      "tipoAssociacao": "PROPRIETARIO",
      "associationId": 1
    },
    {
      "user": {
        "uuid": "b70e9600-g40d-63f6-c938-668877662222",
        "nome": "Maria Santos"
      },
      "tipoAssociacao": "LEITOR",
      "associationId": 2
    }
  ],
  "userCount": 2
}
```

### Get All Knowledge Areas

```bash
curl http://localhost:8080/api/v1/libraries/areas
```

**Response:**
```json
[
  "Legal",
  "Technology",
  "Medicine",
  "Engineering"
]
```

### Delete Library

**Soft Delete (Recommended):**
```bash
curl -X DELETE http://localhost:8080/api/v1/libraries/550e8400-e29b-41d4-a716-446655440000?hard=false
```

**Hard Delete (Irreversible):**
```bash
# ⚠️ WARNING: This permanently deletes the library and ALL associated data!
curl -X DELETE http://localhost:8080/api/v1/libraries/550e8400-e29b-41d4-a716-446655440000?hard=true
```

---

## Document Management

### Upload Methods Comparison

| Method | Best For | Automatic Conversion | Title Extraction |
|--------|----------|----------------------|------------------|
| **Upload from Text** | Markdown/Plain text content you already have | N/A | No (required) |
| **Upload from URL** | Web pages, online PDFs | Yes | Yes |
| **Upload from File** | Local PDFs, DOCX, etc. | Yes | Yes (from filename) |

### 1. Upload from Text

**Use Case:** You have markdown or plain text content ready to upload.

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload/text \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Design Patterns in Java",
    "conteudo": "# Design Patterns\n\n## Creational Patterns\n\n### Singleton\nEnsures a class has only one instance...\n\n### Factory Method\nDefines an interface for creating objects...",
    "libraryId": 1,
    "metadados": {
      "autor": "Gang of Four",
      "tipo_conteudo": "1",
      "palavras_chave": "design patterns,java,software architecture",
      "isbn": "978-0201633610"
    }
  }'
```

### 2. Upload from URL

**Use Case:** Import content from a web page or online document.

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload/url \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/technical-specification.pdf",
    "libraryId": 1,
    "titulo": "System Technical Specification",
    "metadados": {
      "tipo_conteudo": "4",
      "versao": "2.1"
    }
  }'
```

**Note:** Title is optional - will be extracted from HTML `<title>`, PDF metadata, or URL filename if not provided.

### 3. Upload from File

**Use Case:** Upload a local document file (PDF, DOCX, etc.).

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload/file \
  -F "file=@/path/to/document.pdf" \
  -F "libraryId=1" \
  -F "titulo=Technical Manual v3.0" \
  -F 'metadata={"autor":"Engineering Team","tipo_conteudo":"4"}'
```

**Supported formats:**
- PDF (.pdf)
- Microsoft Word (.doc, .docx)
- Text files (.txt, .md)
- OpenDocument (.odt)
- Rich Text Format (.rtf)

### Process Document

After uploading, you must **explicitly trigger processing** to generate embeddings:

```bash
# Start processing
curl -X POST http://localhost:8080/api/v1/documents/123/process?includeQA=false&includeSummary=false
```

**Response:**
```json
{
  "message": "Document processing started",
  "documentId": 123,
  "titulo": "Design Patterns in Java",
  "statusUrl": "/api/v1/documents/123/status",
  "estimatedTime": "1-10 minutes"
}
```

### Monitor Processing Status

```bash
# Check status (call repeatedly until COMPLETED)
curl http://localhost:8080/api/v1/documents/123/status
```

**Response (Processing):**
```json
{
  "documentId": 123,
  "titulo": "Design Patterns in Java",
  "status": "PROCESSING",
  "statusDescription": "Processing in progress",
  "progress": 45,
  "message": "Generating chapter embeddings: 3/7",
  "startedAt": "2025-10-17T10:05:00",
  "updatedAt": "2025-10-17T10:06:30",
  "completedAt": null,
  "tokensTotal": null,
  "flagVigente": true,
  "createdAt": "2025-10-17T10:05:00"
}
```

**Response (Completed):**
```json
{
  "documentId": 123,
  "titulo": "Design Patterns in Java",
  "status": "COMPLETED",
  "statusDescription": "Processing completed successfully",
  "progress": 100,
  "message": "Successfully processed 7 chapters and 42 chunks",
  "startedAt": "2025-10-17T10:05:00",
  "updatedAt": "2025-10-17T10:08:15",
  "completedAt": "2025-10-17T10:08:15",
  "tokensTotal": 15420,
  "flagVigente": true,
  "createdAt": "2025-10-17T10:05:00"
}
```

### Get Document by ID

```bash
curl http://localhost:8080/api/v1/documents/123
```

### Get Documents by Library

```bash
# Get all documents
curl http://localhost:8080/api/v1/documents/library/1?activeOnly=false

# Get only active (flagVigente=true) documents
curl http://localhost:8080/api/v1/documents/library/1?activeOnly=true
```

### Update Document Status

```bash
# Activate document
curl -X POST http://localhost:8080/api/v1/documents/123/status?flagVigente=true

# Deactivate document
curl -X POST http://localhost:8080/api/v1/documents/123/status?flagVigente=false
```

### Delete Document (Soft Delete)

```bash
curl -X DELETE http://localhost:8080/api/v1/documents/123
```

**Note:** Sets `flagVigente=false`. Document remains in database.

---

## User-Library Associations

### Understanding Association Types

| Type | Portuguese | Permissions |
|------|-----------|-------------|
| **PROPRIETARIO** | Proprietário (Owner) | Full access: edit settings, delete library, manage users |
| **COLABORADOR** | Colaborador (Collaborator) | Read-write: add/edit documents, cannot delete library |
| **LEITOR** | Leitor (Reader) | Read-only: search and view documents |

### Create Association

```bash
curl -X POST http://localhost:8080/api/v1/user-libraries \
  -H "Content-Type: application/json" \
  -d '{
    "userUuid": "a60e9500-f39c-52e5-b827-557766551111",
    "libraryUuid": "550e8400-e29b-41d4-a716-446655440000",
    "tipoAssociacao": "COLABORADOR"
  }'
```

**Response:**
```json
{
  "associationId": 5,
  "userUuid": "a60e9500-f39c-52e5-b827-557766551111",
  "userName": "João Silva",
  "libraryUuid": "550e8400-e29b-41d4-a716-446655440000",
  "libraryName": "Brazilian Legislation",
  "tipoAssociacao": "COLABORADOR",
  "createdAt": "2025-10-17T11:00:00"
}
```

### Get Association

```bash
curl http://localhost:8080/api/v1/user-libraries/user/a60e9500-f39c-52e5-b827-557766551111/library/550e8400-e29b-41d4-a716-446655440000
```

### Update Association Type

```bash
curl -X PUT http://localhost:8080/api/v1/user-libraries/user/a60e9500-f39c-52e5-b827-557766551111/library/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -d '{
    "tipoAssociacao": "LEITOR"
  }'
```

### Delete Association

**Soft Delete:**
```bash
curl -X DELETE "http://localhost:8080/api/v1/user-libraries/user/a60e9500-f39c-52e5-b827-557766551111/library/550e8400-e29b-41d4-a716-446655440000?hard=false"
```

**Hard Delete:**
```bash
curl -X DELETE "http://localhost:8080/api/v1/user-libraries/user/a60e9500-f39c-52e5-b827-557766551111/library/550e8400-e29b-41d4-a716-446655440000?hard=true"
```

---

## Search Operations

### Hybrid Search (Recommended)

Combines semantic and textual search using library-configured weights.

```bash
curl -X POST http://localhost:8080/api/v1/search/hybrid \
  -H "Content-Type: application/json" \
  -d '{
    "query": "singleton pattern thread safety",
    "libraryIds": [1],
    "limit": 10,
    "apenasVigentes": true
  }'
```

**With Custom Weights:**
```bash
curl -X POST http://localhost:8080/api/v1/search/hybrid \
  -H "Content-Type: application/json" \
  -d '{
    "query": "article 5 federal constitution",
    "libraryIds": [2],
    "limit": 5,
    "pesoSemantico": 0.3,
    "pesoTextual": 0.7,
    "apenasVigentes": true
  }'
```

### Semantic Search Only

Best for conceptual queries, understanding intent.

```bash
curl -X POST http://localhost:8080/api/v1/search/semantic \
  -H "Content-Type: application/json" \
  -d '{
    "query": "How to ensure thread safety in Java applications?",
    "libraryIds": [1],
    "limit": 10
  }'
```

### Textual Search Only

Best for exact term matching, names, numbers.

```bash
curl -X POST http://localhost:8080/api/v1/search/textual \
  -H "Content-Type: application/json" \
  -d '{
    "query": "\"article 5\" AND constitution -amendment",
    "libraryIds": [2],
    "limit": 20
  }'
```

**Textual Search Syntax:**
- `word1 word2` - OR search (either word)
- `"exact phrase"` - Exact phrase match
- `word1 -word2` - Exclude word2
- Automatic stemming for Portuguese

---

## Common Workflows

### Workflow 1: Set Up New Knowledge Base

```bash
#!/bin/bash

# Step 1: Create library
LIBRARY_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/libraries \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Product Documentation",
    "areaConhecimento": "Technology",
    "pesoSemantico": 0.7,
    "pesoTextual": 0.3
  }')

LIBRARY_ID=$(echo $LIBRARY_RESPONSE | jq -r '.id')
LIBRARY_UUID=$(echo $LIBRARY_RESPONSE | jq -r '.uuid')

echo "✅ Library created: $LIBRARY_UUID"

# Step 2: Associate users
curl -s -X POST http://localhost:8080/api/v1/user-libraries \
  -H "Content-Type: application/json" \
  -d "{
    \"userUuid\": \"USER_UUID_HERE\",
    \"libraryUuid\": \"$LIBRARY_UUID\",
    \"tipoAssociacao\": \"PROPRIETARIO\"
  }" > /dev/null

echo "✅ User associated as owner"

# Step 3: Upload documents
for file in docs/*.pdf; do
  echo "Uploading: $file"
  DOC_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/documents/upload/file \
    -F "file=@$file" \
    -F "libraryId=$LIBRARY_ID")

  DOC_ID=$(echo $DOC_RESPONSE | jq -r '.id')

  # Process document
  curl -s -X POST http://localhost:8080/api/v1/documents/$DOC_ID/process > /dev/null

  echo "  ✅ Uploaded and processing started: $DOC_ID"
done

echo "🎉 Knowledge base setup complete!"
```

### Workflow 2: Document Versioning

When uploading a new version of an existing document:

```bash
# Upload new version with same title
curl -X POST http://localhost:8080/api/v1/documents/upload/text \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "API Documentation",
    "conteudo": "# API Documentation v2.0...",
    "libraryId": 1,
    "flagVigente": true,
    "metadados": {
      "version": "2.0",
      "changelog": "Added authentication section"
    }
  }'
```

**Automatic Behavior:**
- Previous version with same title is automatically set to `flagVigente=false`
- New version becomes the active version
- Old versions remain searchable if `apenasVigentes=false` in search

### Workflow 3: Monitoring Processing Queue

```bash
#!/bin/bash

DOC_IDS=(123 124 125 126)

while true; do
  clear
  echo "=== Document Processing Status ==="
  echo ""

  ALL_COMPLETE=true

  for DOC_ID in "${DOC_IDS[@]}"; do
    STATUS=$(curl -s http://localhost:8080/api/v1/documents/$DOC_ID/status)

    STATUS_VALUE=$(echo $STATUS | jq -r '.status')
    PROGRESS=$(echo $STATUS | jq -r '.progress')
    MESSAGE=$(echo $STATUS | jq -r '.message')

    printf "Doc %3d: [%-10s] %3d%% - %s\n" $DOC_ID "$STATUS_VALUE" $PROGRESS "$MESSAGE"

    if [ "$STATUS_VALUE" != "COMPLETED" ] && [ "$STATUS_VALUE" != "FAILED" ]; then
      ALL_COMPLETE=false
    fi
  done

  if $ALL_COMPLETE; then
    echo ""
    echo "✅ All documents processed!"
    break
  fi

  sleep 5
done
```

---

## Error Handling

### Standard Error Response

All errors follow this format:

```json
{
  "codigo": "ERROR_CODE",
  "mensagem": "Human-readable error message",
  "timestamp": "2025-10-17T10:30:00",
  "detalhes": {
    "field1": "error detail",
    "field2": "error detail"
  }
}
```

### Common Error Codes

| Code | HTTP Status | Meaning | Solution |
|------|-------------|---------|----------|
| `ENTITY_NOT_FOUND` | 404 | Resource doesn't exist | Check UUID/ID |
| `VALIDATION_ERROR` | 400 | Invalid input data | Fix request body |
| `PROCESSING_ERROR` | 500 | Document processing failed | Check logs, retry |
| `INTERNAL_ERROR` | 500 | Server error | Contact support |

### Error Examples

**Library Not Found:**
```json
{
  "codigo": "ENTITY_NOT_FOUND",
  "mensagem": "Biblioteca não encontrada: 550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-10-17T10:30:00",
  "detalhes": null
}
```

**Weight Validation Error:**
```json
{
  "codigo": "VALIDATION_ERROR",
  "mensagem": "Dados inválidos",
  "timestamp": "2025-10-17T10:30:00",
  "detalhes": {
    "pesoSemantico": "A soma dos pesos deve ser igual a 1.0"
  }
}
```

**Association Already Exists:**
```json
{
  "codigo": "INVALID_ARGUMENT",
  "mensagem": "Associação já existe: userId=5, libraryId=10",
  "timestamp": "2025-10-17T10:30:00",
  "detalhes": null
}
```

### Retry Strategy

For processing errors:

```bash
MAX_RETRIES=3
RETRY_DELAY=10

for i in $(seq 1 $MAX_RETRIES); do
  echo "Attempt $i of $MAX_RETRIES"

  STATUS=$(curl -s http://localhost:8080/api/v1/documents/$DOC_ID/process)

  if echo $STATUS | jq -e '.message == "Document processing started"' > /dev/null; then
    echo "✅ Processing started successfully"
    break
  fi

  if [ $i -lt $MAX_RETRIES ]; then
    echo "⚠️ Failed, retrying in ${RETRY_DELAY}s..."
    sleep $RETRY_DELAY
  else
    echo "❌ Failed after $MAX_RETRIES attempts"
    exit 1
  fi
done
```

---

## Additional Resources

- **Swagger UI**: http://localhost:8080/swagger-ui.html (interactive API testing)
- **API Docs**: http://localhost:8080/api-docs (OpenAPI spec)
- **README**: [../README.md](../README.md)
- **Architecture**: [rag_specification.md](rag_specification.md)

---

**Generated with [Claude Code](https://claude.com/claude-code)**
