package bor.tools.splitter;

import java.util.List;

/**
 * Interface for document summarization operations.
 */
public interface DocumentSummarizer {
    /**
     * Creates a summary of the document.
     * @param text Text to summarize
     * @param maxLength Maximum length of summary (optional)
     * @return Summarized text
     */
    String summarize(String text, int maxLength);

    /**
     * Creates a summary with specific instructions.
     * @param text Text to summarize
     * @param instructions Custom instructions for summarization
     * @param maxLength Maximum length of summary (optional)
     * @return Summarized text
     */
    String summarize(String text, String instructions, int maxLength);

    /**
     * Generates question-answer pairs from text.
     * @param text Source text
     * @param numQuestions Number of questions to generate
     * @return List of QA pairs
     */
    List<QuestionAnswer> generateQA(String text, int numQuestions);
}

