package bor.tools.splitter;

import java.net.URL;
import java.util.List;

import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.entity.*;

/**
 * Core interface for document splitting operations.
 */
public interface DocumentSplitter {
    /**
     * Splits a document into logical parts.
     * @param document The document to split
     * @return List of document parts
     */
    List<ChapterDTO> splitDocumento(DocumentoDTO document);

    /**
     * Loads a document from a URL.
     * @param url Document URL
     * @param docStub Document's stub
     * @return Loaded document
     */
    DocumentoDTO carregaDocumento(URL url, DocumentoDTO docStub) throws Exception;

    /**
     * Loads a document from a string path.
     * @param path Document path
     * @param docStub Document's stub
     * @return Loaded document
     */
    DocumentoDTO carregaDocumento(String path, DocumentoDTO docStub) throws Exception;
}
