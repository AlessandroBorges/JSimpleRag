package bor.tools.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bor.tools.utils.RAGUtil;
import superag.retriever.model.DocParte;


/**
 * Split content into chapters, handling both Markdown and plain text
 */
public class ContentSplitter {
    private static final int MIN_TOKENS = 500;
    private static final int IDEAL_TOKENS = 4000;
    private static final int MAX_TOKENS = 16000;

    /**
     * Split content into chapters, handling both Markdown and plain text
     * @param content The content to split
     * @param isMarkdown Whether the content is in Markdown format
     * @return A list of chapters with titles and content
     * @see Chapter
     */
    public List<DocParte> splitContent(String content) {
    	boolean isMarkDown = RAGUtil.isMarkdown(content);
    	return splitContent(content, isMarkDown);
    }

    /**
     * Split content into chapters, handling both Markdown and plain text
     * @param content The content to split
     * @param isMarkdown Whether the content is in Markdown format
     * @return A list of chapters with titles and content
     * @see Chapter
     */
    public List<DocParte> splitContent(String content, boolean isMarkdown) {
        List<DocParte> chapters = new ArrayList<>();

        if (isMarkdown) {
            // Handle Markdown content
            chapters = splitMarkdown(content);
        } else {
            // Handle plain text
            chapters = splitPlainText(content);
        }

        // Post-process chapters to ensure size constraints
        return optimizeChapterSizes(chapters);
    }

    /**
     * Split Markdown content using headers as primary split points
     */
    private List<DocParte> splitMarkdown(String content) {
        List<DocParte> chapters = new ArrayList<>();

        // Regex for Markdown headers (both # and === or --- style)
        Pattern headerPattern = Pattern.compile(
            "^(#{1,6}\\s+.+$)|^(\\S.*)\\s*?\\n(={3,}|-{3,})$",
            Pattern.MULTILINE
        );

        Matcher matcher = headerPattern.matcher(content);
        int lastPos = 0;
        String currentTitle = "Introduction";

        while (matcher.find()) {
            // Extract content between headers
            String sectionContent = content.substring(lastPos, matcher.start()).trim();

            if (!sectionContent.isEmpty()) {
                chapters.add(new DocParte(currentTitle, sectionContent));
            }

            // Update for next iteration
            currentTitle = matcher.group().replace("#", "").trim();
            lastPos = matcher.end();
        }

        // Add final section
        String finalContent = content.substring(lastPos).trim();
        if (!finalContent.isEmpty()) {
            chapters.add(new DocParte(currentTitle, finalContent));
        }

        return chapters;
    }

    /**
     * Split plain text using paragraph breaks and size-based chunking
     */
    private List<DocParte> splitPlainText(String content) {
        List<DocParte> chapters = new ArrayList<>();

        // Split on double line breaks (paragraphs)
        String[] paragraphs = content.split("\\n\\s*\\n");

        StringBuilder currentChapter = new StringBuilder();
        int currentTokenCount = 0;
        int chapterNumber = 1;

        for (String paragraph : paragraphs) {
            int paragraphTokens = estimateTokenCount(paragraph);

            if (currentTokenCount + paragraphTokens > IDEAL_TOKENS &&
                currentTokenCount >= MIN_TOKENS) {
                // Create new chapter if we've reached ideal size
                chapters.add(new DocParte(
                    "Section " + chapterNumber++,
                    currentChapter.toString().trim()
                ));
                currentChapter = new StringBuilder();
                currentTokenCount = 0;
            }

            currentChapter.append(paragraph).append("\n\n");
            currentTokenCount += paragraphTokens;
        }

        // Add final chapter
        if (currentChapter.length() > 0) {
            chapters.add(new DocParte(
                "Section " + chapterNumber,
                currentChapter.toString().trim()
            ));
        }

        return chapters;
    }

    /**
     * Optimize chapter sizes by merging or splitting as needed
     */
    private List<DocParte> optimizeChapterSizes(List<DocParte> chapters) {
        List<DocParte> optimizedChapters = new ArrayList<>();

        for (int i = 0; i < chapters.size(); i++) {
        	DocParte current = chapters.get(i);
            int tokenCount = estimateTokenCount(current.getTexto());

            if (tokenCount < MIN_TOKENS && i < chapters.size() - 1) {
                // Merge with next chapter if too small
            	DocParte next = chapters.get(i + 1);
                optimizedChapters.add(new DocParte(
                    current.getTitulo(),
                    current.getTexto() + "\n\n" + next.getTexto()
                ));
                i++; // Skip next chapter since we merged it
            } else if (tokenCount > MAX_TOKENS) {
                // Split large chapters
                optimizedChapters.addAll(splitLargeChapter(current));
            } else {
                optimizedChapters.add(current);
            }
        }

        return optimizedChapters;
    }

    /**
     * Split a large chapter into smaller ones
     */
    private List<DocParte> splitLargeChapter(DocParte chapter) {
        List<DocParte> subChapters = new ArrayList<>();
        String[] paragraphs = chapter.getTexto().split("\\n\\s*\\n");

        StringBuilder currentContent = new StringBuilder();
        int currentTokens = 0;
        int partNumber = 1;

        for (String paragraph : paragraphs) {
            int paragraphTokens = estimateTokenCount(paragraph);

            if (currentTokens + paragraphTokens > IDEAL_TOKENS &&
                currentTokens >= MIN_TOKENS) {
                subChapters.add(new DocParte(
                    chapter.getTitulo() + " (Part " + partNumber++ + ")",
                    currentContent.toString().trim()
                ));
                currentContent = new StringBuilder();
                currentTokens = 0;
            }

            currentContent.append(paragraph).append("\n\n");
            currentTokens += paragraphTokens;
        }

        if (currentContent.length() > 0) {
            subChapters.add(new DocParte(
                chapter.getTitulo() + (partNumber > 1 ? " (Part " + partNumber + ")" : ""),
                currentContent.toString().trim()
            ));
        }
        return subChapters;
    }

    /**
     * Rough estimation of token count (can be refined based on your tokenizer)
     */
    private int estimateTokenCount(String text) {
        // Simple estimation: words / 0.75 (assuming average token is 0.75 words)
        return (int)(text.split("\\s+").length / 0.75);
    }
}

