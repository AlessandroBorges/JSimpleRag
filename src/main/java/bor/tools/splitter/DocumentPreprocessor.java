package bor.tools.splitter;

/**
 * Interface for document preprocessing operations.
 */
public interface DocumentPreprocessor {
    /**
     * Removes repetitions from text.
     * @param text Input text
     * @return Processed text
     */
    String removeRepetitions(String text);

    /**
     * Splits text into paragraphs.
     * @param text Input text
     * @return Array of paragraphs
     */
    String[] splitIntoParagraphs(String text);

    /**
     * Splits text into sentences.
     * @param text Input text
     * @param maxChars Maximum characters per chunk
     * @return Array of sentences
     */
    String[] splitIntoSentences(String text, int maxChars);
}