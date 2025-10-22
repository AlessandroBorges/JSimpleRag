package bor.tools.splitter;

import java.net.URL;
import java.util.List;

import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;

/**
 * Core interface for document splitting operations. <br>
 *
 * docStub can be null when loading a document. It represents a partial document
 * and maybe used to pass metadata or context.
 *
 * Uses DocumentoWithAssociationDTO since splitters need access to library info
 * and chapter associations for proper document processing.
 */
public interface DocumentSplitter {
    /**
     * Splits a document into logical parts.
     * @param document The document to split
     * @return List of document parts
     */
    List<ChapterDTO> splitDocumento(DocumentoWithAssociationDTO document);

    /**
     * Loads a document from a URL.
     * @param url Document URL
     * @param docStub Document's stub. Can be null
     * @return Loaded document
     */
    DocumentoWithAssociationDTO carregaDocumento(URL url, DocumentoWithAssociationDTO docStub) throws Exception;

    /**
     * Loads a document from a string path.
     * @param path Document path
     * @param docStub Document's stub. Can be null
     * @return Loaded document
     */
    DocumentoWithAssociationDTO carregaDocumento(String path, DocumentoWithAssociationDTO docStub) throws Exception;
}
