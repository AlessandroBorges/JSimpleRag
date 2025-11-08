package bor.tools.splitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import bor.tools.simplellm.LLMProvider;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import bor.tools.utils.RagUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Splitter genérico para documentos em texto plano e Markdown.
 *
 * Assume que o formato de origem é um DocumentoWithAssociationDTO texto plano simples ou
 * markdown eventualmente composta por títulos, subtítulos e parágrafos.
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SplitterGenerico extends AbstractSplitter {

    private static final Logger logger = LoggerFactory.getLogger(SplitterNorma.class);

    /**
     * Chunck ideal de tokens.
     */
    private static final int IDEAL_TOKENS = 512;

    /**
     * Número máximo de tokens em um chunk.
     */
    private static final int MAX_TOKENS = 2048;

    /**
     * Número mínimo de tokens em um chunk.
     */
    private static final int MIN_TOKENS = 300;

    /**
     * Número máximo de tokens em um capitulo. 
     * Default é 16Kt
     */
    protected static final int CHAPTER_MAX_TOKENS = 1024 * 16; 

    /**
     * Número mínimo de tokens em um capitulo. 
     * Default é 4 kt.
     */
    protected static final int CHAPTER_MIN_TOKENS = 1024 * 2;
    
    /**
     * Número ideal de tokens em um capitulo. 
     * Default é 8 kt.
     */
    protected static final int CHAPTER_IDEAL_TOKENS = 1024 * 8;
    
    /**
     * Número ideal de tokens em um chunk. 
     * Default é 2 kt.
     */
    protected static final int CHUNK_IDEAL_TOKENS = 512;
    
    /**
     * Número máximo de tokens em um chunk. 
     * Default é 2 kt.
     */
    protected static final int CHUNK_MAX_TOKENS = 1024 * 2;
    
   /** 
    * Número máximo de tokens em um chunk. 
    * Default é 256 tokens.
    */
   protected static final int CHUNK_MIN_TOKENS = 256;
    
    /**
     * Número mínimo de tokens em um documento para considerar mais de um capítulo.
     * Default é 8 kt.
     */
    protected static final int DOCUMENT_WITH_CHAPTERS_MIN_TOKENS = 1024 * 4; 

    protected boolean removerTachado = true;

    public SplitterGenerico() {
    }

    /**
     * Construtor
     * 
     * @param biblioteca biblioteca de documentos, para sua
     */
    public SplitterGenerico(LLMProvider llmService) {
	super(llmService);
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<ChapterDTO> splitDocumento(@NonNull DocumentoWithAssociationDTO docDTO) {
	String text = docDTO.getTexto();
	
	int tokenCount = RagUtils.countTokensFast(text);// estimativa inicial
	
	try {
	    tokenCount = getLlmServices().tokenCount(text, "fast");
	} catch (LLMException e) {
	    logger.warn("Failed to count tokens, using estimated count based on text length.", e);
	}
	
	if(tokenCount < DOCUMENT_WITH_CHAPTERS_MIN_TOKENS) {
	    // Se o documento for pequeno, não dividir em capítulos
	    List<ChapterDTO> capitulos = new ArrayList<>();
	    
	    String titulo =  docDTO.getTitulo() != null ? docDTO.getTitulo() : "Capitulo 1";
	    ChapterDTO capitulo = docDTO.createAndAddNewChapter(titulo,  text);	
	    
	    capitulos.add(capitulo);
	    return capitulos;
	}
	
	String[] lines = text.split("\n");
	List<TitleTag> titles = detectTitles(lines);
	if (titles.isEmpty()) {
	    // Se não houver títulos, dividir o texto em partes de tamanho fixo
	    return splitBySize(docDTO, CHAPTER_MAX_TOKENS);
	} else {
	    return splitByTitles(docDTO, lines, titles);
	}
    }
    
    

    /**
     * Divide DocumentoWithAssociationDTO por titulos.
     * 
     * @param docDTO - DocumentoWithAssociationDTO a ser dividido
     * @param lines  - linhas do DocumentoWithAssociationDTO
     * @param titles - titulos do DocumentoWithAssociationDTO
     * @return lista de partes do DocumentoWithAssociationDTO
     */
    public List<ChapterDTO> splitByTitles(DocumentoWithAssociationDTO docDTO, String[] lines, List<TitleTag> titles) {
        logger.debug("Splitting document by titles. Found {} titles", titles.size());

        if (titles.isEmpty()) {
            return splitBySize(docDTO, CHAPTER_MAX_TOKENS);
        }

        if (lines == null) {
            lines = docDTO.getTexto().split("\n");
        }
        // Mapa para armazenar títulos hierárquicos
        Map<Integer, String> titleMap = new LinkedHashMap<>();
        
        // Processa cada seção entre títulos
        for (int i = 0; i < titles.size(); i++) {
            TitleTag titleTag = titles.get(i);
            int startPos = (i == 0) ? 0 : titles.get(i - 1).getPosition();
            int endPos = titleTag.getPosition();
            
            updateTitleMap(titleMap, titleTag.getLevel(), titleTag.getTitle());
            
            String content = extractContentBetweenLines(lines, startPos, endPos);
            if (!content.isEmpty()) {
                createChapterWithMetadata(docDTO, titleMap, content);
            }
        }

        // Processa conteúdo após o último título
        int lastTitlePos = titles.get(titles.size() - 1).getPosition();
        String finalContent = extractContentBetweenLines(lines, lastTitlePos, lines.length);
        
        if (!finalContent.isEmpty()) {
            String titleFull = String.join("\n", titleMap.values()) + "\n Conclusão \n";
            createChapterWithMetadata(docDTO, titleMap, titleFull, finalContent);
        }

        logger.debug("Created {} chapters from document", docDTO.getCapitulos().size());
        return docDTO.getCapitulos();
    }

    /**
     * Atualiza o mapa de títulos, removendo níveis inferiores quando necessário
     */
    private void updateTitleMap(Map<Integer, String> titleMap, int level, String title) {
        titleMap.put(level, title);
        // Remove níveis inferiores
        titleMap.keySet().removeIf(key -> key > level);
    }

    /**
     * Cria capítulo e adiciona metadados de títulos
     */
    private void createChapterWithMetadata(DocumentoWithAssociationDTO docDTO, 
                                           Map<Integer, String> titleMap, 
                                           String content) {
        String titleFull = String.join("\n", titleMap.values());
        createChapterWithMetadata(docDTO, titleMap, titleFull, content);
    }

    /**
     * Cria capítulo com título customizado e adiciona metadados
     */
    private void createChapterWithMetadata(DocumentoWithAssociationDTO docDTO, 
                                           Map<Integer, String> titleMap, 
                                           String titleFull,
                                           String content) {
        ChapterDTO capitulo = createChapter(docDTO, titleFull, content);
        
        titleMap.forEach((level, title) -> 
            capitulo.getMetadados().addMetadata("title_level_" + level, title)
        );
    }

    /**
     * Extrai conteúdo entre duas posições de linhas
     */
    private String extractContentBetweenLines(String[] lines, int start, int end) {
	List<String> contentLines = new ArrayList<>();
	for (int i = start; i < end && i < lines.length; i++) {
	    contentLines.add(lines[i]);
	}
	return String.join("\n", contentLines).trim();
    }

    /**
     * Cria um ChapterDTO com os metadados do documento
     * @param docDTO   DocumentoWithAssociationDTO pai
     * @param titulo   título do capítulo
     * @param conteudo conteúdo do capítulo
     * @param ordem    ordem do capítulo
     * 
     * @return ChapterDTO criado
     */
    private ChapterDTO createChapter(DocumentoWithAssociationDTO docDTO, 
	    			    String titulo, 
	    			    String conteudo) {
	ChapterDTO capitulo = new ChapterDTO();
	docDTO.addParte(capitulo);	
	capitulo.setTitulo(titulo);
	capitulo.setConteudo(conteudo);	
	return capitulo;
    }

    /**
     * Divide DocumentoWithAssociationDTO em sentenças, respeitando o limite de máximo
     * de palavras por parte.
     *
     * <p><b>UPDATED v0.0.3:</b> Removed dependency on ContentSplitter. Now uses internal
     * implementation via {@link #splitTextIntoInitialChapters(String)}.</p>
     */
    public List<ChapterDTO> splitBySize(DocumentoWithAssociationDTO docDTO, int maxWords) {
	logger.debug("Splitting document by size with chapterMaxTokens: {}", maxWords);

	// Use internal implementation instead of ContentSplitter
	List<ChapterDTO> chapters = splitTextIntoInitialChapters(docDTO);

	List<ChapterDTO> adjustedChapters = new ArrayList<>();
	int chapterNumber = 1;

	for (ChapterDTO chapter : chapters) {
	    configureChapterMetadata(chapter, docDTO);

	    int wordCount = countWords(chapter.getConteudo());

	    if (wordCount <= maxWords) {
		chapter.setOrdemDoc(chapterNumber++);
		adjustedChapters.add(chapter);
	    } else {
		List<ChapterDTO> splitChapters = splitLargeChapter(chapter, maxWords, chapterNumber);
		chapterNumber += splitChapters.size();
		adjustedChapters.addAll(splitChapters);
	    }
	}
	logger.debug("Split completed. Generated {} chapters", adjustedChapters.size());
	docDTO.setCapitulos(adjustedChapters);
	return adjustedChapters;
    }

    /**
     * Split a chapter into smaller chunks (DocEmbeddings).
     *
     * <p><b>MERGED from ContentSplitter (v0.0.3):</b> This method replaces the incomplete
     * {@code splitBySize(ChapterDTO, int)} and incorporates the full chunking logic
     * from ContentSplitter with improvements:</p>
     * <ul>
     *   <li>Real token counting via {@link LLMProvider} (not heuristic)</li>
     *   <li>Split by detected titles first</li>
     *   <li>Fallback to size-based splitting with paragraph handling</li>
     *   <li>Sophisticated merge/split logic for optimal chunk sizes</li>
     * </ul>
     *
     * @param chapter the chapter to split into chunks
     * @return list of DocumentEmbeddingDTO chunks with tipo=TRECHO or CAPITULO
     * @since 0.0.3
     */
    public List<DocumentEmbeddingDTO> splitChapterIntoChunks(ChapterDTO chapter) {
	String conteudo = chapter.getConteudo();

	List<DocumentEmbeddingDTO> chunks = new ArrayList<>();

	// Count tokens using real LLM tokenizer
	int tokenCount;
	try {
	    tokenCount = getLlmServices().tokenCount(conteudo, "fast");
	    logger.debug("Chapter '{}' has {} tokens (real count)", chapter.getTitulo(), tokenCount);
	} catch (Exception e) {
	    // Fallback to estimation if LLM service fails
	    tokenCount = conteudo.length() / 4;
	    logger.warn("Failed to count tokens via LLM, using estimation: {}", tokenCount);
	}

	// Small chapter, no need to split
	if(tokenCount <= CHUNK_IDEAL_TOKENS) {
	    DocumentEmbeddingDTO newChunk = chapter.createChapterLevelEmbedding(conteudo,
		    							0, 
		    							TipoEmbedding.CAPITULO);	  
	    chunks.add(newChunk);
	    return chunks;
	}

	// Try to split by detected titles first
	String[] lines = conteudo.split("\n");
	List<TitleTag> titles = detectTitles(lines);

	if (titles != null && !titles.isEmpty()) {
	    // Split by titles
	    int count = 1;
	    for (TitleTag title : titles) {
		Integer start = title.getPosition();
		Integer end = title.getLinesLength();
		
		String textBlock = String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));
               
		String[] textChunks = splitLargeTextIntoChunks(textBlock, IDEAL_TOKENS, MAX_TOKENS, MIN_TOKENS);
		for(String textChunk : textChunks) {
		    DocumentEmbeddingDTO newChunk = chapter.createChapterLevelEmbedding(textChunk,
									    count++);	
		    chunks.add(newChunk);
		}		
	    }		
	} else {
	    // Fallback: split by size if no titles found
	    int idealChunkSize = IDEAL_TOKENS * 4; // Approximate character count

	    // Split by paragraphs to avoid cutting in the middle
	    String[] textBlocks = conteudo.split("\\n\\s*\\n");

	    // Check for very big paragraphs
	    int maxBlockSize = (MAX_TOKENS * 4);
	    List<String> refinedBlocks = new ArrayList<>();

	    for(int i=0; i<textBlocks.length; i++) {
		String block = textBlocks[i].trim();
		if(block.length() >= maxBlockSize) {
		    // Further split by sentences
		    String[] sentences = block.split("(?<=[.!?])\\s+");
		    StringBuilder tempBlock = new StringBuilder();
		    for(String sentence : sentences) {
			if((tempBlock.length() + sentence.length()) <= maxBlockSize) {
			    tempBlock.append(sentence).append(" ");
			    continue;
			} else {
			    refinedBlocks.add(tempBlock.toString().trim());
			    tempBlock.setLength(0);
			    tempBlock.append(sentence).append(" ");
			}
		    }
		    // Add remaining content
		    if(tempBlock.length() > 0) {
			refinedBlocks.add(tempBlock.toString().trim());
		    }
		} else {
		    // Too small block, try to merge with next
		    if(block.length() <= (CHUNK_MIN_TOKENS * 4)) {
			if(i < textBlocks.length - 1) {
			    String nextBlock = textBlocks[i+1].trim();
			    String mergedBlock = block + " " + nextBlock;
			    if(mergedBlock.length() <= (idealChunkSize + 200)) {
				refinedBlocks.add(mergedBlock.trim());
				i++; // Skip next block
			    } else {
				refinedBlocks.add(block);
			    }
			} else {
			    refinedBlocks.add(block);
			}
		    } else {
			// Normal block
			refinedBlocks.add(block);
		    }
		}
	    }

	    // Group refined blocks into chunks
	    StringBuilder currentChunk = new StringBuilder();
	    int count = 1;
	    for (String nextBlock : refinedBlocks) {
		if((currentChunk.length() + nextBlock.length()) <= idealChunkSize) {
		    currentChunk.append(nextBlock).append(" ");
		    continue;
		} else {
		    String textBlock = currentChunk.toString().trim();
		    if(!textBlock.isEmpty()) {
			currentChunk.setLength(0);
			currentChunk.append(nextBlock).append(" ");			
			DocumentEmbeddingDTO newChunk = chapter.createChapterLevelEmbedding(textBlock,
									    count++);
			chunks.add(newChunk);
		    }
		}
	    }

	    // Add final chunk if any
	    if(currentChunk.length() > 0) {	
		DocumentEmbeddingDTO newChunk = chapter.createChapterLevelEmbedding(currentChunk.toString().trim(),
									    count++);
		chunks.add(newChunk);
	    }
	}

	logger.debug("Split chapter '{}' into {} chunks", chapter.getTitulo(), chunks.size());
	return chunks;
    }

    /**
     * Split large text into chunks based on token limits.<br>
     * Best-effort split respecting ideal, max, and min token counts.
     * 
     * 
     * @param textBlock - text to split
     * @param idealTokens - ideal number of tokens per chunk
     * @param maxTokens - maximum number of tokens per chunk
     * @param minTokens - minimum number of tokens per chunk
     * @return array of text chunks
     */
    private String[] splitLargeTextIntoChunks(String textBlock, int idealTokens, int maxTokens, int minTokens) {
	List<String> chunks = new ArrayList<>();
	// check token count
	int tokenCount = RagUtils.countTokensFast(textBlock);
	
	if(tokenCount <= idealTokens) {
	    chunks.add(textBlock);
	    return chunks.toArray(new String[chunks.size()]);
	}
	// check paragraphs
	
	
	
	return chunks.toArray(new String[chunks.size()]);
    }

    /**
     * Configura metadados básicos do capítulo
     */
    private void configureChapterMetadata(ChapterDTO chapter, DocumentoWithAssociationDTO docDTO) {
	chapter.setDocumentoId(docDTO.getId());
	chapter.getMetadados().addMetadata(docDTO.getMetadados());
    }

    /**
     * Divide um capítulo grande em partes menores respeitando o limite de palavras
     * @param chapter  capítulo a ser dividido
     * @param maxWords limite máximo de palavras por parte
     * @param startNumber número inicial para enumeração das partes
     * 
     * @return nova lista de partes do capítulo
     * 
     * 
     */
    private List<ChapterDTO> splitLargeChapter(ChapterDTO chapter, int maxWords, int startNumber) {
	List<ChapterDTO> parts = new ArrayList<>();
	String[] paragraphs = splitIntoParagraphs(chapter.getConteudo());
	
	StringBuilder currentContent = new StringBuilder();
	int currentWordCount = 0;
	int partNumber = startNumber;

	for (String paragraph : paragraphs) {
	    int paragraphWords = countWords(paragraph);

	    if (shouldCreateNewPart(currentWordCount, paragraphWords, maxWords)) {
		parts.add(createChapterPart(chapter, chapter.getTitulo(), currentContent.toString(), partNumber++));
		currentContent = new StringBuilder();
		currentWordCount = 0;
	    }

	    appendParagraph(currentContent, paragraph);
	    currentWordCount += paragraphWords;
	}

	// Adicionar última parte se houver conteúdo
	if (currentContent.length() > 0) {
	    parts.add(createChapterPart(chapter, chapter.getTitulo(), currentContent.toString(), partNumber));
	}

	return parts;
    }

    /**
     * Verifica se deve criar uma nova parte baseado no limite de palavras
     */
    private boolean shouldCreateNewPart(int currentWords, int paragraphWords, int maxWords) {
	return currentWords > 0 && (currentWords + paragraphWords > maxWords);
    }

    /**
     * Adiciona parágrafo ao conteúdo atual
     */
    private void appendParagraph(StringBuilder content, String paragraph) {
	content.append(paragraph).append("\n\n");
    }

    /**
     * Cria uma parte de capítulo com título numerado
     */
    private ChapterDTO createChapterPart(ChapterDTO source, String baseTitle, String content, int partNumber) {
	ChapterDTO part = new ChapterDTO();
	part.setDocumentoId(source.getDocumentoId());
	part.setBibliotecaId(source.getBibliotecaId());	
	part.getMetadados().addMetadata(source.getMetadados());
	part.setTitulo(baseTitle + " (Parte " + partNumber + ")");
	part.setConteudo(content.trim());
	part.setOrdemDoc(partNumber);	
	return part;
    }

    /**
     * Splits plain text content into initial chapters based on paragraph structure and size.
     * This method replaces the functionality previously provided by ContentSplitter.splitContent().
     *
     * <p>The algorithm:</p>
     * <ul>
     *   <li>Splits text on double line breaks (paragraphs)</li>
     *   <li>Groups paragraphs into chapters targeting IDEAL_TOKENS size</li>
     *   <li>Creates chapters with minimum MIN_TOKENS tokens</li>
     *   <li>Assigns sequential titles "Section 1", "Section 2", etc.</li>
     * </ul>
     *
     * @param content the text content to split
     * @return list of ChapterDTO with initial split (may need further size adjustment)
     * @since 0.0.3
     */
    private List<ChapterDTO> splitTextIntoInitialChapters(DocumentoWithAssociationDTO docDTO ) {	
	String content = docDTO.getTexto();
	// Split on double line breaks (paragraphs)
	String[] paragraphs = content.split("\\n\\s*\\n");

	StringBuilder currentChapter = new StringBuilder();
	int currentTokenCount = 0;
	int chapterNumber = 1;

	for (String paragraph : paragraphs) {
	    int paragraphTokens =  RagUtils.countTokensFast(paragraph); //countWords(paragraph);

	    // Create new chapter if we've reached ideal size and have minimum content
	    if ((currentTokenCount + paragraphTokens) >= CHAPTER_IDEAL_TOKENS 
		 && currentTokenCount >= CHUNK_MIN_TOKENS /*CHAPTER_MIN_TOKENS*/) 
	    {
		ChapterDTO chapter = docDTO.createAndAddNewChapter("Capitulo " + chapterNumber,
							  currentChapter.toString().trim());
		if(chapter != null) {
		    chapterNumber++;
		}
		currentChapter = new StringBuilder();
		currentTokenCount = 0;
	    }

	    currentChapter.append(paragraph).append("\n\n");
	    currentTokenCount += paragraphTokens;
	}

	// Add final chapter if there's content remaining
	if (currentChapter.length() > 0) {	   	   
	    var chapter = docDTO.createAndAddNewChapter("Capitulo " + chapterNumber,
		 				  currentChapter.toString().trim());	    
	    if(chapter != null) {
		chapterNumber++;
	    }else {
		logger.warn("Failed to create final chapter for document ID {}", docDTO.getId());
	    }	    
	}

	logger.debug("Split text into {} initial chapters", docDTO.getCapitulos().size());
	return docDTO.getCapitulos();
    }

    /**
     * Detecta títulos no texto do DocumentoWithAssociationDTO.
     * 
     *
     * @param text texto do DocumentoWithAssociationDTO, em formato MarkDown ou texto plano
     * @return Map com numero da linha e títulos
     **/
    public List<TitleTag> detectTitles(String[] lines) {
	return detectTitlesStatic(lines);
    }
    
    /**
     * <h1>Versão static</h1>
     *  Detecta títulos no texto do DocumentoWithAssociationDTO.
     * 
     *
     * @param text texto do DocumentoWithAssociationDTO, em formato MarkDown ou texto plano
     * @return Map com numero da linha e títulos
     **/
    protected static List<TitleTag> detectTitlesStatic(String[] lines) {
	List<TitleTag> titles = new ArrayList<>();
	// Define regex patterns for different title formats
	Pattern uppercasePattern = Pattern.compile("^[A-Z\\s]+$");
	Pattern markdownPattern = Pattern.compile("^(#{1,6})\\s+(.*)$");
	Pattern numberingPattern = Pattern.compile("^(\\d+\\.\\s+|\\d+\\)\\s+).*");

	for (int i = 0; i < lines.length; i++) {
	    String line = lines[i].trim();
	    if (line.isEmpty()) {
		continue;
	    }

	    TitleTag titleTag = null;

	    // Check for markdown titles
	    Matcher markdownMatcher = markdownPattern.matcher(line);
	    if (markdownMatcher.matches()) {
		titleTag = new TitleTag();
		String tag = markdownMatcher.group(1);
		titleTag.setTag(tag);
		titleTag.setLevel(tag.length());
		titleTag.setTitle(markdownMatcher.group(2).trim());
		titleTag.setPosition(i);
	    }
	    // Check for uppercase titles with line breaks before and after
	    else if (i > 0 && i < lines.length - 1 && uppercasePattern.matcher(line).matches()
		    && lines[i - 1].trim().isEmpty() && lines[i + 1].trim().isEmpty()) {
		titleTag = new TitleTag();
		titleTag.setTag("h1");
		titleTag.setLevel(1);
		titleTag.setTitle(line);
		titleTag.setPosition(i);
	    }
	    // Check for numbered titles
	    else if (numberingPattern.matcher(line).matches()) {
		titleTag = new TitleTag();
		titleTag.setTag("numbered");
		titleTag.setLevel(1); // Assuming level 1 for simplicity
		titleTag.setTitle(line);
		titleTag.setPosition(i);
	    }

	    if (titleTag != null) {
		titles.add(titleTag);
	    }
	}

	// check if there are consecutive titles, like title and subtitle in a sequence
	// if so, combine them into a single title
	if (titles.size() > 1) {
	    List<TitleTag> mergedTitles = new ArrayList<>();
	    TitleTag currentTitle = titles.get(0);

	    for (int i = 1; i < titles.size(); i++) {
		TitleTag nextTitle = titles.get(i);
		if (nextTitle.getPosition() - currentTitle.getPosition() <= 3) {
		    // Combine titles
		    currentTitle.setTitle(currentTitle.getTitle() + "\n" + nextTitle.getTitle());
		    // The level of the combined title remains the level of the first title
		} else {
		    mergedTitles.add(currentTitle);
		    currentTitle = nextTitle;
		}
	    }
	    mergedTitles.add(currentTitle); // Add the last processed title
	    return mergedTitles;
	}
	
	// populate TitleTag#linesLength field, by check the number of lines until the next title	
	for (int i = 0; i < titles.size(); i++) {
	    TitleTag title = titles.get(i);
	    int startLine = title.getPosition();
	    int endLine = (i < titles.size() - 1) ? titles.get(i + 1).getPosition() : lines.length;
	    title.setLinesLength(endLine - startLine - 1); // Exclude the title line itself
	}

	return titles;
    }

    /**
     * Split chapter content into smaller chunks based on titles or size
     * @param chapter The chapter to split
     * @return List of DocumentEmbeddingDTO chunks
     */
    public List<DocumentEmbeddingDTO> splitChapterContent(ChapterDTO chapter) {
	String conteudo = chapter.getConteudo();
	
	List<DocumentEmbeddingDTO> chunks = new ArrayList<>();
	int tokenCount = conteudo.length() / 4; // Rough estimate: 1 token ~ 4 characters
	
	// small chapter, no need to split
	if(tokenCount <= CHUNK_IDEAL_TOKENS) {
	    DocumentEmbeddingDTO newChunk = chapter.createChapterLevelEmbedding(conteudo,
		    							1, 
		    							TipoEmbedding.CAPITULO);
	    chunks.add(newChunk);
	    return chunks;
	}	
	
	String fullTitle = chapter.getTitulo().trim();
	
	String[] lines = conteudo.split("\n");
	List<TitleTag> titles = detectTitles(lines);

	if (titles != null && !titles.isEmpty()) {
	    int count = 1;
	    for (TitleTag title : titles) {
		Integer start = title.getPosition();
		Integer end = title.getLinesLength();
		String textBlock = String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));
		textBlock = fullTitle + "\n" + textBlock.trim();
		DocumentEmbeddingDTO newChunk = chapter.createChapterLevelEmbedding(textBlock,
									    count++);
		chunks.add(newChunk);
	    }
	} else {
	    // Fallback: split by size if no titles found
	    int idealChunckCharLen = CHUNK_IDEAL_TOKENS * 4; // Approximate character count
	    
	    // Split by paragraphs to avoid cutting in the middle
	    String[] textBlocks = conteudo.split("\\n\\s*\\n");
	    
	    // check the very big paragraphs
	    int maxBlockSize = (MAX_TOKENS * 4) / 2; // half of max size - in characters
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
		    if(block.length() <= (CHUNK_MIN_TOKENS * 4)) {
			//merge with next block if possible
			if(i < textBlocks.length -1) {
			    String nextBlock = textBlocks[i+1].trim();
			    String mergedBlock = block + " " + nextBlock;
			    if(mergedBlock.length() <= (idealChunckCharLen + 200)) {
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
		if((currentChunk.length() + nextBlock.length()) <= idealChunckCharLen) {
		    currentChunk.append(nextBlock).append(" ");
		    continue;
		} else {				
		String textBlock = currentChunk.toString().trim();
		currentChunk.setLength(0); // Reset for next chunk
		currentChunk.append(nextBlock).append(" "); // Start new chunk with current sentence
		DocumentEmbeddingDTO newChunk = chapter.createChapterLevelEmbedding(textBlock,
									    count++);
		chunks.add(newChunk);
		}
	    }//for
	}//else
	return chunks;
    }

   
    /**
     * Remove repetições no texto.
     * 
     * @param text texto a ser processado
     * @return texto sem repetições
     */
    @Override
    public String removeRepetitions(String text) {
	logger.debug("Removing repetitions from text of length: {}", text.length());

	// Delegar para a implementação do AbstractSplitter que já está funcional
	return super.removeRepetitions(text);
    }

    /**
     * Realiza a divisão do texto em parágrafos. É considerado um paragrafo a
     * sequência de linhas de texto: 1) com uma linha em branco antes e depois; 2)
     * com uma linha em branco antes e sem linha em branco depois - fim de arquivo;
     * 3) com uma linha em branco depois e sem linha em branco antes - início de
     * arquivo. 4) um título seguido de uma linha em branco e um parágrafo. 5) uma
     * tabela, em formato markdown, html, csv, etc., seguido por uma linha em branco
     * ou um parágrafo.
     */
    @Override
    public String[] splitIntoParagraphs(String text) {
	String[] lines = text.split("\n");
	var titles = detectTitles(lines);
	return splitIntoParagraphs(lines, titles);
    }

    /**
     * Realiza a divisão do texto em parágrafos.<br>
     * É considerado um paragrafo a sequência de linhas de texto:
     * 
     * <pre>
     * 1) com uma linha em branco antes e depois;
     * 2) com uma linha em branco antes e sem linha em branco depois - fim de arquivo;
     * 3) com uma linha em branco depois e sem linha em branco antes - início de arquivo.
     * 4) um título seguido de uma linha em branco e um parágrafo.
     * 5) uma tabela, em formato markdown, html, csv, etc., seguido por uma linha em branco ou um parágrafo.
     * </pre>
     */
    public String[] splitIntoParagraphs(String[] lines, List<TitleTag> titles) {
	List<String> paragraphs = new ArrayList<>();
	StringBuilder paragraph = new StringBuilder();
	Map<Integer, TitleTag> titleMap = titles.stream()
		.collect(Collectors.toMap(TitleTag::getPosition, t -> t));

	int n = lines.length;

	for (int i = 0; i < n; i++) {
	    String line = lines[i].trim();

	    // Check for blank line
	    if (line.isEmpty()) {
		if (paragraph.length() > 0) {
		    var p = paragraph.toString().trim();
		    paragraphs.add(p);
		    paragraph.setLength(0);
		}
	    } else {
		// Check for title
		if (titleMap.containsKey(i)) {
		    if (paragraph.length() > 0) {
			var p = paragraph.toString().trim();
			paragraphs.add(p);
			paragraph.setLength(0);
		    }
		    paragraphs.add(titleMap.get(i).getTitle());
		} else {
		    // Append line to current paragraph
		    if (paragraph.length() > 0) {
			paragraph.append(" ");
		    }
		    paragraph.append(line);
		}
	    }
	}
	// Add the last paragraph if any
	if (paragraph.length() > 0) {
	    paragraphs.add(paragraph.toString().trim());
	}

	int maxWords = CHAPTER_MAX_TOKENS; // Define your maximum words per paragraph limit
	// verifique se a quantidade de palavras em um paragrafo excede o limite maximo
	// CHAPTER_MAX_TOKENS
	// caso exceda, realizar a divisão do paragrafo em 2 paragrafos menores,
	// considerando sentenças
	// como ponto de divisão.
	{
	    List<String> splitParagraphs = new ArrayList<>();
	    for (String parag : paragraphs) {
		int wordCount = countWords(parag);
		if (wordCount > maxWords) {
		    String[] sentences = splitIntoSentences(parag, maxWords * 80 / 100);
		    for (String sentence : sentences) {
			splitParagraphs.add(sentence);
		    }
		} else {
		    splitParagraphs.add(parag);
		}
	    }
	    paragraphs.clear();
	    paragraphs.addAll(splitParagraphs);
	}

	// verifique se a soma de palavras em parágrafos consecutivos
	// é inferior a limite máximo de palavras por parágrafo.
	// Se for, combine os parágrafos.

	List<String> combinedParagraphs = new ArrayList<>();
	StringBuilder currentParagraph = new StringBuilder();
	int currentWordCount = 0;

	for (String parag : paragraphs) {
	    int wordCount = countWords(parag);
	    if (currentWordCount + wordCount <= maxWords) {
		if (currentParagraph.length() > 0) {
		    currentParagraph.append("\n");
		}
		currentParagraph.append(parag);
		currentWordCount += wordCount;
	    } else {
		combinedParagraphs.add(currentParagraph.toString().trim());
		currentParagraph.setLength(0);
		currentParagraph.append(parag);
		currentWordCount = wordCount;
	    }
	}
	// Add the last paragraph if any
	if (currentParagraph.length() > 0) {
	    combinedParagraphs.add(currentParagraph.toString().trim());
	}
	return combinedParagraphs.toArray(new String[0]);
    }

  

    /**
     * Verifica se a linha especificada é um título.
     * 
     * @param titles    - lista de títulos detectados
     * @param lineIndex - índice da linha a ser verificada
     * @return true se a linha for um título, false caso contrário
     */
    protected boolean containsTitleAtLine(List<TitleTag> titles, int lineIndex) {
	for (TitleTag title : titles) {
	    if (title.getPosition() == lineIndex) {
		return true;
	    }
	}
	return false;
    }
    
   

}