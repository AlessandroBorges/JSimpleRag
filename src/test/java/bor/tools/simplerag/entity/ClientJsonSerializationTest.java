package bor.tools.simplerag.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import bor.tools.simplerag.entity.Client.ApiKeyHistory;
import bor.tools.simplerag.entity.enums.TipoAssociacao;

/**
 * Test class for Client entity JSON serialization/deserialization.
 *
 * Focuses on validating that API key history stored in metadata (JSONB field)
 * is correctly serialized to JSON and deserialized back to object without data loss.
 *
 * This is critical because:
 * - Metadata is stored as JSONB in PostgreSQL
 * - API key history must be preserved for audit purposes
 * - Jackson must correctly handle nested ApiKeyHistory objects
 */
@DisplayName("Client JSON Serialization/Deserialization Tests")
class ClientJsonSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Configure ObjectMapper with JavaTimeModule for LocalDateTime support
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should serialize Client with empty metadata to JSON")
    void testSerializeClientWithEmptyMetadata() throws JsonProcessingException {
        // Given - Client with no API key changes
        Client client = Client.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .nome("Test Client")
                .email("test@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .apiKey("sk_initial_api_key_123")
                .ativo(true)
                .tipoAssociacao(TipoAssociacao.LEITOR)
                .metadata(new Metadata())
                .build();

        // When - Serialize to JSON
        String json = objectMapper.writeValueAsString(client);

        // Then - JSON should be valid and contain expected fields
        assertNotNull(json);
        assertTrue(json.contains("\"nome\":\"Test Client\""));
        assertTrue(json.contains("\"email\":\"test@example.com\""));
        assertTrue(json.contains("\"apiKey\":\"sk_initial_api_key_123\""));

        System.out.println("Serialized Client (empty metadata):");
        System.out.println(json);
    }

    @Test
    @DisplayName("Should deserialize JSON to Client with empty metadata")
    void testDeserializeClientWithEmptyMetadata() throws JsonProcessingException {
        // Given - JSON string
        String json = """
                {
                    "id": 1,
                    "uuid": "550e8400-e29b-41d4-a716-446655440000",
                    "nome": "Test Client",
                    "email": "test@example.com",
                    "passwordHash": "$2a$10$hashedPassword",
                    "apiKey": "sk_initial_api_key_123",
                    "ativo": true,
                    "tipoAssociacao": "LEITOR",
		    "metadata": {}
                   
                }
                """;

        // When - Deserialize from JSON
        Client client = objectMapper.readValue(json, Client.class);

        // Then - Client should be correctly deserialized
        assertNotNull(client);
        assertEquals(1, client.getId());
        assertEquals("Test Client", client.getNome());
        assertEquals("test@example.com", client.getEmail());
        assertEquals("sk_initial_api_key_123", client.getApiKey());
        assertTrue(client.getAtivo());
        assertEquals(TipoAssociacao.LEITOR, client.getTipoAssociacao());
        assertNotNull(client.getMetadata());
        assertTrue(client.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("Should serialize and deserialize Client with API key history in metadata")
    void testSerializeDeserializeClientWithApiKeyHistory() throws JsonProcessingException {
        // Given - Client with API key history
        Client client = Client.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .nome("Test Client")
                .email("test@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .changedBy("admin@example.com")
                //.apiKey("sk_initial_key")
                .apiKeyExpiresAt(LocalDateTime.now().plusDays(30))
                .ativo(true)
                .tipoAssociacao(TipoAssociacao.COLABORADOR)
                .metadata(new Metadata())               
                .build();

        
        client.updateApiKey("primeiro",
        	LocalDateTime.now().plusDays(30),
        	"Dom Pedro I");
        // Simulate API key changes (this triggers setApiKey which stores history)
        
        client.updateApiKey("segundo",
        	LocalDateTime.now().plusDays(60),
        	"Dom Pedro II");

        client.updateApiKey("terceiro",
        	LocalDateTime.now().plusDays(60),
        	"Dom Pedro III");
        // When - Serialize to JSON
        String json = objectMapper.writeValueAsString(client);

        System.out.println("\nSerialized Client with API key history:");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(client));

        // Then - Deserialize back from JSON
        Client deserializedClient = objectMapper.readValue(json, Client.class);

        // Verify basic fields
        assertNotNull(deserializedClient);
        assertEquals(client.getId(), deserializedClient.getId());
        assertEquals(client.getNome(), deserializedClient.getNome());
        assertEquals(client.getEmail(), deserializedClient.getEmail());
        assertEquals("terceiro", deserializedClient.getApiKey());

        // Verify metadata contains API key history
        assertNotNull(deserializedClient.getMetadata());
        assertTrue(deserializedClient.getMetadata().containsKey("apikey_history"),
                "Metadata should contain apikey_history key");

        // Verify API key history entries
        List<ApiKeyHistory> historyList = deserializedClient.getApiKeyHistory();
        assertNotNull(historyList);
        assertEquals(3, historyList.size(), "Should have 2 API key change records");

        System.out.println("\nAPI Key History entries: " + historyList.size());	
    }

    @Test
    @DisplayName("Should preserve API key history details after serialization roundtrip")
    void testApiKeyHistoryDetailsPreserved() throws JsonProcessingException {
        // Given - Client with single API key change
        Client client = Client.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .nome("Test Client")
                .email("test@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .apiKey("sk_old_key")
                .ativo(true)
                .tipoAssociacao(TipoAssociacao.PROPRIETARIO)
                .metadata(new Metadata())
                .changedBy("admin@example.com")
                .build();

        String oldKey = client.getApiKey();
        LocalDateTime beforeChange = LocalDateTime.now();

        client.updateApiKey("sk_new_key", beforeChange, "security_team@example.com");
        LocalDateTime afterChange = LocalDateTime.now();

        // When - Serialize and deserialize
        String json = objectMapper.writeValueAsString(client);
        Client deserializedClient = objectMapper.readValue(json, Client.class);

        // Then - Verify history entry details
        @SuppressWarnings("unchecked")
        List<Object> historyList = (List<Object>) deserializedClient.getMetadata().get("apikey_history");
        assertNotNull(historyList);
        assertEquals(1, historyList.size());

        // Note: Jackson deserializes nested objects as LinkedHashMap by default
        // We need to verify the map contains the expected fields
        @SuppressWarnings("unchecked")
        var historyEntry = (java.util.Map<String, Object>) historyList.get(0);

        assertEquals("sk_new_key", historyEntry.get("apiKey"),
                "History should contain new API key");
        assertEquals(oldKey, historyEntry.get("previousApiKey"),
                "History should contain previous API key");
        assertEquals("security_team@example.com", historyEntry.get("createdBy"),
                "History should contain changedBy user");
        assertNotNull(historyEntry.get("createdAt"),
                "History should contain creation timestamp");

        System.out.println("\nAPI Key History Entry:");
        System.out.println("  New Key: " + historyEntry.get("apiKey"));
        System.out.println("  Previous Key: " + historyEntry.get("previousApiKey"));
        System.out.println("  Changed By: " + historyEntry.get("createdBy"));
        System.out.println("  Changed At: " + historyEntry.get("createdAt"));
    }

    @Test
    @DisplayName("Should handle multiple API key changes in sequence")
    void testMultipleApiKeyChanges() throws JsonProcessingException {
        // Given - Client with multiple API key changes
        Client client = Client.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .nome("Production Client")
                .email("prod@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .apiKey("sk_initial")
                .ativo(true)
                .tipoAssociacao(TipoAssociacao.PROPRIETARIO)
                .metadata(new Metadata())
                .changedBy("setup_script")
                .build();

        // Simulate multiple API key rotations
        String[] users = {"admin", "security_team", "ops_team", "compliance"};
        String[] keys = {"sk_rotation_1", "sk_rotation_2", "sk_rotation_3", "sk_rotation_4"};

        for (int i = 0; i < keys.length; i++) {
            client.setChangedBy(users[i]);
            try {
                Thread.sleep(10); // Small delay to ensure different timestamps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            client.updateApiKey(keys[i],
	    	LocalDateTime.now().plusDays(30 + i * 10),
	    	users[i]);
        }

        // When - Serialize and deserialize
        String json = objectMapper.writeValueAsString(client);

        System.out.println("\nClient with multiple API key changes:");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(client));

        Client deserializedClient = objectMapper.readValue(json, Client.class);

        // Then - Verify all changes are preserved
        assertEquals("sk_rotation_4", deserializedClient.getApiKey(),
                "Current API key should be the last one set");

        @SuppressWarnings("unchecked")
        List<Object> historyList = (List<Object>) deserializedClient.getMetadata().get("apikey_history");
        assertNotNull(historyList);
        assertEquals(4, historyList.size(),
                "Should have 4 API key change records");

        // Verify chronological order
        for (int i = 0; i < historyList.size(); i++) {
            @SuppressWarnings("unchecked")
            var entry = (java.util.Map<String, Object>) historyList.get(i);
            assertEquals(keys[i], entry.get("apiKey"),
                    "History entry " + i + " should have correct new key");
            assertEquals(users[i], entry.get("createdBy"),
                    "History entry " + i + " should have correct user");
        }

        System.out.println("\nTotal API key changes recorded: " + historyList.size());
    }

    @Test
    @DisplayName("Should handle ApiKeyHistory as proper object when using Jackson TypeInfo")
    void testApiKeyHistoryAsObject() throws JsonProcessingException {
        // Given - Manually create ApiKeyHistory object
        ApiKeyHistory history1 = ApiKeyHistory.builder()
                .apiKey("sk_new_key_1")
                .previousApiKey("sk_old_key")
                .createdBy("admin@example.com")
                .createdAt(LocalDateTime.now())
                .build();

        ApiKeyHistory history2 = ApiKeyHistory.builder()
                .apiKey("sk_new_key_2")
                .previousApiKey("sk_new_key_1")
                .createdBy("security@example.com")
                .createdAt(LocalDateTime.now().plusHours(1))
                .build();

        // When - Serialize to JSON
        String json1 = objectMapper.writeValueAsString(history1);
        String json2 = objectMapper.writeValueAsString(history2);

        System.out.println("\nSerialized ApiKeyHistory objects:");
        System.out.println("History 1: " + json1);
        System.out.println("History 2: " + json2);

        // Then - Deserialize back
        ApiKeyHistory deserialized1 = objectMapper.readValue(json1, ApiKeyHistory.class);
        ApiKeyHistory deserialized2 = objectMapper.readValue(json2, ApiKeyHistory.class);

        assertEquals(history1.getApiKey(), deserialized1.getApiKey());
        assertEquals(history1.getPreviousApiKey(), deserialized1.getPreviousApiKey());
        assertEquals(history1.getCreatedBy(), deserialized1.getCreatedBy());
        assertNotNull(deserialized1.getCreatedAt());

        assertEquals(history2.getApiKey(), deserialized2.getApiKey());
        assertEquals(history2.getPreviousApiKey(), deserialized2.getPreviousApiKey());
        assertEquals(history2.getCreatedBy(), deserialized2.getCreatedBy());
        assertNotNull(deserialized2.getCreatedAt());
    }

    @Test
    @DisplayName("Should preserve data types after JSONB roundtrip simulation")
    void testJsonbRoundtripSimulation() throws JsonProcessingException {
        // Given - Client with complete data
        Client originalClient = Client.builder()
                .id(42)
                .uuid(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .nome("JSONB Test Client")
                .email("jsonb@test.com")
                .passwordHash("$2a$10$hashedPassword")
                .apiKey("sk_jsonb_test")
                .apiKeyExpiresAt(LocalDateTime.of(2025, 12, 31, 23, 59, 59))
                .ativo(true)
                .tipoAssociacao(TipoAssociacao.COLABORADOR)
                .metadata(new Metadata())
                .changedBy("test_user")
                .build();

        // Simulate API key change
        originalClient.updateApiKey("sk_jsonb_updated",
		LocalDateTime.of(2026, 1, 31, 23, 59, 59),
		"test_user");

        // When - Simulate JSONB storage: serialize -> deserialize
        String json = objectMapper.writeValueAsString(originalClient);
        Client deserializedClient = objectMapper.readValue(json, Client.class);

        // Then - Verify all data types are preserved
        assertEquals(originalClient.getId(), deserializedClient.getId());
        assertEquals(originalClient.getUuid(), deserializedClient.getUuid());
        assertEquals(originalClient.getNome(), deserializedClient.getNome());
        assertEquals(originalClient.getEmail(), deserializedClient.getEmail());
        assertEquals(originalClient.getPasswordHash(), deserializedClient.getPasswordHash());
        assertEquals(originalClient.getApiKey(), deserializedClient.getApiKey());
        assertEquals(originalClient.getAtivo(), deserializedClient.getAtivo());
        assertEquals(originalClient.getTipoAssociacao(), deserializedClient.getTipoAssociacao());

        // Verify metadata is not null
        assertNotNull(deserializedClient.getMetadata());
        assertTrue(deserializedClient.getMetadata().containsKey("apikey_history"));

        System.out.println("\nJSONB roundtrip successful!");
        System.out.println("Original ID: " + originalClient.getId());
        System.out.println("Deserialized ID: " + deserializedClient.getId());
        System.out.println("History preserved: " + deserializedClient.getMetadata().containsKey("apikey_history"));
    }
}
