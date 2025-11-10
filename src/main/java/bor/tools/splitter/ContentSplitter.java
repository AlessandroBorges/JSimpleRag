package bor.tools.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocChunkDTO;
import bor.tools.simplerag.entity.Chapter;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import bor.tools.utils.RagUtils;



/**
 * Split content into chapters, handling both Markdown and plain text.
 *
 * <p><b>DEPRECATED:</b> This class has been deprecated in favor of {@link SplitterGenerico}
 * which provides the same functionality with improvements:</p>
 * <ul>
 *   <li>Real token counting via {@link bor.tools.simplellm.LLMProvider} instead of heuristic estimation</li>
 *   <li>Integration with {@link SplitterFactory} pattern for better dependency management</li>
 *   <li>Elimination of circular dependency with SplitterGenerico</li>
 *   <li>Consistent behavior across all splitter implementations</li>
 * </ul>
 *
 * <h3>Migration Guide:</h3>
 * <table border="1">
 *   <tr>
 *     <th>Old Code (ContentSplitter)</th>
 *     <th>New Code (SplitterGenerico via Factory)</th>
 *   </tr>
 *   <tr>
 *     <td>
 *       <pre>
 * &#64;Autowired
 * private ContentSplitter contentSplitter;
 *
 * List&lt;DocChunkDTO&gt; chunks =
 *     contentSplitter.splitContent(chapter);
 *       </pre>
 *     </td>
 *     <td>
 *       <pre>
 * &#64;Autowired
 * private SplitterFactory splitterFactory;
 *
 * SplitterGenerico splitter =
 *     splitterFactory.createGenericSplitter(library);
 * List&lt;DocChunkDTO&gt; chunks =
 *     splitter.splitChapterIntoChunks(chapter);
 *       </pre>
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>
 *       <pre>
 * List&lt;ChapterDTO&gt; chapters =
 *     contentSplitter.splitContent(text, false);
 *       </pre>
 *     </td>
 *     <td>
 *       <pre>
 * // Use DocumentProcessingService or
 * // SplitterGenerico.splitBySize() for
 * // document-level splitting
 *       </pre>
 *     </td>
 *   </tr>
 * </table>
 *
 * @deprecated since 0.0.3, scheduled for removal in 0.1.0.
 *             Use {@link SplitterGenerico#splitChapterIntoChunks(ChapterDTO)} instead.
 * @see SplitterGenerico#splitChapterIntoChunks(ChapterDTO)
 * @see SplitterFactory
 */
@Deprecated(since = "0.0.3", forRemoval = true)
@Component
public class ContentSplitter {
    
    /**
     * Chunck ideal de tokens.
     */
    protected static final int IDEAL_TOKENS = 2000;

    /**
     * Número máximo de tokens em um chunk.
     */
    protected static final int MAX_TOKENS = 4096;

    /**
     * Número mínimo de tokens em um chunk.
     */
    protected static final int MIN_TOKENS = 512;
    
    /**
     * Número máximo de tokens em um capitulo. 
     * Default é 16Kt
     */
    protected static final int CHAPTER_MAX_TOKENS = 1024 * 16; 
    
    /**
     * Número ideal de tokens em um capitulo. 
     * Default é 8 kt.
     */
    protected static final int CHAPTER_IDEAL_TOKENS = 1024 * 8;

    /**
     * Split content into chapters, handling both Markdown and plain text.
     *
     * @param content The content to split
     * @return A list of chapters with titles and content
     * @see Chapter
     * @deprecated Use {@link SplitterGenerico#splitBySize(bor.tools.simplerag.dto.DocumentoWithAssociationDTO, int)} instead
     */
    @Deprecated(since = "0.0.3", forRemoval = true)
    public List<ChapterDTO> splitContent(String content) {
    	boolean isMarkDown = RagUtils.isMarkdown(content);
    	return splitContent(content, isMarkDown);
    }

    /**
     * Split chapter into smaller embedding chunks.
     *
     * @param chapter The chapter to split
     * @return List of DocChunkDTO chunks
     * @deprecated Use {@link SplitterGenerico#splitChapterIntoChunks(ChapterDTO)} instead
     */
    @Deprecated(since = "0.0.3", forRemoval = true)
    public List<DocChunkDTO> splitContent(ChapterDTO chapter) {
	List<DocChunkDTO> chunks = splitChapterContent(chapter);
	return chunks;
    }


    /**
     * Split chapter content into smaller chunks based on titles or size
     * @param chapter The chapter to split
     * @return List of DocChunkDTO chunks
     */
    private List<DocChunkDTO> splitChapterContent(ChapterDTO chapter) {
	String conteudo = chapter.getConteudo();
	
	List<DocChunkDTO> chunks = new ArrayList<>();
	int tokenCount = conteudo.length() / 4; // Rough estimate: 1 token ~ 4 characters
	
	// small chapter, no need to split
	if(tokenCount <= IDEAL_TOKENS) {
	    DocChunkDTO newChunk = DocChunkDTO.builder()
		    .documentoId(chapter.getDocumentoId())
		    .bibliotecaId(chapter.getBibliotecaId())
		    .capituloId(chapter.getId())
		    .trechoTexto(conteudo)
		    .ordemCap(1).tipoEmbedding(TipoEmbedding.CAPITULO)
		    .build();
	    chunks.add(newChunk);
	    return chunks;
	}		
	
	String[] lines = conteudo.split("\n");
	List<TitleTag> titles = detectTitles(lines);

	if (titles != null && !titles.isEmpty()) {
	    int count = 1;
	    for (TitleTag title : titles) {
		Integer start = title.getPosition();
		Integer end = title.getLinesLength();
		String textBlock = String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));

		DocChunkDTO newChunk = DocChunkDTO.builder()
			.documentoId(chapter.getDocumentoId())
			.bibliotecaId(chapter.getBibliotecaId())
			.capituloId(chapter.getId())
			.trechoTexto(textBlock)
			.ordemCap(count++).tipoEmbedding(TipoEmbedding.TRECHO).build();
		chunks.add(newChunk);
	    }
	} else {
	    // Fallback: split by size if no titles found
	    int idealChunckSize = IDEAL_TOKENS * 4; // Approximate character count
	    
	    // Split by paragraphs to avoid cutting in the middle
	    String[] textBlocks = conteudo.split("\\n\\s*\\n");
	    
	    // check the very big paragraphs
	    int maxBlockSize = (MAX_TOKENS * 4); // half of max size - in characters
	    List<String> refinedBlocks = new ArrayList<>();
	    
	    for(int i=0; i<textBlocks.length; i++) {
		String block = textBlocks[i].trim();
		if(block.length() >= maxBlockSize) {
		    // further split by sentences
		    String[] sentences = block.split("(?<=[.!?])\\s+");
		    StringBuilder tempBlock = new StringBuilder();
		    for(String sentence : sentences) {
			if((tempBlock.length() + sentence.length()) <= maxBlockSize) {
			    tempBlock.append(sentence).append(" ");
			    continue;
			}else {
			    refinedBlocks.add(tempBlock.toString().trim());
			    tempBlock.setLength(0); // reset
			    tempBlock.append(sentence).append(" ");
			}
		    }
		} else {
		    // too small block, merge it with next
		    if(block.length() <= (MIN_TOKENS * 4)) {
			//merge with next block if possible
			if(i < textBlocks.length -1) {
			    String nextBlock = textBlocks[i+1].trim();
			    String mergedBlock = block + " " + nextBlock;
			    if(mergedBlock.length() <= (idealChunckSize + 200)) {
				refinedBlocks.add(mergedBlock.trim());
				i++; // skip next block
			    } else {
				// cannot merge, add current block
				refinedBlocks.add(block);
			    }
			}			
		    } else {
			// normal block
			refinedBlocks.add(block);
		    }
		}
	    }//for
	    
	    StringBuilder currentChunk = new StringBuilder();
	    int count = 1;
	    for (String nextBlock : refinedBlocks) {		
		if((currentChunk.length() + nextBlock.length()) <= idealChunckSize) {
		    currentChunk.append(nextBlock).append(" ");
		    continue;
		} else {				
		String textBlock = currentChunk.toString().trim();
		currentChunk.setLength(0); // Reset for next chunk
		currentChunk.append(nextBlock).append(" "); // Start new chunk with current sentence

		DocChunkDTO newChunk = DocChunkDTO.builder()
			.documentoId(chapter.getDocumentoId())
			.bibliotecaId(chapter.getBibliotecaId())
			.capituloId(chapter.getId())
			.trechoTexto(textBlock)
			.ordemCap(count++)
			.tipoEmbedding(TipoEmbedding.TRECHO).build();

		chunks.add(newChunk);
		}
	    }//for
	}//else
	return chunks;
    }

    /**
     * Detect titles in the given lines.
     *
     * @param lines source lines
     * @return list of detected titles
     * @see TitleTag
     * @deprecated Use {@link SplitterGenerico#detectTitles(String[])} instead
     */
    @Deprecated(since = "0.0.3", forRemoval = true)
    public List<TitleTag> detectTitles(String[] lines) {
	return SplitterGenerico.detectTitlesStatic(lines);
    }

    /**
     * Split content into chapters, handling both Markdown and plain text.
     *
     * @param content The content to split
     * @param isMarkdown Whether the content is in Markdown format
     * @return A list of chapters with titles and content
     * @see Chapter
     * @deprecated Use {@link SplitterGenerico#splitBySize(bor.tools.simplerag.dto.DocumentoWithAssociationDTO, int)} instead
     */
    @Deprecated(since = "0.0.3", forRemoval = true)
    public List<ChapterDTO> splitContent(String content, boolean isMarkdown) {
        List<ChapterDTO> chapters = new ArrayList<>();

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
    private List<ChapterDTO> splitMarkdown(String content) {
        List<ChapterDTO> chapters = new ArrayList<>();

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
                chapters.add(new ChapterDTO(currentTitle, sectionContent));
            }

            // Update for next iteration
            currentTitle = matcher.group().replace("#", "").trim();
            lastPos = matcher.end();
        }

        // Add final section
        String finalContent = content.substring(lastPos).trim();
        if (!finalContent.isEmpty()) {
            chapters.add(new ChapterDTO(currentTitle, finalContent));
        }

        return chapters;
    }

    /**
     * Split plain text using paragraph breaks and size-based chunking
     */
    private List<ChapterDTO> splitPlainText(String content) {
        List<ChapterDTO> chapters = new ArrayList<>();

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
                chapters.add(new ChapterDTO(
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
            chapters.add(new ChapterDTO(
                "Section " + chapterNumber,
                currentChapter.toString().trim()
            ));
        }

        return chapters;
    }

    /**
     * Optimize chapter sizes by merging or splitting as needed
     */
    private List<ChapterDTO> optimizeChapterSizes(List<ChapterDTO> chapters) {
        List<ChapterDTO> optimizedChapters = new ArrayList<>();

        for (int i = 0; i < chapters.size(); i++) {
        	ChapterDTO current = chapters.get(i);
            int tokenCount = estimateTokenCount(current.getConteudo());

            if (tokenCount < MIN_TOKENS && i < chapters.size() - 1) {
                // Merge with next chapter if too small
            	ChapterDTO next = chapters.get(i + 1);
                optimizedChapters.add(new ChapterDTO(
                    current.getTitulo(),
                    current.getConteudo() + "\n\n" + next.getConteudo()
                ));
                i++; // Skip next chapter since we merged it
            } else if (tokenCount >= CHAPTER_IDEAL_TOKENS) {
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
    private List<ChapterDTO> splitLargeChapter(ChapterDTO chapter) {
        List<ChapterDTO> subChapters = new ArrayList<>();
        String[] paragraphs = chapter.getConteudo().split("\\n\\s*\\n");

        StringBuilder currentContent = new StringBuilder();
        int currentTokens = 0;
        int partNumber = 1;

        for (String paragraph : paragraphs) {
            int paragraphTokens = estimateTokenCount(paragraph);

            if (currentTokens + paragraphTokens > IDEAL_TOKENS &&
                currentTokens >= MIN_TOKENS) {
        	
                subChapters.add(new ChapterDTO(partNumber,
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
            subChapters.add(new ChapterDTO(
        	    partNumber,
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

