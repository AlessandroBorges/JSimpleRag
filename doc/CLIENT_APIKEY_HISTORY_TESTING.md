# Client API Key History - Testing Guide

## Overview

This document describes the testing strategy for the Client entity's API key history feature, which stores audit trails of API key changes in JSONB metadata.

## Architecture

### API Key History Storage

When a Client's API key is changed, the system automatically:
1. Creates an `ApiKeyHistory` record with:
   - New API key
   - Previous API key
   - User who made the change (`changedBy`)
   - Timestamp of change
2. Stores this record in the `metadata` field (JSONB in PostgreSQL)
3. Maintains a complete audit trail of all key rotations

### Database Schema

```sql
CREATE TABLE client (
    id SERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    tipo_associacao VARCHAR(50) NOT NULL DEFAULT 'LEITOR',
    api_key VARCHAR(255) NOT NULL UNIQUE,
    api_key_expires_at TIMESTAMP,
    password_hash VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);
```

### Metadata Structure

The `metadata` JSONB field contains:

```json
{
  "apikey_history": [
    {
      "@class": "bor.tools.simplerag.entity.Client$ApiKeyHistory",
      "apiKey": "sk_new_key_abc123",
      "previousApiKey": "sk_old_key_xyz789",
      "createdBy": "admin@example.com",
      "createdAt": "2025-01-15T14:30:00"
    },
    {
      "@class": "bor.tools.simplerag.entity.Client$ApiKeyHistory",
      "apiKey": "sk_newer_key_def456",
      "previousApiKey": "sk_new_key_abc123",
      "createdBy": "security_team@example.com",
      "createdAt": "2025-01-20T10:15:00"
    }
  ]
}
```

## Test Suite

### Test File Location

```
src/test/java/bor/tools/simplerag/entity/ClientJsonSerializationTest.java
```

### Test Cases

#### 1. **testSerializeClientWithEmptyMetadata**
- **Purpose:** Verify basic serialization with no API key history
- **Validates:** JSON structure, field presence, empty metadata handling

#### 2. **testDeserializeClientWithEmptyMetadata**
- **Purpose:** Verify basic deserialization from JSON
- **Validates:** All fields correctly restored, metadata initialization

#### 3. **testSerializeDeserializeClientWithApiKeyHistory**
- **Purpose:** Validate full roundtrip with API key history
- **Validates:**
  - Multiple API key changes stored correctly
  - History list preserved
  - Chronological order maintained

#### 4. **testApiKeyHistoryDetailsPreserved**
- **Purpose:** Verify individual history entry details
- **Validates:**
  - New API key stored
  - Previous API key stored
  - `changedBy` user stored
  - Timestamp stored
  - Data types preserved

#### 5. **testMultipleApiKeyChanges**
- **Purpose:** Simulate real-world key rotation scenarios
- **Validates:**
  - Multiple sequential changes
  - Complete audit trail
  - Chronological order
  - Different users tracked

#### 6. **testApiKeyHistoryAsObject**
- **Purpose:** Test ApiKeyHistory as standalone object
- **Validates:**
  - Jackson serialization/deserialization
  - LocalDateTime handling
  - Object structure integrity

#### 7. **testJsonbRoundtripSimulation**
- **Purpose:** Simulate JSONB database storage cycle
- **Validates:**
  - All data types preserved (UUID, LocalDateTime, Boolean, Enum)
  - Metadata integrity
  - No data loss

## Running the Tests

### Run Single Test Class

```bash
mvn test -Dtest=ClientJsonSerializationTest
```

### Run Specific Test Method

```bash
mvn test -Dtest=ClientJsonSerializationTest#testSerializeDeserializeClientWithApiKeyHistory
```

### Run All Tests

```bash
mvn test
```

### Run with Detailed Output

```bash
mvn test -Dtest=ClientJsonSerializationTest -X
```

## Expected Output

Successful test execution will show:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running bor.tools.simplerag.entity.ClientJsonSerializationTest

Serialized Client (empty metadata):
{"id":1,"uuid":"...","nome":"Test Client",...}

Serialized Client with API key history:
{
  "id" : 1,
  "uuid" : "...",
  "metadata" : {
    "apikey_history" : [ ... ]
  },
  ...
}

API Key History entries: 2

[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

## Integration with Database

### Hibernate Configuration

The `Client` entity uses `@Type` annotation for the `metadata` field:

```java
@Type(JsonType.class)
@Column(columnDefinition = "jsonb")
private Metadata metadata;
```

This ensures:
- Jackson serialization for Java → JSON
- PostgreSQL JSONB storage
- Automatic type conversion

### JPA Repository Operations

When saving/loading Client entities:

```java
// Save client (triggers API key history)
Client client = new Client();
client.setChangedBy("admin@example.com");
client.setApiKey("sk_new_key");
clientRepository.save(client);

// Load client (deserializes metadata)
Client loaded = clientRepository.findById(id).get();
List<ApiKeyHistory> history = loaded.getMetadata().get("apikey_history");
```

## Troubleshooting

### Issue: "Cannot deserialize instance of ApiKeyHistory"

**Cause:** Jackson doesn't know how to deserialize the nested object.

**Solution:** The `@JsonTypeInfo` annotation on `ApiKeyHistory` class provides type information in JSON:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public static class ApiKeyHistory { ... }
```

### Issue: "LocalDateTime not serializing"

**Cause:** Jackson needs JavaTimeModule for Java 8+ date/time types.

**Solution:** Register the module in ObjectMapper:

```java
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.registerModule(new JavaTimeModule());
```

### Issue: "Metadata is null after deserialization"

**Cause:** JSONB field might be NULL in database.

**Solution:** Use default value in entity:

```java
@Builder.Default
private Metadata metadata = new Metadata();
```

## Best Practices

### 1. Always Set `changedBy` Before Changing API Key

```java
// GOOD
client.setChangedBy("security_team@example.com");
client.setApiKey("sk_new_key");

// BAD
client.setApiKey("sk_new_key");  // changedBy will be "system"
```

### 2. Query API Key History

```java
@SuppressWarnings("unchecked")
List<ApiKeyHistory> history =
    (List<ApiKeyHistory>) client.getMetadata().get("apikey_history");

if (history != null) {
    for (ApiKeyHistory entry : history) {
        System.out.println("Key changed by: " + entry.getCreatedBy());
        System.out.println("Changed at: " + entry.getCreatedAt());
    }
}
```

### 3. Audit Queries (Native SQL)

```sql
-- Find all clients who changed API key in last 7 days
SELECT
    c.id,
    c.email,
    jsonb_array_elements(c.metadata->'apikey_history') as history_entry
FROM client c
WHERE c.metadata ? 'apikey_history'
AND (
    SELECT MAX((entry->>'createdAt')::timestamp)
    FROM jsonb_array_elements(c.metadata->'apikey_history') entry
) > NOW() - INTERVAL '7 days';
```

## Performance Considerations

### JSONB Indexing

For frequent history queries, consider adding a GIN index:

```sql
CREATE INDEX idx_client_metadata_apikey_history
ON client USING GIN ((metadata->'apikey_history'));
```

### History Size Management

If history grows large (>100 entries), consider:

1. **Archiving old entries:**
   ```java
   // Keep only last 50 entries
   List<ApiKeyHistory> history = getHistory();
   if (history.size() > 50) {
       history = history.subList(history.size() - 50, history.size());
       metadata.put("apikey_history", history);
   }
   ```

2. **Separate audit table:**
   - Move old entries to dedicated `client_apikey_audit` table
   - Keep only recent entries in metadata

## Security Considerations

### 1. API Key Storage

- **Never log actual API keys** in plain text
- Store only hashes or truncated versions in logs
- Rotate keys regularly

### 2. Audit Trail Integrity

- **Never allow manual editing** of `apikey_history`
- Changes should only come through `setApiKey()` method
- Consider digital signatures for critical applications

### 3. Access Control

```java
// In service layer
@PreAuthorize("hasRole('ADMIN')")
public List<ApiKeyHistory> getApiKeyHistory(Integer clientId) {
    Client client = clientRepository.findById(clientId)
        .orElseThrow(...);
    return (List<ApiKeyHistory>) client.getMetadata().get("apikey_history");
}
```

## Future Enhancements

### Planned Features

1. **Automatic key expiration:**
   - Check `apiKeyExpiresAt` before authentication
   - Auto-rotate keys before expiration

2. **History analytics:**
   - Average key lifetime
   - Frequency of rotations
   - User rotation patterns

3. **Compliance reports:**
   - Export history for audits
   - SOC2/ISO27001 compliance
   - GDPR audit trails

## References

- [Jackson Documentation](https://github.com/FasterXML/jackson-docs)
- [PostgreSQL JSONB Documentation](https://www.postgresql.org/docs/current/datatype-json.html)
- [Spring Data JPA Documentation](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hypersistence Utils JSONB](https://github.com/vladmihalcea/hypersistence-utils)

## Support

For issues or questions:
- Check test output for detailed error messages
- Review database logs for JSONB errors
- Verify Jackson configuration
- Contact the development team
