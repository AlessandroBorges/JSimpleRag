package bor.tools.simplerag.service;

import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.Chapter;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.entity.enums.TipoConteudo;
import bor.tools.simplerag.repository.ChapterRepository;
import bor.tools.simplerag.repository.DocEmbeddingJdbcRepository;
import bor.tools.simplerag.repository.DocumentoRepository;
import bor.tools.splitter.AsyncSplitterService;
import bor.tools.splitter.DocumentRouter;
import bor.tools.utils.DocumentConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentoService
 *
 * Tests document upload, processing, and persistence operations
 * using mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class DocumentoServiceTest {

    @Mock
    private DocumentoRepository documentoRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private DocEmbeddingJdbcRepository embeddingRepository;

    @Mock
    private LibraryService libraryService;

    @Mock
    private DocumentConverter documentConverter;

    @Mock
    private DocumentRouter documentRouter;

    @Mock
    private AsyncSplitterService asyncSplitterService;

    @InjectMocks
    private DocumentoService documentoService;

    private Library testLibrary;
    private Documento testDocumento;

    @BeforeEach
    void setUp() {
        // Setup test library
        testLibrary = new Library();
        testLibrary.setId(1);
        testLibrary.setNome("Test Library");
        testLibrary.setUuid(UUID.randomUUID());

        // Setup test documento
        testDocumento = Documento.builder()
                .id(1)
                .bibliotecaId(1)
                .titulo("Test Document")
                .conteudoMarkdown("# Test\nContent")
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .build();
    }

    // ============ Upload from Text Tests ============

    @Test
    void testUploadFromText_Success() {
        // Given
        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        DocumentoDTO result = documentoService.uploadFromText(
                "Test Document",
                "# Test\nContent",
                1,
                new MetaDoc()
        );

        // Then
        assertNotNull(result);
        assertEquals("Test Document", result.getTitulo());
        verify(documentoRepository).save(any(Documento.class));
    }

    @Test
    void testUploadFromText_LibraryNotFound() {
        // Given
        when(libraryService.findById(1)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            documentoService.uploadFromText(
                    "Test Document",
                    "Content",
                    1,
                    null
            );
        });

        verify(documentoRepository, never()).save(any());
    }

    @Test
    void testUploadFromText_WithMetadata() {
        // Given
        MetaDoc metadata = new MetaDoc();
        metadata.put("autor", "Test Author");
        metadata.put("area", "Test Area");

        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        DocumentoDTO result = documentoService.uploadFromText(
                "Test Document",
                "Content",
                1,
                metadata
        );

        // Then
        assertNotNull(result);
        verify(documentoRepository).save(argThat(doc ->
                doc.getMetadados() != null &&
                doc.getMetadados().containsKey("autor")
        ));
    }

    @Test
    void testUploadFromText_TokenCountCalculated() {
        // Given
        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        String longContent = "word ".repeat(1000); // ~1000 words

        // When
        documentoService.uploadFromText("Test", longContent, 1, null);

        // Then
        verify(documentoRepository).save(argThat(doc ->
                doc.getTokensTotal() != null && doc.getTokensTotal() > 0
        ));
    }

    // ============ Upload from URL Tests ============

    @Test
    void testUploadFromUrl_Success() throws Exception {
        // Given
        String url = "https://example.com/document.pdf";
        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentConverter.detectFormat(any(java.net.URI.class)))
                .thenReturn("application/pdf");
        when(documentConverter.convertToMarkdown(any(java.net.URI.class), anyString()))
                .thenReturn("# Converted Content");
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        DocumentoDTO result = documentoService.uploadFromUrl(url, 1, "Test", null);

        // Then
        assertNotNull(result);
        verify(documentConverter).detectFormat(any(java.net.URI.class));
        verify(documentConverter).convertToMarkdown(any(java.net.URI.class), eq("application/pdf"));
        verify(documentoRepository).save(any(Documento.class));
    }

    @Test
    void testUploadFromUrl_DeriveTitleFromUrl() throws Exception {
        // Given
        String url = "https://example.com/my-document.pdf";
        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentConverter.detectFormat(any(java.net.URI.class))).thenReturn("application/pdf");
        when(documentConverter.convertToMarkdown(any(java.net.URI.class), anyString()))
                .thenReturn("Content");
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        documentoService.uploadFromUrl(url, 1, null, null);

        // Then
        verify(documentoRepository).save(argThat(doc ->
                doc.getTitulo() != null && !doc.getTitulo().isEmpty()
        ));
    }

    @Test
    void testUploadFromUrl_MetadataIncludesUrl() throws Exception {
        // Given
        String url = "https://example.com/doc.pdf";
        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentConverter.detectFormat(any(java.net.URI.class))).thenReturn("application/pdf");
        when(documentConverter.convertToMarkdown(any(java.net.URI.class), anyString()))
                .thenReturn("Content");
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        documentoService.uploadFromUrl(url, 1, "Test", null);

        // Then
        verify(documentoRepository).save(argThat(doc ->
                doc.getMetadados() != null &&
                doc.getMetadados().containsKey("url") &&
                doc.getMetadados().get("url").equals(url)
        ));
    }

    // ============ Upload from File Tests ============

    @Test
    void testUploadFromFile_Success() throws Exception {
        // Given
        String fileName = "test.pdf";
        byte[] fileContent = "PDF content".getBytes();

        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentConverter.detectFormat(any(byte[].class))).thenReturn("application/pdf");
        when(documentConverter.convertToMarkdown(any(byte[].class), anyString()))
                .thenReturn("# Converted");
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        DocumentoDTO result = documentoService.uploadFromFile(fileName, fileContent, 1, null);

        // Then
        assertNotNull(result);
        verify(documentConverter).detectFormat(fileContent);
        verify(documentConverter).convertToMarkdown(fileContent, "application/pdf");
    }

    @Test
    void testUploadFromFile_MetadataIncludesFileInfo() throws Exception {
        // Given
        String fileName = "document.docx";
        byte[] fileContent = "content".getBytes();

        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentConverter.detectFormat(any(byte[].class))).thenReturn("application/vnd.openxmlformats");
        when(documentConverter.convertToMarkdown(any(byte[].class), anyString()))
                .thenReturn("Content");
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        documentoService.uploadFromFile(fileName, fileContent, 1, null);

        // Then
        verify(documentoRepository).save(argThat(doc ->
                doc.getMetadados() != null &&
                doc.getMetadados().containsKey("file_name") &&
                doc.getMetadados().containsKey("file_size_bytes")
        ));
    }

    // ============ Process Document Async Tests ============

    @Test
    void testProcessDocumentAsync_Success() throws Exception {
        // Given
        when(documentoRepository.findById(1)).thenReturn(Optional.of(testDocumento));
        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentRouter.detectContentType(anyString())).thenReturn(TipoConteudo.OUTROS);

        // Create mock processing result
        AsyncSplitterService.ProcessingResult mockResult = new AsyncSplitterService.ProcessingResult();
        mockResult.setCapitulos(Collections.emptyList());
        mockResult.setAllEmbeddings(Collections.emptyList());

        when(asyncSplitterService.fullProcessingAsync(
                any(DocumentoDTO.class),
                any(LibraryDTO.class),
                any(TipoConteudo.class),
                anyBoolean(),
                anyBoolean()
        )).thenReturn(CompletableFuture.completedFuture(mockResult));

        when(chapterRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        CompletableFuture<DocumentoService.ProcessingStatus> future =
                documentoService.processDocumentAsync(1, false, false);
        DocumentoService.ProcessingStatus status = future.get();

        // Then
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());
        assertEquals(1, status.getDocumentId());
    }

    @Test
    void testProcessDocumentAsync_DocumentNotFound() throws Exception {
        // Given
        when(documentoRepository.findById(999)).thenReturn(Optional.empty());

        // When
        CompletableFuture<DocumentoService.ProcessingStatus> future =
                documentoService.processDocumentAsync(999, false, false);
        DocumentoService.ProcessingStatus status = future.get();

        // Then
        assertNotNull(status);
        assertEquals("FAILED", status.getStatus());
        assertNotNull(status.getErrorMessage());
    }

    // ============ Persistence Tests ============

    @Test
    void testPersistProcessingResult_SavesChapters() throws SQLException {
        // Given
        AsyncSplitterService.ProcessingResult result = new AsyncSplitterService.ProcessingResult();

        ChapterDTO chapterDTO = ChapterDTO.builder()
                .titulo("Chapter 1")
                .conteudo("Content")
                .ordemDoc(1)
                .build();
        result.setCapitulos(Collections.singletonList(chapterDTO));
        result.setAllEmbeddings(Collections.emptyList());

        Chapter savedChapter = Chapter.builder()
                .id(1)
                .titulo("Chapter 1")
                .build();

        when(chapterRepository.saveAll(anyList()))
                .thenReturn(Collections.singletonList(savedChapter));
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        documentoService.persistProcessingResult(result, testDocumento);

        // Then
        verify(chapterRepository).saveAll(argThat(chapters -> {
            List<Chapter> chapterList = new ArrayList<>();
            chapters.forEach(chapterList::add);
            return chapterList.size() == 1 && "Chapter 1".equals(chapterList.get(0).getTitulo());
        }));

    }

    @Test
    void testPersistProcessingResult_SavesEmbeddings() throws SQLException {
        // Given
        AsyncSplitterService.ProcessingResult result = new AsyncSplitterService.ProcessingResult();
        result.setCapitulos(Collections.emptyList());

        DocumentEmbeddingDTO embeddingDTO = new DocumentEmbeddingDTO();
        embeddingDTO.setTrechoTexto("Text");
        embeddingDTO.setEmbeddingVector(new float[]{0.1f, 0.2f});

        result.setAllEmbeddings(Collections.singletonList(embeddingDTO));

        when(chapterRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(embeddingRepository.save(any())).thenReturn(1);
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        documentoService.persistProcessingResult(result, testDocumento);

        // Then
        verify(embeddingRepository, times(1)).save(any());
    }

    @Test
    void testPersistProcessingResult_HandlesEmbeddingSaveError() throws SQLException {
        // Given
        AsyncSplitterService.ProcessingResult result = new AsyncSplitterService.ProcessingResult();
        result.setCapitulos(Collections.emptyList());

        DocumentEmbeddingDTO embeddingDTO = new DocumentEmbeddingDTO();
        embeddingDTO.setTrechoTexto("Text");
        result.setAllEmbeddings(Collections.singletonList(embeddingDTO));

        when(chapterRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(embeddingRepository.save(any())).thenThrow(new SQLException("DB error"));

        // When/Then
        assertThrows(RuntimeException.class, () -> {
            documentoService.persistProcessingResult(result, testDocumento);
        });
    }

    // ============ CRUD Operation Tests ============

    @Test
    void testFindById_Success() {
        // Given
        when(documentoRepository.findById(1)).thenReturn(Optional.of(testDocumento));

        // When
        Optional<DocumentoDTO> result = documentoService.findById(1);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Test Document", result.get().getTitulo());
    }

    @Test
    void testFindById_NotFound() {
        // Given
        when(documentoRepository.findById(999)).thenReturn(Optional.empty());

        // When
        Optional<DocumentoDTO> result = documentoService.findById(999);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testFindByLibraryId() {
        // Given
        when(documentoRepository.findByBibliotecaId(1))
                .thenReturn(Collections.singletonList(testDocumento));

        // When
        List<DocumentoDTO> results = documentoService.findByLibraryId(1);

        // Then
        assertEquals(1, results.size());
        assertEquals("Test Document", results.get(0).getTitulo());
    }

    @Test
    void testFindActiveByLibraryId() {
        // Given
        when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(1))
                .thenReturn(Collections.singletonList(testDocumento));

        // When
        List<DocumentoDTO> results = documentoService.findActiveByLibraryId(1);

        // Then
        assertEquals(1, results.size());
        assertTrue(results.get(0).getFlagVigente());
    }

    @Test
    void testUpdateStatus() {
        // Given
        when(documentoRepository.findById(1)).thenReturn(Optional.of(testDocumento));
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        documentoService.updateStatus(1, false);

        // Then
        verify(documentoRepository).save(argThat(doc ->
                doc.getFlagVigente() == false
        ));
    }

    @Test
    void testUpdateStatus_DocumentNotFound() {
        // Given
        when(documentoRepository.findById(999)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            documentoService.updateStatus(999, false);
        });
    }

    @Test
    void testDelete() {
        // Given
        when(documentoRepository.findById(1)).thenReturn(Optional.of(testDocumento));
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        // When
        documentoService.delete(1);

        // Then
        verify(documentoRepository).save(argThat(doc ->
                doc.getFlagVigente() == false
        ));
    }

    // ============ Helper Method Tests ============

    @Test
    void testDeriveTitle_FromFilename() throws Exception {
        // Test is implicitly covered by uploadFromFile tests
        // This verifies the logic works correctly
        String fileName = "my-important-document.pdf";
        byte[] content = "content".getBytes();

        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentConverter.detectFormat(any(byte[].class))).thenReturn("application/pdf");
        when(documentConverter.convertToMarkdown(any(byte[].class), anyString()))
                .thenReturn("Content");
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        documentoService.uploadFromFile(fileName, content, 1, null);

        verify(documentoRepository).save(argThat(doc ->
                doc.getTitulo() != null &&
                !doc.getTitulo().equals("my-important-document.pdf")
        ));
    }

    @Test
    void testTokenEstimation() {
        // Given
        when(libraryService.findById(1)).thenReturn(Optional.of(testLibrary));
        when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

        String content = "word ".repeat(100); // 100 words

        // When
        documentoService.uploadFromText("Test", content, 1, null);

        // Then
        verify(documentoRepository).save(argThat(doc ->
                doc.getTokensTotal() != null &&
                doc.getTokensTotal() > 50 && // Should be around 133 tokens
                doc.getTokensTotal() < 200
        ));
    }
}
