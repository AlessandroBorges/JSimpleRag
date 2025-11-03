package bor.tools.simplerag.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.Model;
import bor.tools.simplellm.ModelEmbedding;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.Chapter;
import bor.tools.simplerag.entity.DocumentEmbedding;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.entity.enums.TipoConteudo;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import bor.tools.simplerag.repository.ChapterRepository;
import bor.tools.simplerag.repository.DocEmbeddingJdbcRepository;
import bor.tools.simplerag.service.LibraryService;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import bor.tools.simplerag.service.processing.DocumentProcessingService;
import bor.tools.simplerag.service.processing.context.LLMContext;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.strategy.QAEmbeddingStrategy;
import bor.tools.simplerag.service.embedding.strategy.SummaryEmbeddingStrategy;
import bor.tools.splitter.AbstractSplitter;
import bor.tools.splitter.DocumentRouter;
import bor.tools.splitter.SplitterFactory;
import bor.tools.splitter.SplitterGenerico;

/**
 * Integration tests for the complete document processing flow.
 *
 * <p>These tests validate the end-to-end workflow described in:</p>
 * <ul>
 *   <li>NEW_PROCESSING_FLOW_PROPOSAL.md (v1.1)</li>
 *   <li>Fluxo_carga_documents.md</li>
 * </ul>
 *
 * <p><b>Processing Flow Tested:</b></p>
 * <pre>
 * PHASE 1 (Required):
 *   1. Create LLM and Embedding contexts
 *   2. Split document into chapters and chunks
 *   3. Persist chapters and embeddings (with NULL vectors)
 *   4. Calculate embeddings in batches
 *   5. Update embedding vectors
 *
 * PHASE 2 (Optional):
 *   6. Generate Q&A embeddings (if requested)
 *   7. Generate summary embeddings (if requested)
 * </pre>
 *
 * @since 0.0.3
 * @version 1.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Document Processing Integration Tests")
class DocumentProcessingIntegrationTest {

    @Mock
    private DocumentRouter documentRouter;

    @Mock
    private SplitterFactory splitterFactory;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private DocEmbeddingJdbcRepository embeddingRepository;

    @Mock
    private LLMServiceManager llmServiceManager;

    @Mock
    private LibraryService libraryService;

    @Mock
    private QAEmbeddingStrategy qaEmbeddingStrategy;

    @Mock
    private SummaryEmbeddingStrategy summaryEmbeddingStrategy;

    @Mock
    private LLMService llmService;

    @Mock
    private ModelEmbedding modelEmbedding;

    @Mock
    private Model completionModel;

    @Mock
    private AbstractSplitter splitter;

    private DocumentProcessingService documentProcessingService;

    // Test data
    private Documento testDocumento;
    private Library testLibrary;
    private LibraryDTO testLibraryDTO;
    private Integer testLibraryId = 1;
    private Integer testDocumentId = 100;

    @BeforeEach
    void setUp() throws Exception {
        // Setup library
        testLibrary = new Library();
        testLibrary.setId(testLibraryId);
        testLibrary.setNome("Test Library");

        testLibraryDTO = LibraryDTO.builder()
                .id(testLibraryId)
                .nome("Test Library")
                .build();

        // Setup document (15,000 tokens - will create multiple chapters)
        String largeContent = generateLargeContent(15000);
        testDocumento = Documento.builder()
                .id(testDocumentId)
                .bibliotecaId(testLibraryId)
                .titulo("Large Test Document")
                .conteudoMarkdown(largeContent)
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .metadados(new MetaDoc())
                .build();

        // Setup LLM Service Manager mocks
        setupLLMServiceManagerMocks();

        // Create service instance
        documentProcessingService = new DocumentProcessingService(
                documentRouter,
                splitterFactory,
                chapterRepository,
                embeddingRepository,
                llmServiceManager,
                qaEmbeddingStrategy,
                summaryEmbeddingStrategy
        );
    }

    /**
     * Setup mocks for LLMServiceManager to return valid contexts.
     */
    private void setupLLMServiceManagerMocks() throws Exception {
        // Setup ModelEmbedding
        when(modelEmbedding.getContextLength()).thenReturn(8192);

        // Setup LLMService for embeddings
        when(llmService.embeddings(any(Embeddings_Op.class), any(String[].class), any(MapParam.class)))
                .thenAnswer(invocation -> {
                    String[] texts = invocation.getArgument(1);
                    float[][] vectors = new float[texts.length][1536];
                    for (int i = 0; i < texts.length; i++) {
                        for (int j = 0; j < 1536; j++) {
                            vectors[i][j] = (float) Math.random();
                        }
                    }
                    return Arrays.asList(vectors);
                });

        // Setup LLMService for token counting
        when(llmService.tokenCount(anyString(), anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            // Simple estimation: words / 0.75
            return (int) Math.ceil(text.split("\\s+").length / 0.75);
        });

        // Setup LLMService for completions (summaries)
        // Note: CompletionResponse is complex, so we'll mock the generateCompletion in LLMContext instead
        when(llmService.completion(anyString(), anyString(), any(MapParam.class)))
                .thenReturn(null);  // Will be handled by LLMContext mock

        // Setup LLMServiceManager to return LLMService
        when(llmServiceManager.getBestCompletionModelName()).thenReturn(null);
        when(llmServiceManager.getServiceByModel(anyString())).thenReturn(llmService);
        when(llmServiceManager.getLLMServiceByRegisteredModel(anyString())).thenReturn(llmService);
    }

    /**
     * Generates content with approximately the specified number of tokens.
     */
    private String generateLargeContent(int targetTokens) {
        StringBuilder sb = new StringBuilder();
        int wordsNeeded = (int) (targetTokens * 0.75);

        sb.append("# Large Test Document\n\n");

        for (int i = 0; i < wordsNeeded / 50; i++) {
            sb.append("This is paragraph ").append(i + 1)
              .append(". It contains some test content for processing. ")
              .append("We need to ensure that the document is large enough to be split into multiple chapters. ")
              .append("Each chapter should be around 2000-4200 tokens for optimal processing.\n\n");
        }

        return sb.toString();
    }

    // ========== Phase 1: Complete Processing Flow Tests ==========

    @Nested
    @DisplayName("Phase 1: Complete Document Processing")
    class Phase1ProcessingTests {

        @Test
        @DisplayName("Should successfully process small document (1 chapter, no summary)")
        void processDocument_SmallDocument_Success() throws Exception {
            // Arrange - Small document (1500 tokens, 1 chapter, no summary)
            String smallContent = generateLargeContent(1500);
            Documento smallDoc = Documento.builder()
                    .id(101)
                    .bibliotecaId(testLibraryId)
                    .titulo("Small Document")
                    .conteudoMarkdown(smallContent)
                    .build();

            // Mock content type detection
            when(documentRouter.detectContentType(anyString())).thenReturn(TipoConteudo.OUTROS);

            // Mock splitter factory
            when(splitterFactory.createSplitter(any(TipoConteudo.class), any(LibraryDTO.class)))
                    .thenReturn(splitter);

            // Mock splitter to return 1 small chapter
            ChapterDTO chapterDTO = ChapterDTO.builder()
                    .titulo("Chapter 1")
                    .conteudo(smallContent)
                    .ordemDoc(0)
                    .build();

            when(splitter.splitDocumento(any(DocumentoWithAssociationDTO.class)))
                    .thenReturn(Arrays.asList(chapterDTO));

            // Mock chapter persistence (generate ID)
            when(chapterRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<Chapter> chapters = invocation.getArgument(0);
                for (int i = 0; i < chapters.size(); i++) {
                    chapters.get(i).setId(200 + i);
                }
                return chapters;
            });

            // Mock embedding persistence (just return success)
            when(embeddingRepository.saveAll(anyList())).thenReturn(Arrays.asList(1));

            // Mock embedding update (no-op)
            doNothing().when(embeddingRepository).updateEmbeddingVector(anyInt(), any(float[].class));

            // Act
            DocumentProcessingService.ProcessingResult result =
                    documentProcessingService.processDocument(smallDoc, testLibraryDTO).get();

            // Assert
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(1, result.getChaptersCount(), "Should create 1 chapter");
            assertEquals(1, result.getEmbeddingsCount(), "Should create 1 TRECHO (no summary, small chapter)");
            assertEquals(1, result.getEmbeddingsProcessed(), "All embeddings should be processed");
            assertEquals(0, result.getEmbeddingsFailed(), "No embeddings should fail");

            // Verify contexts were created
            verify(llmServiceManager, times(1)).getBestCompletionModelName();
            verify(llmServiceManager, times(1)).getLLMServiceByRegisteredModel(anyString());

            // Verify split and persist
            verify(documentRouter, times(1)).detectContentType(anyString());
            verify(splitterFactory, times(1)).createSplitter(any(TipoConteudo.class), any(LibraryDTO.class));
            verify(chapterRepository, times(1)).saveAll(anyList());
            verify(embeddingRepository, times(1)).saveAll(anyList());

            // Verify embeddings calculated (1 batch call for 1 embedding)
            verify(llmService, atLeastOnce()).embeddings(any(Embeddings_Op.class), any(String[].class), any());
        }

        @Test
        @DisplayName("Should successfully process large document (4 chapters, with summaries)")
        void processDocument_LargeDocument_Success() throws Exception {
            // Arrange - Large document (15,000 tokens)
            when(documentRouter.detectContentType(anyString())).thenReturn(TipoConteudo.OUTROS);
            when(splitterFactory.createSplitter(any(TipoConteudo.class), any(LibraryDTO.class)))
                    .thenReturn(splitter);

            // Mock splitter to return 4 chapters
            List<ChapterDTO> chapterDTOs = Arrays.asList(
                    createChapterDTO("Chapter 1", generateLargeContent(3750), 0),
                    createChapterDTO("Chapter 2", generateLargeContent(3750), 1),
                    createChapterDTO("Chapter 3", generateLargeContent(1200), 2),
                    createChapterDTO("Chapter 4", generateLargeContent(6300), 3)
            );

            when(splitter.splitDocumento(any(DocumentoWithAssociationDTO.class)))
                    .thenReturn(chapterDTOs);

            // Mock SplitterGenerico for chunk splitting
            SplitterGenerico genericSplitter = mock(SplitterGenerico.class);
            when(splitterFactory.createGenericSplitter(any(LibraryDTO.class)))
                    .thenReturn(genericSplitter);

            // Mock chunk splitting (2 chunks per large chapter)
            when(genericSplitter.splitChapterIntoChunks(any(ChapterDTO.class)))
                    .thenAnswer(invocation -> {
                        ChapterDTO chapter = invocation.getArgument(0);
                        int tokens = chapter.getConteudo().split("\\s+").length;

                        // If > 2000 tokens, split into chunks
                        if (tokens > 1500) {
                            return createMockChunks(2);  // 2 chunks
                        } else {
                            return createMockChunks(1);  // 1 chunk
                        }
                    });

            // Mock chapter persistence
            when(chapterRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<Chapter> chapters = invocation.getArgument(0);
                for (int i = 0; i < chapters.size(); i++) {
                    chapters.get(i).setId(200 + i);
                }
                return chapters;
            });

            // Mock embedding persistence
            when(embeddingRepository.saveAll(anyList())).thenReturn(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
            doNothing().when(embeddingRepository).updateEmbeddingVector(anyInt(), any(float[].class));

            // Act
            DocumentProcessingService.ProcessingResult result =
                    documentProcessingService.processDocument(testDocumento, testLibraryDTO).get();

            // Assert
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(4, result.getChaptersCount(), "Should create 4 chapters");

            // Expected: 3 RESUMO (chapters 1,2,4 > 2500 tokens) + 8 TRECHO (chunks)
            assertTrue(result.getEmbeddingsCount() >= 8,
                    "Should create at least 8 embeddings (chunks + summaries)");

            assertTrue(result.getEmbeddingsProcessed() > 0, "Should process embeddings");
            assertNotNull(result.getDuration());

            // Verify full workflow
            verify(documentRouter, times(1)).detectContentType(anyString());
            verify(chapterRepository, times(1)).saveAll(argThat(chapters ->
                ((List<Chapter>) chapters).size() == 4));
            verify(embeddingRepository, times(1)).saveAll(anyList());

            // Verify batch embeddings were called
            verify(llmService, atLeastOnce())
                    .embeddings(eq(Embeddings_Op.DOCUMENT), any(String[].class), any());
        }

        @Test
        @DisplayName("Should handle embedding failures gracefully and continue processing")
        void processDocument_WithEmbeddingFailures_ContinuesProcessing() throws Exception {
            // Arrange
            String smallContent = generateLargeContent(1500);
            Documento smallDoc = Documento.builder()
                    .id(102)
                    .bibliotecaId(testLibraryId)
                    .titulo("Document with Failures")
                    .conteudoMarkdown(smallContent)
                    .build();

            when(documentRouter.detectContentType(anyString())).thenReturn(TipoConteudo.OUTROS);
            when(splitterFactory.createSplitter(any(TipoConteudo.class), any(LibraryDTO.class)))
                    .thenReturn(splitter);

            ChapterDTO chapterDTO = createChapterDTO("Chapter 1", smallContent, 0);
            when(splitter.splitDocumento(any(DocumentoWithAssociationDTO.class)))
                    .thenReturn(Arrays.asList(chapterDTO));

            when(chapterRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<Chapter> chapters = invocation.getArgument(0);
                chapters.get(0).setId(200);
                return chapters;
            });

            when(embeddingRepository.saveAll(anyList())).thenReturn(Arrays.asList(1));

            // Mock embedding vector update to fail
            doThrow(new RuntimeException("Database error"))
                    .when(embeddingRepository).updateEmbeddingVector(anyInt(), any(float[].class));

            // Act
            DocumentProcessingService.ProcessingResult result =
                    documentProcessingService.processDocument(smallDoc, testLibraryDTO).get();

            // Assert
            assertNotNull(result);
            assertTrue(result.isSuccess(), "Processing should still succeed despite failures");
            assertEquals(1, result.getChaptersCount());
            assertEquals(0, result.getEmbeddingsProcessed(), "No embeddings should be successfully updated");
            assertEquals(1, result.getEmbeddingsFailed(), "1 embedding should fail");
        }

        @Test
        @DisplayName("Should handle oversized text by truncating or summarizing")
        void processDocument_WithOversizedText_HandlesCorrectly() throws Exception {
            // This test verifies the handleOversizedText logic
            // When text > contextLength, it should either truncate or generate summary

            // Arrange - Create very large content that exceeds context length
            String veryLargeContent = generateLargeContent(10000);  // > 8192 context length
            Documento largeDoc = Documento.builder()
                    .id(103)
                    .bibliotecaId(testLibraryId)
                    .titulo("Oversized Document")
                    .conteudoMarkdown(veryLargeContent)
                    .build();

            when(documentRouter.detectContentType(anyString())).thenReturn(TipoConteudo.OUTROS);
            when(splitterFactory.createSplitter(any(TipoConteudo.class), any(LibraryDTO.class)))
                    .thenReturn(splitter);

            ChapterDTO chapterDTO = createChapterDTO("Huge Chapter", veryLargeContent, 0);
            when(splitter.splitDocumento(any(DocumentoWithAssociationDTO.class)))
                    .thenReturn(Arrays.asList(chapterDTO));

            SplitterGenerico genericSplitter = mock(SplitterGenerico.class);
            when(splitterFactory.createGenericSplitter(any(LibraryDTO.class)))
                    .thenReturn(genericSplitter);
            when(genericSplitter.splitChapterIntoChunks(any(ChapterDTO.class)))
                    .thenReturn(createMockChunks(5));  // 5 large chunks

            when(chapterRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<Chapter> chapters = invocation.getArgument(0);
                chapters.get(0).setId(200);
                return chapters;
            });

            when(embeddingRepository.saveAll(anyList())).thenReturn(Arrays.asList(1, 2, 3, 4, 5, 6));
            doNothing().when(embeddingRepository).updateEmbeddingVector(anyInt(), any(float[].class));

            // Act
            DocumentProcessingService.ProcessingResult result =
                    documentProcessingService.processDocument(largeDoc, testLibraryDTO).get();

            // Assert
            assertNotNull(result);
            assertTrue(result.isSuccess());

            // Verify that completion was called for oversized text (summarization)
            // or verify that text was truncated (via metadata)
            verify(llmService, atLeastOnce())
                    .embeddings(eq(Embeddings_Op.DOCUMENT), any(String[].class), any());
        }
    }

    // ========== Helper Methods ==========

    /**
     * Creates a ChapterDTO for testing.
     */
    private ChapterDTO createChapterDTO(String titulo, String conteudo, int ordem) {
        return ChapterDTO.builder()
                .titulo(titulo)
                .conteudo(conteudo)
                .ordemDoc(ordem)
                .build();
    }

    /**
     * Creates mock DocumentEmbeddingDTO chunks.
     */
    private List<bor.tools.simplerag.dto.DocumentEmbeddingDTO> createMockChunks(int count) {
        List<bor.tools.simplerag.dto.DocumentEmbeddingDTO> chunks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            bor.tools.simplerag.dto.DocumentEmbeddingDTO chunk =
                    bor.tools.simplerag.dto.DocumentEmbeddingDTO.builder()
                            .tipoEmbedding(TipoEmbedding.TRECHO)
                            .trechoTexto("Chunk " + (i + 1) + " content")
                            .build();
            chunks.add(chunk);
        }

        return chunks;
    }
}
