package bor.tools.simplerag.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import bor.tools.simplerag.entity.Chapter;
import bor.tools.simplerag.entity.DocChunk;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.enums.TipoEmbedding;

/**
 * Basic integration test for DocChunkJdbcRepository
 *
 * Tests database connectivity and basic CRUD operations.
 * Requires PostgreSQL with PGVector extension running.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocChunkJdbcRepositoryTest {

    @Autowired
    private DocChunkJdbcRepository embeddingRepository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    private Library testLibrary;
    private Documento testDocument;
    private Chapter testChapter;

    @BeforeEach
    void setUp() throws SQLException {
        // Clean up test data
        try {
            embeddingRepository.deleteAll();
        } catch (Exception e) {
            // Ignore if table is empty
        }
        chapterRepository.deleteAll();
        documentoRepository.deleteAll();
        libraryRepository.deleteAll();

        // Create test library
        testLibrary = new Library();
        testLibrary.setNome("Test Library");
        testLibrary.setAreaConhecimento("Testing");
        testLibrary.setPesoSemantico(0.6f);
        testLibrary.setPesoTextual(0.4f);
        testLibrary = libraryRepository.save(testLibrary);

        // Create test document
        testDocument = Documento.builder()
                .bibliotecaId(testLibrary.getId())
                .titulo("Test Document")
                .conteudoMarkdown("# Test Content")
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .build();
        testDocument = documentoRepository.save(testDocument);

        // Create test chapter
        testChapter = new Chapter();
        testChapter.setDocumentoId(testDocument.getId());
        testChapter.setTitulo("Test Chapter");
        testChapter.setConteudo("Chapter content");
        testChapter.setOrdemDoc(1);
        testChapter = chapterRepository.save(testChapter);
    }

    // ============ Database Connectivity Tests ============

    @Test
    void testDatabaseConnection() {
        // When - Test basic connection by querying for all embeddings
        List<DocChunk> embeddings = embeddingRepository.findAll();

        // Then - Should not throw exception
        assertNotNull(embeddings, "Database connection should work");
    }

    @Test
    void testPGVectorExtensionAvailable() {
        // When - Initialize repository (creates extensions)
        embeddingRepository.doOnce();

        // Then - Should not throw exception
        assertTrue(true, "PGVector extension should be available");
    }

    // ============ Basic CRUD Operations ============

    @Test
    void testSave_DocumentLevelEmbedding() throws SQLException {
        // Given - Document-level embedding
        DocChunk embedding = DocChunk.builder()
                .libraryId(testLibrary.getId())
                .documentoId(testDocument.getId())
                .tipoEmbedding(TipoEmbedding.DOCUMENTO)
                .texto("This is a test document embedding")
                .embeddingVector(createTestVector(768))
                .build();

        // When
        Integer savedId = embeddingRepository.save(embedding);

        // Then
        assertNotNull(savedId, "Saved embedding should have ID");
        assertTrue(savedId > 0, "ID should be positive");
        assertEquals(savedId, embedding.getId(), "Entity should be updated with ID");
    }

    @Test
    void testSave_ChapterLevelEmbedding() throws SQLException {
        // Given - Chapter-level embedding
        DocChunk embedding = DocChunk.builder()
                .libraryId(testLibrary.getId())
                .documentoId(testDocument.getId())
                .chapterId(testChapter.getId())
                .tipoEmbedding(TipoEmbedding.CAPITULO)
                .texto("This is a test chapter embedding")
                .embeddingVector(createTestVector(768))
                .build();

        // When
        Integer savedId = embeddingRepository.save(embedding);

        // Then
        assertNotNull(savedId, "Saved embedding should have ID");
        assertTrue(savedId > 0, "ID should be positive");
    }

    @Test
    void testSave_ChunkLevelEmbedding() throws SQLException {
        // Given - Chunk-level embedding
        DocChunk embedding = DocChunk.builder()
                .libraryId(testLibrary.getId())
                .documentoId(testDocument.getId())
                .chapterId(testChapter.getId())
                .tipoEmbedding(TipoEmbedding.TRECHO)
                .texto("This is a test chunk embedding")
                .orderChapter(1)
                .embeddingVector(createTestVector(768))
                .build();

        // When
        Integer savedId = embeddingRepository.save(embedding);

        // Then
        assertNotNull(savedId, "Saved embedding should have ID");
        assertTrue(savedId > 0, "ID should be positive");
    }

    @Test
    void testFindById() throws SQLException {
        // Given - Saved embedding
        DocChunk embedding = createAndSaveTestEmbedding();

        // When
        Optional<DocChunk> found = embeddingRepository.findById(embedding.getId());

        // Then
        assertTrue(found.isPresent(), "Should find saved embedding");
        assertEquals(embedding.getId(), found.get().getId());
        assertEquals(embedding.getTexto(), found.get().getTexto());
        assertEquals(embedding.getLibraryId(), found.get().getLibraryId());
    }

    @Test
    void testFindByDocumentoId() throws SQLException {
        // Given - Multiple embeddings for same document
        createAndSaveTestEmbedding();
        createAndSaveTestEmbedding();

        // When
        List<DocChunk> embeddings = embeddingRepository.findByDocumentoId(testDocument.getId());

        // Then
        assertNotNull(embeddings);
        assertEquals(2, embeddings.size(), "Should find all embeddings for document");
        assertTrue(embeddings.stream().allMatch(e ->
            e.getDocumentoId().equals(testDocument.getId())
        ));
    }

    @Test
    void testFindByBibliotecaId() throws SQLException {
        // Given - Embedding in library
        createAndSaveTestEmbedding();

        // When
        List<DocChunk> embeddings = embeddingRepository.findByBibliotecaId(testLibrary.getId());

        // Then
        assertNotNull(embeddings);
        assertTrue(embeddings.size() > 0, "Should find embeddings in library");
        assertTrue(embeddings.stream().allMatch(e ->
            e.getLibraryId().equals(testLibrary.getId())
        ));
    }

    @Test
    void testFindByCapituloId() throws SQLException {
        // Given - Chapter-level embedding
        DocChunk embedding = DocChunk.builder()
                .libraryId(testLibrary.getId())
                .documentoId(testDocument.getId())
                .chapterId(testChapter.getId())
                .tipoEmbedding(TipoEmbedding.CAPITULO)
                .texto("Chapter embedding")
                .embeddingVector(createTestVector(768))
                .build();
        embeddingRepository.save(embedding);

        // When
        List<DocChunk> embeddings = embeddingRepository.findByCapituloId(testChapter.getId());

        // Then
        assertNotNull(embeddings);
        assertTrue(embeddings.size() > 0, "Should find embeddings for chapter");
        assertTrue(embeddings.stream().allMatch(e ->
            e.getChapterId().equals(testChapter.getId())
        ));
    }

    @Test
    void testFindByTipoEmbedding() throws SQLException {
        // Given - Embeddings of different types
        DocChunk docEmbedding = DocChunk.builder()
                .libraryId(testLibrary.getId())
                .documentoId(testDocument.getId())
                .tipoEmbedding(TipoEmbedding.DOCUMENTO)
                .texto("Document embedding")
                .embeddingVector(createTestVector(768))
                .build();
        embeddingRepository.save(docEmbedding);

        DocChunk chapterEmbedding = DocChunk.builder()
                .libraryId(testLibrary.getId())
                .documentoId(testDocument.getId())
                .chapterId(testChapter.getId())
                .tipoEmbedding(TipoEmbedding.CAPITULO)
                .texto("Chapter embedding")
                .embeddingVector(createTestVector(768))
                .build();
        embeddingRepository.save(chapterEmbedding);

        // When
        List<DocChunk> docEmbeddings = embeddingRepository.findByTipoEmbedding(TipoEmbedding.DOCUMENTO);
        List<DocChunk> chapterEmbeddings = embeddingRepository.findByTipoEmbedding(TipoEmbedding.CAPITULO);

        // Then
        assertTrue(docEmbeddings.size() > 0, "Should find document embeddings");
        assertTrue(chapterEmbeddings.size() > 0, "Should find chapter embeddings");
        assertTrue(docEmbeddings.stream().allMatch(e ->
            e.getTipoEmbedding() == TipoEmbedding.DOCUMENTO
        ));
        assertTrue(chapterEmbeddings.stream().allMatch(e ->
            e.getTipoEmbedding() == TipoEmbedding.CAPITULO
        ));
    }

    @Test
    void testUpdate() throws SQLException {
        // Given - Saved embedding
        DocChunk embedding = createAndSaveTestEmbedding();
        String newText = "Updated text content";
        embedding.setTexto(newText);

        // When
        int updated = embeddingRepository.update(embedding);

        // Then
        assertEquals(1, updated, "Should update one record");

        // Verify update
        Optional<DocChunk> found = embeddingRepository.findById(embedding.getId());
        assertTrue(found.isPresent());
        assertEquals(newText, found.get().getTexto());
    }

    @Test
    void testDelete() throws SQLException {
        // Given - Saved embedding
        DocChunk embedding = createAndSaveTestEmbedding();
        Integer embeddingId = embedding.getId();

        // When
        int deleted = embeddingRepository.delete(embeddingId);

        // Then
        assertEquals(1, deleted, "Should delete one record");

        // Verify deletion
        Optional<DocChunk> found = embeddingRepository.findById(embeddingId);
        assertFalse(found.isPresent(), "Deleted embedding should not be found");
    }

    @Test
    void testFindAll() throws SQLException {
        // Given - Multiple embeddings
        createAndSaveTestEmbedding();
        createAndSaveTestEmbedding();
        createAndSaveTestEmbedding();

        // When
        List<DocChunk> all = embeddingRepository.findAll();

        // Then
        assertNotNull(all);
        assertTrue(all.size() >= 3, "Should find at least 3 embeddings");
    }

    // ============ Search Operations Tests ============

    @Test
    void testPesquisaSemantica() throws SQLException {
        // Given - Saved embedding with vector
        DocChunk embedding = createAndSaveTestEmbedding();
        float[] queryVector = createTestVector(768);

        // When
        List<DocChunk> results = embeddingRepository.pesquisaSemantica(
                queryVector,
                new Integer[]{testLibrary.getId()},
                10
        );

        // Then
        assertNotNull(results, "Search results should not be null");
        // Results may be empty if vectors are too different, but should not throw exception
    }

    @Test
    void testPesquisaTextual() throws SQLException {
        // Given - Saved embedding with text
        DocChunk embedding = DocChunk.builder()
                .libraryId(testLibrary.getId())
                .documentoId(testDocument.getId())
                .tipoEmbedding(TipoEmbedding.DOCUMENTO)
                .texto("This document discusses Java programming and Spring Framework")
                .embeddingVector(createTestVector(768))
                .build();
        embeddingRepository.save(embedding);

        // When
        List<DocChunk> results = embeddingRepository.pesquisaTextual(
                "Java Spring",
                new Integer[]{testLibrary.getId()},
                10
        );

        // Then
        assertNotNull(results, "Search results should not be null");
        // Note: Results depend on PostgreSQL full-text search configuration
    }

    @Test
    void testPesquisaHibrida() throws SQLException {
        // Given - Saved embedding
        DocChunk embedding = DocChunk.builder()
                .libraryId(testLibrary.getId())
                .documentoId(testDocument.getId())
                .tipoEmbedding(TipoEmbedding.DOCUMENTO)
                .texto("This document discusses hybrid search capabilities")
                .embeddingVector(createTestVector(768))
                .build();
        embeddingRepository.save(embedding);

        float[] queryVector = createTestVector(768);

        // When
        List<DocChunk> results = embeddingRepository.pesquisaHibrida(
                queryVector,
                "hybrid search",
                new Integer[]{testLibrary.getId()},
                10,
                0.6f,
                0.4f
        );

        // Then
        assertNotNull(results, "Search results should not be null");
    }

    // ============ Helper Methods ============

    private DocChunk createAndSaveTestEmbedding() throws SQLException {
        DocChunk embedding = DocChunk.builder()
                .libraryId(testLibrary.getId())
                .documentoId(testDocument.getId())
                .tipoEmbedding(TipoEmbedding.DOCUMENTO)
                .texto("Test embedding text content")
                .embeddingVector(createTestVector(768))
                .build();

        embeddingRepository.save(embedding);
        return embedding;
    }

    /**
     * Creates a test vector with specified dimension
     * Values are normalized to unit length
     */
    private float[] createTestVector(int dimension) {
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) Math.random();
        }

        // Normalize to unit length
        float sumSquares = 0f;
        for (float v : vector) {
            sumSquares += v * v;
        }
        float magnitude = (float) Math.sqrt(sumSquares);

        for (int i = 0; i < dimension; i++) {
            vector[i] = vector[i] / magnitude;
        }

        return vector;
    }
}
