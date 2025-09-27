package bor.tools.splitter;

import bor.tools.simplerag.dto.DocumentoDTO;


/**
 * Service interface for translation operations.
 */
public interface TranslationService {
    /**
     * Translates a document to target language.
     * @param document Document to translate
     * @param source Source language
     * @param target Target language
     * @return Translated document
     */
    DocumentoDTO translateDocument(DocumentoDTO document, Lang target);
}