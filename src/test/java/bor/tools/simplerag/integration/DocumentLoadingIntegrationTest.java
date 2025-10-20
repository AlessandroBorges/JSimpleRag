package bor.tools.simplerag.integration;

import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.repository.ChapterRepository;
import bor.tools.simplerag.repository.DocEmbeddingJdbcRepository;
import bor.tools.simplerag.repository.DocumentoRepository;
import bor.tools.simplerag.repository.LibraryRepository;
import bor.tools.simplerag.service.DocumentoService;
import bor.tools.simplerag.service.LibraryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for complete document loading workflow
 *
 * Tests the entire pipeline from upload through processing,
 * verifying database persistence and async operations.
 *
 * NOTE: These tests require a running PostgreSQL database with PGVector.
 * Configure test database in application-test.properties
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentLoadingIntegrationTest {

    @Autowired
    private DocumentoService documentoService;

    @Autowired
    private LibraryService libraryService;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private DocEmbeddingJdbcRepository embeddingRepository;

    private Library testLibrary;
    private LibraryDTO testLibraryDTO;

    @BeforeEach
    void setUp() {
        // Clean up test data
        try {
	    embeddingRepository.deleteAll();
	} catch (DataAccessException e) {	    
	    e.printStackTrace();
	} catch (SQLException e) {	   
	    e.printStackTrace();
	}
        chapterRepository.deleteAll();
        documentoRepository.deleteAll();
        libraryRepository.deleteAll();

        // Create test library
        testLibrary = new Library();
        testLibrary.setNome("Integration Test Library");
        testLibrary.setUuid(UUID.randomUUID());
        testLibrary.setAreaConhecimento("Test Area");
        testLibrary.setPesoSemantico(0.6f);
        testLibrary.setPesoTextual(0.4f);
        testLibrary = libraryRepository.save(testLibrary);

        testLibraryDTO = LibraryDTO.from(testLibrary);
    }

    // ============ End-to-End Upload Workflow Tests ============

    @Test
    void testCompleteWorkflow_UploadFromText() {
        // Given
        String titulo = "Integration Test Document";
        String conteudo = "# Integration Test\n\n" +
                         "This is a test document for integration testing.\n\n" +
                         "## Section 1\n" +
                         "Content for section 1.\n\n" +
                         "## Section 2\n" +
                         "Content for section 2.";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("autor", "Integration Test");
        metadata.put("area", "Testing");

        // When - Upload document
        DocumentoDTO uploadedDoc = documentoService.uploadFromText(
                titulo,
                conteudo,
                testLibrary.getId(),
                new MetaDoc(metadata)
        );

        // Then - Verify document was saved
        assertNotNull(uploadedDoc);
        assertNotNull(uploadedDoc.getId());
        assertEquals(titulo, uploadedDoc.getTitulo());
        assertEquals(testLibrary.getId(), uploadedDoc.getBibliotecaId());
        assertTrue(uploadedDoc.getFlagVigente());
        assertNotNull(uploadedDoc.getTokensTotal());
        assertTrue(uploadedDoc.getTokensTotal() > 0);

        // Verify in database
        Optional<Documento> savedDoc = documentoRepository.findById(uploadedDoc.getId());
        assertTrue(savedDoc.isPresent());
        assertEquals(conteudo, savedDoc.get().getConteudoMarkdown());
        assertTrue(savedDoc.get().getMetadados().containsKey("autor"));
    }

    @Test
    void testCompleteWorkflow_UploadAndProcess() throws Exception {
        // Given
        String titulo = "Processing Test Document";
        String conteudo = "# Processing Test\n\n" +
                         "This document will be processed to generate chapters and embeddings.\n\n" +
                         "## Chapter 1\n" +
                         "Content of chapter 1 with enough text to be meaningful.\n\n" +
                         "## Chapter 2\n" +
                         "Content of chapter 2 with more information.";

        // When - Upload document
        DocumentoDTO uploadedDoc = documentoService.uploadFromText(
                titulo,
                conteudo,
                testLibrary.getId(),
                null
        );

        assertNotNull(uploadedDoc.getId());

        // When - Process document
        CompletableFuture<DocumentoService.ProcessingStatus> processingFuture =
                documentoService.processDocumentAsync(uploadedDoc.getId(), false, false);

        // Wait for processing to complete
        DocumentoService.ProcessingStatus status = processingFuture.get();

        // Then - Verify processing completed
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());
        assertEquals(uploadedDoc.getId(), status.getDocumentId());
        assertNotNull(status.getChaptersCount());
        assertNotNull(status.getEmbeddingsCount());

        // Verify chapters were saved
        long chapterCount = chapterRepository.countByDocumento(uploadedDoc.getId());
        assertTrue(chapterCount > 0, "Should have created chapters");

        // Verify document was updated
        Optional<Documento> processedDoc = documentoRepository.findById(uploadedDoc.getId());
        assertTrue(processedDoc.isPresent());
        assertNotNull(processedDoc.get().getTokensTotal());
    }

    // ============ CRUD Operations Integration Tests ============

    @Test
    void testFindById_Integration() {
        // Given
        Documento documento = Documento.builder()
                .bibliotecaId(testLibrary.getId())
                .titulo("Find By ID Test")
                .conteudoMarkdown("Content")
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .build();
        documento = documentoRepository.save(documento);

        // When
        Optional<DocumentoDTO> found = documentoService.findById(documento.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("Find By ID Test", found.get().getTitulo());
        assertEquals(testLibrary.getId(), found.get().getBibliotecaId());
    }

    @Test
    void testFindByLibraryId_Integration() {
        // Given - Create multiple documents
        for (int i = 1; i <= 3; i++) {
            Documento doc = Documento.builder()
                    .bibliotecaId(testLibrary.getId())
                    .titulo("Document " + i)
                    .conteudoMarkdown("Content " + i)
                    .flagVigente(true)
                    .dataPublicacao(LocalDate.now())
                    .build();
            documentoRepository.save(doc);
        }

        // When
        var documents = documentoService.findByLibraryId(testLibrary.getId());

        // Then
        assertEquals(3, documents.size());
        assertTrue(documents.stream().allMatch(d ->
                d.getBibliotecaId().equals(testLibrary.getId())
        ));
    }

    @Test
    void testFindActiveByLibraryId_Integration() {
        // Given - Create active and inactive documents
        Documento activeDoc = Documento.builder()
                .bibliotecaId(testLibrary.getId())
                .titulo("Active Document")
                .conteudoMarkdown("Content")
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .build();
        documentoRepository.save(activeDoc);

        Documento inactiveDoc = Documento.builder()
                .bibliotecaId(testLibrary.getId())
                .titulo("Inactive Document")
                .conteudoMarkdown("Content")
                .flagVigente(false)
                .dataPublicacao(LocalDate.now())
                .build();
        documentoRepository.save(inactiveDoc);

        // When
        var activeDocuments = documentoService.findActiveByLibraryId(testLibrary.getId());

        // Then
        assertEquals(1, activeDocuments.size());
        assertTrue(activeDocuments.get(0).getFlagVigente());
        assertEquals("Active Document", activeDocuments.get(0).getTitulo());
    }

    @Test
    void testUpdateStatus_Integration() {
        // Given
        Documento documento = Documento.builder()
                .bibliotecaId(testLibrary.getId())
                .titulo("Status Test")
                .conteudoMarkdown("Content")
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .build();
        documento = documentoRepository.save(documento);

        // When - Deactivate
        documentoService.updateStatus(documento.getId(), false);

        // Then
        Optional<Documento> updated = documentoRepository.findById(documento.getId());
        assertTrue(updated.isPresent());
        assertFalse(updated.get().getFlagVigente());

        // When - Reactivate
        documentoService.updateStatus(documento.getId(), true);

        // Then
        updated = documentoRepository.findById(documento.getId());
        assertTrue(updated.isPresent());
        assertTrue(updated.get().getFlagVigente());
    }

    @Test
    void testDelete_Integration() {
        // Given
        Documento documento = Documento.builder()
                .bibliotecaId(testLibrary.getId())
                .titulo("Delete Test")
                .conteudoMarkdown("Content")
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .build();
        documento = documentoRepository.save(documento);

        // When
        documentoService.delete(documento.getId());

        // Then - Soft delete, document still exists but inactive
        Optional<Documento> deleted = documentoRepository.findById(documento.getId());
        assertTrue(deleted.isPresent());
        assertFalse(deleted.get().getFlagVigente());
    }

    // ============ Library Validation Tests ============

    @Test
    void testUploadWithInvalidLibrary_ThrowsException() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            documentoService.uploadFromText(
                    "Test",
                    "Content",
                    99999, // Non-existent library
                    null
            );
        });
    }

    // ============ Token Estimation Tests ============

    @Test
    void testTokenEstimation_Integration() {
        // Given - Document with known word count
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            content.append("word ");
        }

        // When
        DocumentoDTO uploadedDoc = documentoService.uploadFromText(
                "Token Test",
                content.toString(),
                testLibrary.getId(),
                null
        );

        // Then - Verify tokens were estimated
        assertNotNull(uploadedDoc.getTokensTotal());
        assertTrue(uploadedDoc.getTokensTotal() > 50); // Should be around 133 tokens
        assertTrue(uploadedDoc.getTokensTotal() < 200);
    }

    // ============ Metadata Handling Tests ============

    @Test
    void testMetadataPreservation_Integration() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("autor", "Test Author");
        metadata.put("data_publicacao", "2025-01-15");
        metadata.put("area", "Testing");
        metadata.put("custom_field", "custom_value");

        // When
        DocumentoDTO uploadedDoc = documentoService.uploadFromText(
                "Metadata Test",
                "Content",
                testLibrary.getId(),
                new MetaDoc(metadata)
        );

        // Then - Verify all metadata was preserved
        Optional<Documento> saved = documentoRepository.findById(uploadedDoc.getId());
        assertTrue(saved.isPresent());

        Map<String, Object> savedMetadata = saved.get().getMetadados();
        assertNotNull(savedMetadata);
        assertEquals("Test Author", savedMetadata.get("autor"));
        assertEquals("2025-01-15", savedMetadata.get("data_publicacao"));
        assertEquals("Testing", savedMetadata.get("area"));
        assertEquals("custom_value", savedMetadata.get("custom_field"));
    }

    // ============ Concurrent Operations Tests ============

    @Test
    void testConcurrentUploads_Integration() throws InterruptedException {
        // Given - Multiple concurrent uploads
        int numDocuments = 5;
        Thread[] threads = new Thread[numDocuments];

        for (int i = 0; i < numDocuments; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                documentoService.uploadFromText(
                        "Concurrent Document " + index,
                        "Content " + index,
                        testLibrary.getId(),
                        null
                );
            });
        }

        // When - Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - Verify all documents were saved
        var documents = documentoService.findByLibraryId(testLibrary.getId());
        assertEquals(numDocuments, documents.size());
    }

    // ============ Error Handling Tests ============

    @Test
    void testProcessNonExistentDocument_ReturnsFailedStatus() throws Exception {
        // When
        CompletableFuture<DocumentoService.ProcessingStatus> future =
                documentoService.processDocumentAsync(99999, false, false);

        DocumentoService.ProcessingStatus status = future.get();

        // Then
        assertNotNull(status);
        assertEquals("FAILED", status.getStatus());
        assertNotNull(status.getErrorMessage());
    }

    @Test
    void testUpdateStatusNonExistentDocument_ThrowsException() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            documentoService.updateStatus(99999, false);
        });
    }

    // ============ Data Integrity Tests ============

    @Test
    void testDocumentDates_Integration() {
        // Given
        LocalDate publicationDate = LocalDate.of(2025, 1, 15);

        Documento documento = Documento.builder()
                .bibliotecaId(testLibrary.getId())
                .titulo("Date Test")
                .conteudoMarkdown("Content")
                .flagVigente(true)
                .dataPublicacao(publicationDate)
                .build();
        documento = documentoRepository.save(documento);

        // When
        Optional<DocumentoDTO> found = documentoService.findById(documento.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(publicationDate, found.get().getDataPublicacao());
        assertNotNull(found.get().getCreatedAt());
    }

    @Test
    void testFlagVigenteConstraint_Integration() {
        // This test verifies that the flagVigente field works correctly
        // Given - Two documents with same title, different flagVigente
        Documento oldVersion = Documento.builder()
                .bibliotecaId(testLibrary.getId())
                .titulo("Versioned Document")
                .conteudoMarkdown("Old content")
                .flagVigente(false)
                .dataPublicacao(LocalDate.now().minusDays(10))
                .build();
        documentoRepository.save(oldVersion);

        Documento newVersion = Documento.builder()
                .bibliotecaId(testLibrary.getId())
                .titulo("Versioned Document")
                .conteudoMarkdown("New content")
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .build();
        documentoRepository.save(newVersion);

        // When - Query for active documents
        var activeDocuments = documentoService.findActiveByLibraryId(testLibrary.getId());

        // Then - Should only find the active version
        assertEquals(1, activeDocuments.size());
        assertEquals("New content", activeDocuments.get(0).getConteudoMarkdown());
    }
}
