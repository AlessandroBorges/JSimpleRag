# LLM Configuration Unification - State Backup

**Date**: 2025-10-27
**Purpose**: Complete backup of current LLM configuration state before unification refactoring
**Target**: Merge `LLMServiceConfig` and `MultiLLMServiceConfig` into unified `LLMConfiguration`

---

## Executive Summary

### Current Issues

1. **Critical Bean Overlap**: Both classes create `LLMService` beans, but only `primaryLLMService` is used (@Primary)
2. **Code Duplication**: `parseProviderName()` method duplicated (~45 lines)
3. **Legacy Injections**: 6 classes still inject `LLMService` directly instead of `LLMServiceManager`
4. **Missing Features**: `use_defaults` flag only in `LLMServiceConfig`, not in `MultiLLMServiceConfig`
5. **Dependency Chain**: `MultiLLMServiceConfig` depends on and reuses `LLMServiceConfig` methods

### Solution Approach

Unified `LLMConfiguration` class that:
- Consolidates all LLM provider configuration
- Supports multi-provider with primary/secondary setup
- Includes utility methods as static helpers
- Provides single entry point for all LLM service management

---

## 1. Current Configuration Classes

### 1.1 LLMServiceConfig.java

**Location**: `src/main/java/bor/tools/simplerag/config/LLMServiceConfig.java`
**Lines**: 260 lines
**Purpose**: Base configuration for single LLM provider with granular settings

#### Key Features

- **Unique Feature**: `use_defaults` flag (line 35)
  - `false`: Override default config with user settings
  - `true`: Use default config from `LLMServiceFactory.getDefaultLLMConfig(provider)` and apply changes

#### Configuration Properties

```properties
# Provider Selection
llmservice.provider.name=LM_STUDIO (default)
llmservice.provider.use_defaults=false (default)

# Models
llmservice.provider.llm.models=qwen/qwen3-1.7b (default)
llmservice.provider.embedding.model=text-embedding-snowflake-arctic-embed-l-v2.0 (default)
llmservice.provider.embedding.dimension=768 (default)
llmservice.provider.embedding.embeddingContextLength=2048 (default)

# API Configuration
llmservice.provider.api.url=#{null} (optional)
llmservice.provider.api.key=#{null} (optional)
```

#### Beans Created

1. **`llmService()`** (line 63) - @Bean (NOT @Primary)
   - **Status**: UNUSED - shadowed by `primaryLLMService` from `MultiLLMServiceConfig`
   - **Issue**: Creates service but never injected due to @Primary on other bean

2. **`llmServiceProperties()`** (line 173) - @Bean
   - Returns `LLMServiceProperties` POJO
   - Used for configuration access in other components

#### Utility Methods (Static, Reused by MultiLLMServiceConfig)

1. **`parseLLMModelsToArray(String llmModels, LLMConfig config)`** (line 216)
   - Parses comma-separated model names
   - Creates `Model` objects with default context length (8192)
   - Adds models to provided `LLMConfig`

2. **`parseEmbeddingModelsToArray(String embeddingModel, int embeddingContextLength, LLMConfig config)`** (line 243)
   - Parses comma-separated embedding model names
   - Creates `ModelEmbedding` objects with provided context length
   - Adds embeddings to provided `LLMConfig`

#### Private Helper Methods

1. **`parseProviderName(String name)`** (line 122)
   - **DUPLICATED** in `MultiLLMServiceConfig` (line 291)
   - Converts string to `SERVICE_PROVIDER` enum
   - Handles normalization and aliases (LMSTUDIO, LM_STUDIO, OPENAI, GPT, OLLAMA, ANTHROPIC, CLAUDE)

#### Supported Providers

- LM_STUDIO (default)
- OPENAI
- OLLAMA
- ANTHROPIC
- GEMINI (commented out)
- COHERE (commented out)
- HUGGINGFACE (commented out)

---

### 1.2 MultiLLMServiceConfig.java

**Location**: `src/main/java/bor/tools/simplerag/config/MultiLLMServiceConfig.java`
**Lines**: 422 lines
**Purpose**: Configuration for multiple LLM providers with fallback strategies

#### Configuration Properties

**Primary Provider** (same as LLMServiceConfig):
```properties
llmservice.provider.name=LM_STUDIO (default)
llmservice.provider.llm.models=qwen/qwen3-1.7b (default)
llmservice.provider.embedding.model=text-embedding-nomic-embed-text-v1.5@q8_0 (default)
llmservice.provider.embedding.dimension=768 (default)
llmservice.provider.api.url=#{null} (optional)
llmservice.provider.api.key=#{null} (optional)
```

**Secondary Provider** (optional):
```properties
llmservice.provider2.enabled=false (default)
llmservice.provider2.name=#{null}
llmservice.provider2.llm.models=#{null}
llmservice.provider2.embedding.model=#{null}
llmservice.provider2.embedding.dimension=#{null}
llmservice.provider2.api.url=#{null}
llmservice.provider2.api.key=#{null}
```

**Strategy Configuration**:
```properties
llmservice.strategy=FAILOVER (default)
llmservice.failover.max-retries=3 (default)
llmservice.failover.timeout-seconds=30 (default)
```

#### Beans Created

1. **`primaryLLMService()`** (line 125) - @Bean @Primary
   - **Status**: ACTIVE - This is the main LLMService bean injected everywhere
   - Creates primary provider service
   - Uses `createLLMService()` helper method

2. **`secondaryLLMService()`** (line 155) - @Bean @ConditionalOnProperty
   - **Status**: CONDITIONAL - Only created if `llmservice.provider2.enabled=true`
   - Creates secondary/backup provider service
   - Uses `createLLMService()` helper method

3. **`llmServiceManager()`** (line 190) - @Bean
   - **Status**: RECOMMENDED - This is the recommended bean for injection
   - Manages multiple providers with strategy
   - Handles failover, round-robin, etc.

4. **`primaryLLMProperties()`** (line 228) - @Bean
   - Returns `LLMProviderProperties` for primary provider
   - Used for configuration info access

5. **`secondaryLLMProperties()`** (line 244) - @Bean @ConditionalOnProperty
   - Returns `LLMProviderProperties` for secondary provider
   - Only created if secondary is enabled

#### Helper Methods

1. **`createLLMService(String providerName, String embeddingModel, String apiUrl, String apiKey)`** (line 261)
   - Creates `LLMService` instance
   - Calls `parseProviderName()` to get enum
   - **Reuses** `LLMServiceConfig.parseEmbeddingModelsToArray()` (line 271)
   - Stores service in `listLLMService` map

2. **`parseProviderName(String name)`** (line 291)
   - **DUPLICATED CODE** from `LLMServiceConfig` (~45 lines)
   - Identical logic for provider name parsing

3. **`parseStrategy(String name)`** (line 340)
   - Converts strategy name to `LLMServiceStrategy` enum
   - Supports: FAILOVER, ROUND_ROBIN, PRIMARY_ONLY, MODEL_BASED

4. **`getActiveProviderMap()`** (line 284)
   - Returns unmodifiable map of active providers
   - Used by `LLMInfoController` to list providers

#### Dependencies

**Constructor Injection** (line 115):
```java
private final LLMServiceConfig LLMServiceConfig;

MultiLLMServiceConfig(LLMServiceConfig LLMServiceConfig) {
    this.LLMServiceConfig = LLMServiceConfig;
}
```

**Uses from LLMServiceConfig**:
- Line 271: `LLMServiceConfig.parseEmbeddingModelsToArray(embeddingModel, 0, config);`

#### Supported Strategies

- **FAILOVER** (default): Try secondary on primary failure
- **ROUND_ROBIN**: Alternate between providers
- **PRIMARY_ONLY**: Use only primary provider
- **MODEL_BASED**: Route by model name

---

## 2. Usage Mapping

### 2.1 Files Referencing LLMServiceConfig

**Total**: 4 files

1. **LLMServiceConfig.java** (self-reference)
2. **MultiLLMServiceConfig.java** - Constructor injection + method reuse
3. **LLMInfoController.java** - No direct usage (imports commented)
4. **LLMServiceConfigTest.java** - Test class

### 2.2 Files Referencing MultiLLMServiceConfig

**Total**: 3 files

1. **MultiLLMServiceConfig.java** (self-reference)
2. **LLMInfoController.java** - Injected for `getActiveProviderMap()`
3. **LLMServiceConfigTest.java** - Test class

### 2.3 Files with Direct LLMService Injection (Legacy)

**Total**: 20 files (6 need migration)

#### Modern Usage (Already using LLMServiceManager) ✅

1. **ChapterEmbeddingStrategy.java** - Uses `LLMServiceManager`
2. **QAEmbeddingStrategy.java** - Uses `LLMServiceManager`
3. **QueryEmbeddingStrategy.java** - Uses `LLMServiceManager`
4. **SummaryEmbeddingStrategy.java** - Uses `LLMServiceManager`
5. **EmbeddingServiceImpl.java** - Uses `LLMServiceManager`
6. **RAGUtil.java** - Uses `LLMServiceManager`

#### Legacy Usage (Need Migration) ⚠️

**Priority 1 - Controllers**:
1. **LLMInfoController.java** (line 46)
   ```java
   private final LLMService llmService;
   private final MultiLLMServiceConfig multiLlmServiceConfig;
   ```
   - **Usage**: Lines 73, 112, 115, 153, 154, 200, 201, 210
   - **Migration**: Replace with `LLMServiceManager`
   - **Methods affected**: `getProviders()`, `getConfigurations()`, `getInstalledModels()`, `getRegisteredModels()`

**Priority 2 - Document Processing**:
2. **SplitterFactory.java** (line 27)
   ```java
   private final LLMService llmService;
   ```
   - **Usage**: Constructor injection, lines 121, 130, 139, 154, 185, 186, 198, 225
   - **Migration**: Replace with `LLMServiceManager`

3. **DocumentRouter.java** (line 29)
   ```java
   private final LLMService llmService;
   ```
   - **Usage**: Constructor injection (line 34), lines 133, 228, 230, 241, 298
   - **Migration**: Replace with `LLMServiceManager`

4. **AbstractSplitter.java** (line unknown - need to read)
   - **Migration**: Replace with `LLMServiceManager`

5. **DocumentSummarizerImpl.java** (line unknown - need to read)
   - **Migration**: Replace with `LLMServiceManager`

**Deprecated (Already marked)**:
6. **EmbeddingProcessorImpl.java** - Marked as deprecated, uses old interface

**Test Files** (Low priority):
- **LLMServiceManagerTest.java** - Test usage
- **LLMServiceConfigTest.java** - Test usage
- **QueryEmbeddingStrategyTest.java** - Test usage

**Other Files** (Need review):
- **SplitterWiki.java** - Check if uses `LLMService`
- **SplitterGenerico.java** - Check if uses `LLMService`

---

## 3. Configuration Property Mapping

### 3.1 Overlap in Properties

| Property | LLMServiceConfig | MultiLLMServiceConfig | Notes |
|----------|------------------|----------------------|-------|
| `llmservice.provider.name` | ✅ (line 32) | ✅ (line 63) | **Overlap** - Used by both |
| `llmservice.provider.llm.models` | ✅ (line 38) | ✅ (line 66) | **Overlap** - Different defaults |
| `llmservice.provider.embedding.model` | ✅ (line 41) | ✅ (line 69) | **Overlap** - Different defaults |
| `llmservice.provider.embedding.dimension` | ✅ (line 44) | ✅ (line 72) | **Overlap** - Same default (768) |
| `llmservice.provider.api.url` | ✅ (line 50) | ✅ (line 75) | **Overlap** - Both optional |
| `llmservice.provider.api.key` | ✅ (line 53) | ✅ (line 78) | **Overlap** - Both optional |

### 3.2 Unique to LLMServiceConfig

| Property | Line | Default | Purpose |
|----------|------|---------|---------|
| `llmservice.provider.use_defaults` | 35 | `false` | Use default config from factory |
| `llmservice.provider.embedding.embeddingContextLength` | 47 | `2048` | Context length for embeddings |

### 3.3 Unique to MultiLLMServiceConfig

**Secondary Provider**:
| Property | Line | Default | Purpose |
|----------|------|---------|---------|
| `llmservice.provider2.enabled` | 83 | `false` | Enable secondary provider |
| `llmservice.provider2.name` | 86 | `null` | Secondary provider name |
| `llmservice.provider2.llm.models` | 89 | `null` | Secondary LLM models |
| `llmservice.provider2.embedding.model` | 92 | `null` | Secondary embedding model |
| `llmservice.provider2.embedding.dimension` | 95 | `null` | Secondary embedding dimension |
| `llmservice.provider2.api.url` | 98 | `null` | Secondary API URL |
| `llmservice.provider2.api.key` | 101 | `null` | Secondary API key |

**Strategy Configuration**:
| Property | Line | Default | Purpose |
|----------|------|---------|---------|
| `llmservice.strategy` | 106 | `FAILOVER` | Multi-provider strategy |
| `llmservice.failover.max-retries` | 109 | `3` | Max retry attempts |
| `llmservice.failover.timeout-seconds` | 112 | `30` | Timeout for operations |

---

## 4. Bean Dependency Graph

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Container                          │
└─────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┼─────────────────┐
            │                 │                 │
            ▼                 ▼                 ▼
    ┌──────────────┐  ┌─────────────────┐  ┌──────────────┐
    │LLMServiceConfig│ │MultiLLMServiceConfig│ │ Application  │
    │              │  │                 │  │   Beans      │
    └──────────────┘  └─────────────────┘  └──────────────┘
            │                 │                    │
            │                 │                    │
    Creates │                 │ Creates            │ Injects
            ▼                 ▼                    ▼
    ┌──────────────┐  ┌──────────────────┐  ┌──────────────┐
    │ llmService() │  │primaryLLMService │  │  Services    │
    │   (UNUSED)   │  │    (@Primary)    │  │ Controllers  │
    └──────────────┘  └──────────────────┘  └──────────────┘
            ╳                 │
    Not Injected      Injected Everywhere
    (Shadowed)                │
                              ├─────────────────────┐
                              ▼                     ▼
                    ┌──────────────────┐  ┌──────────────────┐
                    │secondaryLLMService│  │llmServiceManager │
                    │  (@Conditional)  │  │  (Recommended)   │
                    └──────────────────┘  └──────────────────┘
                              │                     ▲
                              └─────────────────────┘
                                  Managed by Manager
```

### Key Issues

1. **Unused Bean**: `llmService()` from `LLMServiceConfig` is never injected
2. **@Primary Shadows**: `primaryLLMService` shadows the unused bean
3. **Legacy Injections**: 6 classes inject `LLMService` instead of `LLMServiceManager`
4. **Mixed Usage**: Some classes use `LLMService`, others use `LLMServiceManager`

---

## 5. Code Duplication Analysis

### 5.1 parseProviderName() Method

**Duplication**: ~45 lines, identical logic

**Location 1**: `LLMServiceConfig.java:122-166`
**Location 2**: `MultiLLMServiceConfig.java:291-335`

**Code**:
```java
private SERVICE_PROVIDER parseProviderName(String name) {
    if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("Provider name cannot be null or empty");
    }

    String normalized = name.trim().toUpperCase()
        .replace("-", "_")
        .replace(" ", "_");
    normalized = normalized.replace("LLMSERVICE", "")
        .replace(".java", "")
        .replace("java", "");

    try {
        // Try to parse as enum value directly
        return SERVICE_PROVIDER.valueOf(normalized);
    } catch (IllegalArgumentException e) {
        // Handle common aliases
        switch (normalized) {
            case "LMSTUDIO":
            case "LM_STUDIO":
                return SERVICE_PROVIDER.LM_STUDIO;
            case "OPENAI":
            case "GPT":
                return SERVICE_PROVIDER.OPENAI;
            case "OLLAMA":
                return SERVICE_PROVIDER.OLLAMA;
            case "ANTHROPIC":
            case "CLAUDE":
                return SERVICE_PROVIDER.ANTHROPIC;
            default:
                throw new IllegalArgumentException("Unknown LLM provider: " + name);
        }
    }
}
```

**Solution**: Extract to utility class `LLMProviderParser`

---

## 6. Integration Points

### 6.1 LLMServiceManager Integration

**File**: `src/main/java/bor/tools/simplerag/service/llm/LLMServiceManager.java`

**Purpose**: Central manager for multiple LLM services with strategy support

**Key Features**:
- Manages multiple `LLMService` instances
- Implements fallback strategies (FAILOVER, ROUND_ROBIN, etc.)
- Provides unified interface for service selection
- Handles retries and timeouts

**Current State**:
- Created by `MultiLLMServiceConfig.llmServiceManager()` (line 190)
- Used by modern embedding strategies
- Should be the primary injection point

### 6.2 Embedding Service Architecture

**Modern Architecture** (Already migrated):
```
EmbeddingServiceImpl
    ├─> LLMServiceManager (Strategy-based selection)
    │   ├─> primaryLLMService
    │   └─> secondaryLLMService (optional)
    │
    └─> Strategies
        ├─> ChapterEmbeddingStrategy
        ├─> QAEmbeddingStrategy
        ├─> QueryEmbeddingStrategy
        └─> SummaryEmbeddingStrategy
```

**Legacy Architecture** (Needs migration):
```
SplitterFactory
    ├─> LLMService (direct injection)
    ├─> DocumentRouter
    │   └─> LLMService (direct injection)
    │
    └─> Splitters
        ├─> AbstractSplitter
        ├─> SplitterGenerico
        ├─> SplitterNorma
        └─> SplitterWiki
```

---

## 7. Application Properties Files

### 7.1 Main Properties

**File**: `src/main/resources/application.properties`

Expected properties (need to verify in actual file):
```properties
# Primary LLM Provider
llmservice.provider.name=LM_STUDIO
llmservice.provider.use_defaults=false
llmservice.provider.llm.models=qwen/qwen3-1.7b
llmservice.provider.embedding.model=text-embedding-nomic-embed-text-v1.5@q8_0
llmservice.provider.embedding.dimension=768
llmservice.provider.embedding.embeddingContextLength=2048

# Secondary LLM Provider (optional)
llmservice.provider2.enabled=false
# llmservice.provider2.name=OPENAI
# llmservice.provider2.embedding.model=text-embedding-ada-002
# llmservice.provider2.embedding.dimension=1536

# Strategy
llmservice.strategy=FAILOVER
llmservice.failover.max-retries=3
llmservice.failover.timeout-seconds=30
```

### 7.2 Test Properties

**File**: `src/test/resources/application-test.properties`

Expected to have test-specific LLM configurations

---

## 8. Migration Impact Assessment

### 8.1 High Impact Changes

1. **Remove Unused Bean** - `LLMServiceConfig.llmService()`
   - Impact: Low - Bean is never injected
   - Risk: None

2. **Migrate LLMInfoController** - Replace direct `LLMService` injection
   - Impact: Medium - Public API endpoints affected
   - Risk: Medium - API responses might change structure
   - Endpoints affected:
     - `/api/v1/llm/providers`
     - `/api/v1/llm/configurations`
     - `/api/v1/llm/models/installed`
     - `/api/v1/llm/models/registered`

3. **Migrate Splitter Classes** - Replace `LLMService` with `LLMServiceManager`
   - Impact: High - Document processing pipeline affected
   - Risk: Medium - Need to test all splitter types
   - Classes affected: `SplitterFactory`, `DocumentRouter`, `AbstractSplitter`, splitter implementations

### 8.2 Medium Impact Changes

1. **Extract Utility Methods** - Create `LLMProviderParser` utility
   - Impact: Low - Internal refactoring
   - Risk: Low - Static methods, no state

2. **Consolidate Properties** - Merge property handling
   - Impact: Medium - Configuration parsing changes
   - Risk: Low - Backward compatible if done correctly

### 8.3 Low Impact Changes

1. **Update Tests** - Adapt test classes
   - Impact: Low - Test-only changes
   - Risk: Low

2. **Documentation** - Update API docs and README
   - Impact: Low - Documentation only
   - Risk: None

---

## 9. Test Coverage Analysis

### 9.1 Existing Test Files

1. **LLMServiceConfigTest.java**
   - Tests `LLMServiceConfig` bean creation
   - Tests provider parsing
   - Tests model parsing utilities

2. **LLMServiceManagerTest.java**
   - Tests `LLMServiceManager` with multiple providers
   - Tests fallback strategies
   - Tests retry logic

### 9.2 Required New Tests

1. **LLMConfigurationTest.java** (new unified config)
   - Test primary provider creation
   - Test secondary provider creation (conditional)
   - Test `LLMServiceManager` creation with strategy
   - Test property parsing and validation
   - Test backward compatibility with old properties

2. **LLMProviderParserTest.java** (new utility)
   - Test provider name normalization
   - Test alias handling
   - Test error cases

3. **Integration Tests**
   - Test full configuration loading
   - Test multi-provider scenarios
   - Test strategy switching

---

## 10. Backward Compatibility Requirements

### 10.1 Property Compatibility

**MUST SUPPORT** existing property names:
```properties
llmservice.provider.name
llmservice.provider.llm.models
llmservice.provider.embedding.model
llmservice.provider.embedding.dimension
llmservice.provider.api.url
llmservice.provider.api.key
llmservice.provider2.enabled
llmservice.provider2.name
# ... all provider2 properties
llmservice.strategy
llmservice.failover.max-retries
llmservice.failover.timeout-seconds
```

### 10.2 Bean Name Compatibility

**MUST PROVIDE** same bean names for existing injections:
- `primaryLLMService` (@Primary)
- `secondaryLLMService` (@Conditional)
- `llmServiceManager`
- `primaryLLMProperties`
- `secondaryLLMProperties`

### 10.3 API Compatibility

**MUST MAINTAIN** public API contracts:
- `LLMInfoController` endpoints must continue working
- Response DTOs must remain unchanged
- Error handling must be consistent

---

## 11. Success Criteria

### 11.1 Functional Requirements

- ✅ All existing configuration properties work unchanged
- ✅ Primary and secondary provider setup works
- ✅ All LLM service strategies function correctly
- ✅ Legacy direct `LLMService` injections migrated to `LLMServiceManager`
- ✅ No unused beans in Spring container
- ✅ No code duplication between configuration classes

### 11.2 Non-Functional Requirements

- ✅ All existing tests pass
- ✅ New tests cover unified configuration
- ✅ Build completes successfully
- ✅ Application starts without errors
- ✅ Documentation updated

### 11.3 Quality Requirements

- ✅ Code follows existing project patterns
- ✅ Logging consistent with existing code
- ✅ Error messages clear and actionable
- ✅ Comments and JavaDoc updated

---

## 12. Risk Assessment

### 12.1 High Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Breaking document processing pipeline | High | Medium | Comprehensive testing of all splitter types |
| API endpoint behavior changes | High | Low | Maintain exact response DTOs, add integration tests |
| Configuration loading failures | High | Low | Thorough property validation, fallback to defaults |

### 12.2 Medium Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Test failures during migration | Medium | Medium | Fix tests incrementally, maintain coverage |
| Performance degradation | Medium | Low | Profile before/after, benchmark critical paths |
| Spring bean injection issues | Medium | Medium | Validate bean names, use @Qualifier where needed |

### 12.3 Low Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Documentation outdated | Low | High | Update docs in same PR as code changes |
| Logging inconsistency | Low | Medium | Follow existing log patterns |
| Property name confusion | Low | Low | Clear migration guide, deprecation warnings |

---

## 13. Rollback Plan

### 13.1 Git Safety

1. Create feature branch: `feature/unify-llm-configuration`
2. Commit after each phase with descriptive messages
3. Tag before major changes: `before-config-unification`
4. Maintain ability to revert individual commits

### 13.2 Rollback Steps

If critical issues arise:

1. **Immediate**: Revert last commit
   ```bash
   git revert HEAD
   ```

2. **Full rollback**: Reset to tagged state
   ```bash
   git reset --hard before-config-unification
   ```

3. **Partial rollback**: Cherry-pick working commits
   ```bash
   git checkout main
   git cherry-pick <working-commit-sha>
   ```

### 13.3 Emergency Hotfix

If production issue discovered after merge:

1. Create hotfix branch from main
2. Restore old configuration classes
3. Remove new unified configuration
4. Deploy hotfix immediately
5. Fix issues in feature branch
6. Re-merge when stable

---

## 14. Implementation Phases (Reference)

This backup documents the state BEFORE implementing these phases:

### Phase 1: Análise e Preparação ✅ (This document)
- Create backup documentation (THIS FILE)
- Document all current usages
- Map configuration properties
- Identify migration targets

### Phase 2: Criar classe utilitária LLMProviderParser
- Extract `parseProviderName()` to utility
- Add comprehensive tests
- Update both config classes to use utility

### Phase 3: Criar nova classe LLMConfiguration unificada
- Create new `LLMConfiguration.java`
- Consolidate all configuration properties
- Implement bean creation methods
- Add validation and error handling

### Phase 4: Migrar lógica de MultiLLMServiceConfig
- Move multi-provider logic to unified config
- Move strategy configuration
- Move helper methods
- Preserve all functionality

### Phase 5: Atualizar LLMInfoController
- Replace direct `LLMService` injection with `LLMServiceManager`
- Update methods to use manager API
- Test all endpoints
- Verify response DTOs unchanged

### Phase 6: Migrar classes legado
- `SplitterFactory`: Replace `LLMService` with `LLMServiceManager`
- `DocumentRouter`: Replace `LLMService` with `LLMServiceManager`
- `AbstractSplitter`: Update API to accept manager
- `DocumentSummarizerImpl`: Migrate to manager
- Test document processing pipeline

### Phase 7: Deprecar e remover classes antigas
- Add `@Deprecated` to `LLMServiceConfig`
- Add `@Deprecated` to `MultiLLMServiceConfig`
- Update documentation with deprecation notice
- Plan removal for next major version

### Phase 8: Atualizar testes e validar build
- Update existing tests
- Add new tests for unified config
- Run full test suite
- Validate build success
- Test application startup
- Verify all endpoints functional

---

## 15. Key Files for Reference

### Configuration Classes
- `src/main/java/bor/tools/simplerag/config/LLMServiceConfig.java`
- `src/main/java/bor/tools/simplerag/config/MultiLLMServiceConfig.java`

### Service Manager
- `src/main/java/bor/tools/simplerag/service/llm/LLMServiceManager.java`
- `src/main/java/bor/tools/simplerag/service/llm/LLMServiceStrategy.java`

### Controllers (Need Migration)
- `src/main/java/bor/tools/simplerag/controller/LLMInfoController.java`

### Splitters (Need Migration)
- `src/main/java/bor/tools/splitter/SplitterFactory.java`
- `src/main/java/bor/tools/splitter/DocumentRouter.java`
- `src/main/java/bor/tools/splitter/AbstractSplitter.java`
- `src/main/java/bor/tools/splitter/DocumentSummarizerImpl.java`

### Tests
- `src/test/java/bor/tools/simplerag/config/LLMServiceConfigTest.java`
- `src/test/java/bor/tools/simplerag/service/llm/LLMServiceManagerTest.java`

### Properties
- `src/main/resources/application.properties`
- `src/test/resources/application-test.properties`
- `.env.example`

---

## 16. Additional Notes

### Dependencies on External Library

Both configuration classes depend on `JSimpleLLM` library (`bor.tools` group):
- `LLMConfig`
- `LLMService`
- `LLMServiceFactory`
- `SERVICE_PROVIDER`
- `Model`
- `ModelEmbedding`
- `Model_Type`

**Important**: Do not modify external library interfaces. Adapter pattern may be needed if API changes required.

### Spring Boot Version

- Spring Boot 3.x
- Uses modern dependency injection patterns
- Leverages `@ConditionalOnProperty` for optional beans
- Uses `@Primary` for default bean selection

### Logging Strategy

All classes use SLF4J with Lombok's `@Slf4j`:
```java
@Slf4j
public class ConfigClass {
    log.info("Message");
    log.debug("Debug message");
    log.error("Error message", exception);
}
```

---

## 17. Current State Snapshot

**Snapshot Date**: 2025-10-27
**Git Branch**: main
**Last Commit**: 3c1fa52 "Refactor dos DTOs de Documento"

### Build Status
- ✅ Compilation: SUCCESS
- ✅ Tests: Passing (expected)
- ✅ Application Startup: Working

### Known Issues
1. Unused `llmService()` bean in `LLMServiceConfig`
2. Duplicated `parseProviderName()` method
3. 6 classes using legacy `LLMService` direct injection
4. `MultiLLMServiceConfig` depends on `LLMServiceConfig`

### Configuration Currently in Use
Based on git status, both classes are active:
- `LLMServiceConfig` - Creates unused bean
- `MultiLLMServiceConfig` - Creates active beans with @Primary

---

**END OF BACKUP DOCUMENTATION**

*This document serves as complete reference for the current state before unification refactoring begins.*
