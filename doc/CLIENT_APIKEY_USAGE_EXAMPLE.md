# Client API Key Usage Examples

## Overview

This document provides practical examples of using the Client API key management and history features.

## Basic Usage

### 1. Creating a New Client

```java
// In ClientService or Controller
ClientDTO clientDTO = ClientDTO.builder()
    .nome("Production App")
    .email("prod-app@company.com")
    .passwordHash("mySecurePassword123")  // Will be hashed automatically
    .tipoAssociacao(TipoAssociacao.COLABORADOR)
    .ativo(true)
    .build();

ClientDTO saved = clientService.save(clientDTO);

// API key is auto-generated
System.out.println("API Key: " + saved.getApiKey());
// Output: API Key: sk_a1b2c3d4e5f6...
```

### 2. Rotating API Key

```java
// Get existing client
ClientDTO client = clientService.findById(clientId)
    .orElseThrow(() -> new IllegalArgumentException("Client not found"));

// Regenerate API key (automatically creates history entry)
String newApiKey = clientService.regenerateApiKey(clientId);

System.out.println("New API Key: " + newApiKey);
System.out.println("Please update your application configuration with the new key");
```

### 3. Setting API Key Expiration

```java
// Set key to expire in 90 days
LocalDateTime expiresAt = LocalDateTime.now().plusDays(90);
clientService.setApiKeyExpiration(clientId, expiresAt);

System.out.println("API key will expire at: " + expiresAt);
```

### 4. Manual API Key Change (with History)

```java
// Load client entity
Client client = clientRepository.findById(clientId)
    .orElseThrow(() -> new IllegalArgumentException("Client not found"));

// Set who is changing the key
client.setChangedBy("security_admin@company.com");

// Change the API key (triggers automatic history storage)
client.setApiKey("sk_manually_set_key_xyz");

// Save to database
clientRepository.save(client);
```

## Advanced Usage

### 5. Retrieving API Key History

```java
// Load client
Client client = clientRepository.findById(clientId)
    .orElseThrow(() -> new IllegalArgumentException("Client not found"));

// Get history from metadata
@SuppressWarnings("unchecked")
List<Object> historyList = (List<Object>) client.getMetadata().get("apikey_history");

if (historyList != null && !historyList.isEmpty()) {
    System.out.println("API Key Change History:");
    System.out.println("Total changes: " + historyList.size());

    for (int i = 0; i < historyList.size(); i++) {
        @SuppressWarnings("unchecked")
        var entry = (java.util.Map<String, Object>) historyList.get(i);

        System.out.println("\nChange #" + (i + 1));
        System.out.println("  New Key: " + maskApiKey((String) entry.get("apiKey")));
        System.out.println("  Previous Key: " + maskApiKey((String) entry.get("previousApiKey")));
        System.out.println("  Changed By: " + entry.get("createdBy"));
        System.out.println("  Changed At: " + entry.get("createdAt"));
    }
}

// Helper method to mask API keys for display
private String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.length() < 10) return "***";
    return apiKey.substring(0, 7) + "..." + apiKey.substring(apiKey.length() - 4);
}
```

Output:
```
API Key Change History:
Total changes: 3

Change #1
  New Key: sk_a1b2...xyz9
  Previous Key: sk_old1...abc3
  Changed By: admin@company.com
  Changed At: 2025-01-15T10:30:00

Change #2
  New Key: sk_c3d4...def6
  Previous Key: sk_a1b2...xyz9
  Changed By: security_team@company.com
  Changed At: 2025-01-20T14:15:00

Change #3
  New Key: sk_e5f6...ghi9
  Previous Key: sk_c3d4...def6
  Changed By: ops_team@company.com
  Changed At: 2025-01-25T09:45:00
```

### 6. Scheduled Key Rotation

```java
@Service
public class ApiKeyRotationService {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientService clientService;

    /**
     * Scheduled task to rotate API keys before expiration
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void rotateExpiringKeys() {
        log.info("Starting scheduled API key rotation check");

        // Find keys expiring in next 7 days
        LocalDateTime threshold = LocalDateTime.now().plusDays(7);

        List<Client> clients = clientRepository.findAll();
        int rotatedCount = 0;

        for (Client client : clients) {
            if (client.getApiKeyExpiresAt() != null &&
                client.getApiKeyExpiresAt().isBefore(threshold) &&
                client.getAtivo()) {

                try {
                    // Set automated rotation user
                    client.setChangedBy("system_auto_rotation");

                    // Regenerate key
                    String newKey = clientService.regenerateApiKey(client.getId());

                    // Set new expiration (90 days)
                    clientService.setApiKeyExpiration(
                        client.getId(),
                        LocalDateTime.now().plusDays(90)
                    );

                    // Notify client
                    sendKeyRotationNotification(client.getEmail(), newKey);

                    rotatedCount++;
                    log.info("Rotated API key for client: {}", client.getEmail());

                } catch (Exception e) {
                    log.error("Failed to rotate key for client {}: {}",
                        client.getEmail(), e.getMessage());
                }
            }
        }

        log.info("Scheduled rotation completed. Rotated {} keys", rotatedCount);
    }

    private void sendKeyRotationNotification(String email, String newKey) {
        // Implementation depends on your notification system
        // Email, Slack, SMS, etc.
    }
}
```

### 7. Audit Report Generation

```java
@Service
public class ClientAuditService {

    @Autowired
    private ClientRepository clientRepository;

    /**
     * Generate API key rotation audit report
     */
    public AuditReport generateRotationReport(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        List<Client> allClients = clientRepository.findAll();
        AuditReport report = new AuditReport();

        for (Client client : allClients) {
            @SuppressWarnings("unchecked")
            List<Object> historyList =
                (List<Object>) client.getMetadata().get("apikey_history");

            if (historyList == null) continue;

            for (Object historyObj : historyList) {
                @SuppressWarnings("unchecked")
                var entry = (java.util.Map<String, Object>) historyObj;

                // Parse timestamp (Jackson deserializes as String or array)
                LocalDateTime changedAt = parseTimestamp(entry.get("createdAt"));

                if (changedAt != null &&
                    changedAt.isAfter(startDate) &&
                    changedAt.isBefore(endDate)) {

                    report.addEntry(AuditEntry.builder()
                        .clientId(client.getId())
                        .clientEmail(client.getEmail())
                        .changedBy((String) entry.get("createdBy"))
                        .changedAt(changedAt)
                        .previousKey(maskKey((String) entry.get("previousApiKey")))
                        .newKey(maskKey((String) entry.get("apiKey")))
                        .build());
                }
            }
        }

        return report;
    }

    private LocalDateTime parseTimestamp(Object timestamp) {
        if (timestamp instanceof String) {
            return LocalDateTime.parse((String) timestamp);
        } else if (timestamp instanceof List) {
            // Jackson may deserialize as [year, month, day, hour, minute, second, nano]
            @SuppressWarnings("unchecked")
            List<Integer> parts = (List<Integer>) timestamp;
            if (parts.size() >= 6) {
                return LocalDateTime.of(
                    parts.get(0),  // year
                    parts.get(1),  // month
                    parts.get(2),  // day
                    parts.get(3),  // hour
                    parts.get(4),  // minute
                    parts.size() > 5 ? parts.get(5) : 0  // second
                );
            }
        }
        return null;
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 10) return "***";
        return key.substring(0, 7) + "..." + key.substring(key.length() - 4);
    }
}

@Data
@Builder
class AuditEntry {
    private Integer clientId;
    private String clientEmail;
    private String changedBy;
    private LocalDateTime changedAt;
    private String previousKey;
    private String newKey;
}

@Data
class AuditReport {
    private List<AuditEntry> entries = new ArrayList<>();
    private LocalDateTime generatedAt = LocalDateTime.now();

    public void addEntry(AuditEntry entry) {
        entries.add(entry);
    }

    public int getTotalChanges() {
        return entries.size();
    }
}
```

### 8. REST API Examples

#### Create Client

```bash
curl -X POST "http://localhost:8080/api/v1/clients" \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Mobile App Client",
    "email": "mobile@example.com",
    "passwordHash": "securePassword123",
    "tipoAssociacao": "COLABORADOR"
  }'
```

Response:
```json
{
  "id": 1,
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "Mobile App Client",
  "email": "mobile@example.com",
  "apiKey": "sk_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "tipoAssociacao": "COLABORADOR",
  "ativo": true,
  "metadata": {}
}
```

#### Regenerate API Key

```bash
curl -X POST "http://localhost:8080/api/v1/clients/1/regenerate-api-key"
```

Response:
```
sk_x9y8z7w6v5u4t3s2r1q0p9o8n7m6l5k4
```

#### Get Client with History

```bash
curl -X GET "http://localhost:8080/api/v1/clients/uuid/550e8400-e29b-41d4-a716-446655440000"
```

Response:
```json
{
  "id": 1,
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "Mobile App Client",
  "email": "mobile@example.com",
  "apiKey": "sk_x9y8z7w6v5u4t3s2r1q0p9o8n7m6l5k4",
  "tipoAssociacao": "COLABORADOR",
  "ativo": true,
  "metadata": {
    "apikey_history": [
      {
        "@class": "bor.tools.simplerag.entity.Client$ApiKeyHistory",
        "apiKey": "sk_x9y8z7w6v5u4t3s2r1q0p9o8n7m6l5k4",
        "previousApiKey": "sk_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
        "createdBy": "system",
        "createdAt": "2025-01-15T14:30:00"
      }
    ]
  }
}
```

## Database Queries

### Query Clients with Recent Key Changes

```sql
-- Find clients who changed API key in last 30 days
SELECT
    c.id,
    c.email,
    c.nome,
    jsonb_array_length(c.metadata->'apikey_history') as total_changes,
    (
        SELECT (entry->>'createdAt')::timestamp
        FROM jsonb_array_elements(c.metadata->'apikey_history') entry
        ORDER BY (entry->>'createdAt')::timestamp DESC
        LIMIT 1
    ) as last_change_date
FROM client c
WHERE c.metadata ? 'apikey_history'
AND (
    SELECT (entry->>'createdAt')::timestamp
    FROM jsonb_array_elements(c.metadata->'apikey_history') entry
    ORDER BY (entry->>'createdAt')::timestamp DESC
    LIMIT 1
) > NOW() - INTERVAL '30 days';
```

### Query Most Frequent Key Changers

```sql
-- Find clients with most API key rotations
SELECT
    c.id,
    c.email,
    c.nome,
    jsonb_array_length(c.metadata->'apikey_history') as rotation_count
FROM client c
WHERE c.metadata ? 'apikey_history'
ORDER BY jsonb_array_length(c.metadata->'apikey_history') DESC
LIMIT 10;
```

### Query by User Who Changed Key

```sql
-- Find all key changes made by specific user
SELECT
    c.id,
    c.email,
    entry->>'apiKey' as new_key,
    entry->>'previousApiKey' as old_key,
    entry->>'createdBy' as changed_by,
    (entry->>'createdAt')::timestamp as changed_at
FROM client c,
     jsonb_array_elements(c.metadata->'apikey_history') entry
WHERE entry->>'createdBy' = 'security_team@company.com'
ORDER BY (entry->>'createdAt')::timestamp DESC;
```

## Best Practices

### 1. Always Set ChangedBy

```java
// GOOD - Track who is making the change
client.setChangedBy(getCurrentUser().getEmail());
client.setApiKey(newKey);

// BAD - Will use default "system"
client.setApiKey(newKey);
```

### 2. Validate Before Key Change

```java
public void rotateApiKey(Integer clientId, String requestedBy) {
    // Verify permissions
    if (!hasPermission(requestedBy, "ROTATE_API_KEY")) {
        throw new SecurityException("Not authorized to rotate API keys");
    }

    // Load client
    Client client = clientRepository.findById(clientId)
        .orElseThrow(...);

    // Set user context
    client.setChangedBy(requestedBy);

    // Generate and set new key
    String newKey = generateSecureApiKey();
    client.setApiKey(newKey);

    // Save (history is automatically stored)
    clientRepository.save(client);

    // Log the action
    log.info("API key rotated for client {} by {}",
        client.getEmail(), requestedBy);
}
```

### 3. Secure Key Storage

```java
// When returning client to API, hide sensitive data
public ClientDTO getClientForApi(Integer clientId) {
    ClientDTO client = clientService.findById(clientId)
        .orElseThrow(...);

    // Hide password
    client.setPasswordHash(null);

    // Optionally hide full API key (show only prefix)
    if (client.getApiKey() != null) {
        String masked = client.getApiKey().substring(0, 10) + "...";
        client.setApiKey(masked);
    }

    return client;
}
```

## Troubleshooting

### Issue: History Not Being Saved

**Problem:** API key changes but no history entries appear.

**Solution:** Ensure you're calling `setApiKey()` method, not directly setting the field:

```java
// CORRECT
client.setApiKey("new_key");

// INCORRECT (bypasses setter logic)
client.apiKey = "new_key";  // This won't trigger history storage
```

### Issue: Cannot Cast Metadata History

**Problem:** ClassCastException when retrieving history.

**Solution:** Handle both LinkedHashMap and ApiKeyHistory types:

```java
Object historyObj = client.getMetadata().get("apikey_history");

if (historyObj instanceof List) {
    @SuppressWarnings("unchecked")
    List<Object> historyList = (List<Object>) historyObj;

    for (Object entry : historyList) {
        if (entry instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;
            // Access fields via map.get("fieldName")
        }
    }
}
```

## Summary

The Client API key management system provides:
- ✅ Automatic history tracking
- ✅ Audit trail for compliance
- ✅ JSONB storage for flexibility
- ✅ User attribution for changes
- ✅ RESTful API for management

Use these examples as templates for implementing API key management in your application.
