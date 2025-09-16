package bor.tools.splitter;

import superag.retriever.model.Documento;

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
    Documento translateDocument(Documento document, Lang source, Lang target);
}