# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JSimpleRag is a hierarchical RAG (Retrieval-Augmented Generation) system built in Java with PostgreSQL/PGVector. The system enables intelligent document search and retrieval through a combination of semantic (embedding-based) and textual (full-text) search capabilities.

## Architecture

### Core Hierarchy
The system implements a 4-level hierarchical structure:

```
Biblioteca (Knowledge Area/Library)
├── Documento (Document - books, articles, manuals)
│   ├── Capítulo (Chapter - ~8k tokens)
│   │   └── DocEmbedding (Chunks - ~2k tokens)
│   └── DocEmbedding (Chapter-level embeddings)
└── DocEmbedding (Document-level embeddings)
```

### Key Components
- **Hybrid Search Engine**: Combines semantic similarity (PGVector) with PostgreSQL full-text search
- **Asynchronous Processing**: Background document processing and embedding generation
- **LLM Integration**: Uses `JSimpleLLM` library from `bor.tools` group for embeddings and completions
- **Weighted Search**: Configurable semantic vs textual search weights per library

### Technology Stack
- **Backend**: Java 17 + Spring Boot 3.x
- **Database**: PostgreSQL 18+ with PGVector extension
- **Dependencies**: Maven-based with `JSimpleLLM` as core LLM service
- **APIs**: OpenAPI/Swagger documented REST endpoints

## Development Commands

### Build and Test

```bash
# Build project
./mvnw clean compile

# Run tests
./mvnw test

# Run integration tests
./mvnw test -P integration-tests

# Run all tests with verification
./mvnw verify

# Run specific test
./mvnw test -Dtest=BibliotecaServiceTest

# Run with specific test method
./mvnw test -Dtest=BibliotecaServiceTest#deveCriarBiblioteca
```

### Application Execution

```bash
# Run application
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Package application
./mvnw package

# Run packaged jar
java -jar target/simplerag-0.0.1-SNAPSHOT.jar
```

### Database Operations

```bash
# Start PostgreSQL with PGVector (Docker)
docker-compose up -d postgres

# Access database
docker exec -it jsimplerag_postgres_1 psql -U rag_user -d rag_db

# Run Liquibase migrations
./mvnw liquibase:update
```

## Key Architectural Patterns

### Entity Relationships
- `Biblioteca` → `Documento` (1:N) - Libraries contain multiple documents
- `Documento` → `Capitulo` (1:N) - Documents are split into chapters
- `DocEmbedding` - Links to Biblioteca, Documento, and optionally Capitulo
- Version control via `flag_vigente` on documents

### Search Strategy
1. **Query Processing**: Generate query-optimized embeddings using `Emb_Operation.QUERY`
2. **Hybrid Search**: Combine semantic similarity (`<=>` operator) with textual ranking (`ts_rank_cd`)
3. **Weighted Results**: Apply configurable weights (semantic + textual = 1.0)
4. **Hierarchical Navigation**: Results maintain library → document → chapter → chunk context

### LLM Service Integration
The system integrates with `JSimpleLLM` for:
- **Embedding Generation**: Specialized operations (DOCUMENT, QUERY, CLUSTERING)
- **Text Completion**: Answer generation and content summarization
- **Token Management**: Cost optimization and context window management

## Database Schema Essentials

### Core Tables
- `biblioteca`: Knowledge area containers with search weight configuration
- `documento`: Markdown content with versioning (`flag_vigente`)
- `capitulo`: Document chapters with ordering (`ordem_doc`)
- `doc_embedding`: Vector embeddings with full-text search integration

### Critical Indexes
- `idx_embedding_vector`: IVFFlat index for vector similarity search
- `idx_embedding_texto`: GIN index for full-text search
- `idx_documento_vigente`: Partial index for active documents only

## API Structure

### Main Endpoints
- `/api/v1/bibliotecas` - Library management
- `/api/v1/documentos` - Document CRUD and processing
- `/api/v1/pesquisa` - Hybrid search operations
- `/api/v1/embeddings` - Direct embedding management

### Processing Flow
1. **Document Upload**: POST to `/api/v1/documentos`
2. **Async Processing**: POST to `/api/v1/documentos/{id}/processar`
3. **Search**: POST to `/api/v1/pesquisa` with query and parameters

## Configuration Requirements

### Required Environment Variables
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` - Database connection
- `OPENAI_API_KEY` - LLM service authentication (or equivalent for chosen provider)

### Key Application Properties
- `rag.embedding.model` - Embedding model selection
- `rag.embedding.dimensoes` - Vector dimensions (default: 1536)
- `rag.processamento.chunk-size-maximo` - Maximum chunk size for embeddings
- `spring.liquibase.change-log` - Database migration management

## Important Constraints

### Business Rules
- Only one document per title can be `flag_vigente=true` in a library
- Semantic + textual weights must sum to 1.0 for each library
- Documents require minimum 100 characters for processing
- Maximum document size: 50MB

### Performance Considerations
- Embedding generation is asynchronous to prevent UI blocking
- Search queries timeout at 2 seconds
- Support for 10M+ documents with proper indexing
- Target: 100+ queries/second throughput