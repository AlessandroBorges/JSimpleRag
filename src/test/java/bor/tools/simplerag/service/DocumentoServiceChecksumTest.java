package bor.tools.simplerag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.repository.DocumentoRepository;
import bor.tools.utils.RagUtils;

/**
 * Unit tests for checksum calculation and duplicate detection in DocumentoService.
 *
 * Tests cover:
 * - Checksum calculation using CRC64
 * - Text normalization for checksum
 * - Duplicate document detection (same checksum + biblioteca_id)
 * - Successful upload when no duplicates exist
 * - Exception throwing when duplicate is detected
 * - Edge cases (null, empty content, different libraries)
 */
@ExtendWith(MockitoExtension.class)
class DocumentoServiceChecksumTest {

    @Mock
    private DocumentoRepository documentoRepository;

    @Mock
    private LibraryService libraryService;

    @InjectMocks
    private DocumentoService documentoService;

    private Library testLibrary;
    private Integer testLibraryId = 1;
    private String testContent = "Este é um documento de teste com conteúdo único.";
    private String testTitle = "Documento de Teste";

    @BeforeEach
    void setUp() {
        testLibrary = new Library();
        testLibrary.setId(testLibraryId);
        testLibrary.setNome("Biblioteca de Teste");
    }

    // ========== Checksum Calculation Tests ==========

    @Test
    void testChecksumCalculation_SameContentProducesSameChecksum() {
        String content1 = "Conteúdo de teste";
        String content2 = "Conteúdo de teste";

        String checksum1 = calculateChecksum(content1);
        String checksum2 = calculateChecksum(content2);

        assertNotNull(checksum1, "Checksum should not be null");
        assertNotNull(checksum2, "Checksum should not be null");
        assertEquals(checksum1, checksum2, "Same content should produce same checksum");
    }

    @Test
    void testChecksumCalculation_DifferentContentProducesDifferentChecksum() {
        String content1 = "Conteúdo A";
        String content2 = "Conteúdo B";

        String checksum1 = calculateChecksum(content1);
        String checksum2 = calculateChecksum(content2);

        assertNotEquals(checksum1, checksum2, "Different content should produce different checksums");
    }

    @Test
    void testChecksumCalculation_NormalizedContent() {
        // Different formatting, same content after normalization
        String content1 = "Teste   com    espaços";
        String content2 = "teste com espaços";
        String content3 = "TESTE COM ESPAÇOS";

        String checksum1 = calculateChecksum(content1);
        String checksum2 = calculateChecksum(content2);
        String checksum3 = calculateChecksum(content3);

        assertEquals(checksum1, checksum2, "Different spacing should produce same checksum");
        assertEquals(checksum2, checksum3, "Different case should produce same checksum");
    }

    @Test
    void testChecksumCalculation_WhitespaceVariations() {
        String content1 = "Linha 1\nLinha 2\nLinha 3";
        String content2 = "Linha 1 Linha 2 Linha 3";
        String content3 = "Linha 1\t\tLinha 2\t\tLinha 3";

        String checksum1 = calculateChecksum(content1);
        String checksum2 = calculateChecksum(content2);
        String checksum3 = calculateChecksum(content3);

        // All should normalize to same content
        assertEquals(checksum1, checksum2, "Newlines and spaces should normalize to same checksum");
        assertEquals(checksum2, checksum3, "Tabs and spaces should normalize to same checksum");
    }

    @Test
    void testChecksumCalculation_EmptyContent() {
        String checksum1 = calculateChecksum("");
        String checksum2 = calculateChecksum("   ");
        String checksum3 = calculateChecksum("\n\t  ");

        // Empty/whitespace-only content should produce same checksum
        assertEquals(checksum1, checksum2, "Empty and whitespace-only should produce same checksum");
        assertEquals(checksum2, checksum3, "Different whitespace types should normalize to same");
    }

    @Test
    void testChecksumCalculation_NullContent() {
        String checksum = calculateChecksum(null);
        assertNull(checksum, "Null content should produce null checksum");
    }

    // ========== Duplicate Detection Tests ==========

    @Test
    void testUploadFromText_NoDuplicates_Success() {
        // Arrange
        when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));
        when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                .thenReturn(Collections.emptyList());

        Documento savedDoc = createTestDocument(testTitle, testContent);
        savedDoc.setId(100);
        when(documentoRepository.save(any(Documento.class))).thenReturn(savedDoc);

        // Act
        DocumentoDTO result = documentoService.uploadFromText(testTitle, testContent, testLibraryId, null);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(100, result.getId(), "Document should have ID");
        assertEquals(testTitle, result.getTitulo(), "Title should match");
        verify(documentoRepository, times(1)).save(any(Documento.class));
    }

    @Test
    void testUploadFromText_DuplicateDetected_ThrowsException() {
        // Arrange
        when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));

        // Create existing document with checksum in metadata
        Documento existingDoc = createTestDocument("Documento Existente", testContent);
        existingDoc.setId(99);
        String checksum = calculateChecksum(testContent);
        MetaDoc existingMeta = new MetaDoc();
        existingMeta.setChecksum(checksum);
        existingDoc.setMetadados(existingMeta);

        when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                .thenReturn(Arrays.asList(existingDoc));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> documentoService.uploadFromText(testTitle, testContent, testLibraryId, null),
                "Should throw exception when duplicate is detected"
        );

        assertTrue(exception.getMessage().contains("identical content already exists"),
                "Exception message should mention duplicate content");
        assertTrue(exception.getMessage().contains("Documento Existente"),
                "Exception message should include existing document title");
        assertTrue(exception.getMessage().contains("99"),
                "Exception message should include existing document ID");

        verify(documentoRepository, never()).save(any(Documento.class));
    }

    @Test
    void testUploadFromText_SameContentDifferentLibrary_Success() {
        // Arrange
        Integer library1Id = 1;
        Integer library2Id = 2;

        Library library2 = new Library();
        library2.setId(library2Id);
        library2.setNome("Biblioteca 2");

        when(libraryService.findById(library1Id)).thenReturn(Optional.of(testLibrary));
        when(libraryService.findById(library2Id)).thenReturn(Optional.of(library2));

        // Document in library 1
        Documento doc1 = createTestDocument(testTitle, testContent);
        doc1.setId(100);
        doc1.setBibliotecaId(library1Id);
        String checksum = calculateChecksum(testContent);
        MetaDoc meta1 = new MetaDoc();
        meta1.setChecksum(checksum);
        doc1.setMetadados(meta1);

        when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(library1Id))
                .thenReturn(Arrays.asList(doc1));
        when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(library2Id))
                .thenReturn(Collections.emptyList());

        // Save mock - use answer to return different IDs
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> {
            Documento doc = invocation.getArgument(0);
            Documento saved = Documento.builder()
                    .id(doc.getBibliotecaId().equals(library1Id) ? 100 : 200)
                    .bibliotecaId(doc.getBibliotecaId())
                    .titulo(doc.getTitulo())
                    .conteudoMarkdown(doc.getConteudoMarkdown())
                    .flagVigente(doc.getFlagVigente())
                    .dataPublicacao(doc.getDataPublicacao())
                    .tokensTotal(doc.getTokensTotal())
                    .metadados(doc.getMetadados())
                    .build();
            return saved;
        });

        // Act - Upload to library 1 (already exists)
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> documentoService.uploadFromText(testTitle, testContent, library1Id, null)
        );

        // Act - Upload same content to library 2 (should succeed)
        DocumentoDTO result2 = documentoService.uploadFromText(testTitle, testContent, library2Id, null);

        // Assert
        assertNotNull(result2, "Upload to different library should succeed");
        assertEquals(200, result2.getId(), "Document in library 2 should have different ID");
    }

    @Test
    void testUploadFromText_DifferentFormattingSameContent_DetectedAsDuplicate() {
        // Arrange
        when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));

        String content1 = "TEXTO COM VÁRIAS    LINHAS\n\nE ESPAÇOS";
        String content2 = "texto com várias linhas e espaços"; // Same after normalization

        // Existing document
        Documento existingDoc = createTestDocument("Documento 1", content1);
        existingDoc.setId(1);
        String checksum1 = calculateChecksum(content1);
        MetaDoc meta = new MetaDoc();
        meta.setChecksum(checksum1);
        existingDoc.setMetadados(meta);

        when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                .thenReturn(Arrays.asList(existingDoc));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> documentoService.uploadFromText("Documento 2", content2, testLibraryId, null),
                "Different formatting but same content should be detected as duplicate"
        );

        assertTrue(exception.getMessage().contains("identical content already exists"));
        verify(documentoRepository, never()).save(any(Documento.class));
    }

    @Test
    void testUploadFromText_NonVigenteDocument_NotConsideredDuplicate() {
        // Arrange
        when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));

        // Non-vigente document (should not be considered for duplicate check)
        Documento nonVigenteDoc = createTestDocument("Documento Antigo", testContent);
        nonVigenteDoc.setId(50);
        nonVigenteDoc.setFlagVigente(false);
        String checksum = calculateChecksum(testContent);
        MetaDoc meta = new MetaDoc();
        meta.setChecksum(checksum);
        nonVigenteDoc.setMetadados(meta);

        // Only return vigente documents
        when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                .thenReturn(Collections.emptyList());

        Documento savedDoc = createTestDocument(testTitle, testContent);
        savedDoc.setId(100);
        when(documentoRepository.save(any(Documento.class))).thenReturn(savedDoc);

        // Act
        DocumentoDTO result = documentoService.uploadFromText(testTitle, testContent, testLibraryId, null);

        // Assert
        assertNotNull(result, "Should succeed when only non-vigente duplicates exist");
        assertEquals(100, result.getId());
        verify(documentoRepository, times(1)).save(any(Documento.class));
    }

    @Test
    void testUploadFromText_LibraryNotFound_ThrowsException() {
        // Arrange
        when(libraryService.findById(testLibraryId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> documentoService.uploadFromText(testTitle, testContent, testLibraryId, null)
        );

        assertTrue(exception.getMessage().contains("Library not found"));
        verify(documentoRepository, never()).save(any(Documento.class));
    }

    @Test
    void testUploadFromText_NullChecksum_NoExceptionThrown() {
        // Arrange
        when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));
        lenient().when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                .thenReturn(Collections.emptyList());

        // Use answer to create saved document with same empty content
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> {
            Documento doc = invocation.getArgument(0);
            Documento saved = Documento.builder()
                    .id(100)
                    .bibliotecaId(doc.getBibliotecaId())
                    .titulo(doc.getTitulo())
                    .conteudoMarkdown(doc.getConteudoMarkdown())
                    .flagVigente(doc.getFlagVigente())
                    .dataPublicacao(doc.getDataPublicacao())
                    .tokensTotal(doc.getTokensTotal())
                    .metadados(doc.getMetadados())
                    .build();
            return saved;
        });

        // Act - Upload with empty content (checksum will be null or empty)
        DocumentoDTO result = documentoService.uploadFromText(testTitle, "", testLibraryId, null);

        // Assert
        assertNotNull(result, "Should handle empty content gracefully");
        verify(documentoRepository, times(1)).save(any(Documento.class));
    }

    @Test
    void testUploadFromText_ChecksumStoredInMetadata() {
        // Arrange
        when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));
        when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                .thenReturn(Collections.emptyList());

        Documento savedDoc = createTestDocument(testTitle, testContent);
        savedDoc.setId(100);
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> {
            Documento doc = invocation.getArgument(0);
            savedDoc.setMetadados(doc.getMetadados());
            return savedDoc;
        });

        // Act
        DocumentoDTO result = documentoService.uploadFromText(testTitle, testContent, testLibraryId, null);

        // Assert
        verify(documentoRepository, times(1)).save(argThat(doc -> {
            MetaDoc meta = new MetaDoc(doc.getMetadados());
            String storedChecksum = meta.getChecksum();
            String expectedChecksum = calculateChecksum(testContent);
            return storedChecksum != null && storedChecksum.equals(expectedChecksum);
        }));
    }

    // ========== Helper Methods ==========

    /**
     * Helper method to calculate checksum using same logic as DocumentoService
     */
    private String calculateChecksum(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        String normalized = content.toLowerCase().replaceAll("\\s+", " ").trim();
        byte[] bytes = normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return RagUtils.getCRC64Checksum(bytes);
    }

    /**
     * Helper method to create test document
     */
    private Documento createTestDocument(String titulo, String conteudo) {
        return Documento.builder()
                .bibliotecaId(testLibraryId)
                .titulo(titulo)
                .conteudoMarkdown(conteudo)
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .metadados(new MetaDoc())
                .build();
    }
}
