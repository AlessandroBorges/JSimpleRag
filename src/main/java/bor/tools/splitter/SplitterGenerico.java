package bor.tools.splitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import bor.tools.simplellm.LLMService;
import bor.tools.simplerag.dto.CapituloDTO;
import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.entity.Capitulo;
import bor.tools.simplerag.entity.Documento;
import lombok.Data;
import lombok.EqualsAndHashCode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import bor.tools.simplellm.*;
import bor.tools.simplerag.entity.*;

import bor.tools.simplerag.dto.*;

/**
 * Splitter genérico para documentos em texto plano e Markdown.
 *
 * Assume que o formato de origem é um DocumentoDTO texto plano simples ou
 * markdown eventualmente composta por títulos, subtítulos e parágrafos.
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SplitterGenerico extends AbstractSplitter {

    private static final Logger logger = LoggerFactory.getLogger(SplitterNorma.class);

    /**
     * Número máximo de palavras em uma parte. Default é 6KB
     */
    protected int maxWords = 1024 * 6;

    protected boolean removerTachado = true;

    public SplitterGenerico() {
    }

    /**
     * Construtor
     * 
     * @param biblioteca biblioteca de documentos, para sua
     */
    public SplitterGenerico(LLMService llmService) {
	super(llmService);
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<CapituloDTO> splitDocumento(@NonNull DocumentoDTO DocumentoDTO) {
	String text = DocumentoDTO.getTexto();
	String[] lines = text.split("\n");
	List<TitleTag> titles = detectTitles(lines);
	if (titles.isEmpty()) {
	    // Se não houver títulos, dividir o texto em partes de tamanho fixo
	    return splitBySize(DocumentoDTO, this.maxWords);
	} else {
	    return splitByTitles(DocumentoDTO, lines, titles);
	}
    }

    /**
     * Divide DocumentoDTO por titulos.
     * 
     * @param DocumentoDTO - DocumentoDTO a ser dividido
     * @param lines        - linhas do DocumentoDTO
     * @param titles       - titulos do DocumentoDTO
     * @return lista de partes do DocumentoDTO
     */
    public List<CapituloDTO> splitByTitles(DocumentoDTO DocumentoDTO, String[] lines, List<TitleTag> titles) {

	if (titles.isEmpty()) {
	    return splitBySize(DocumentoDTO, maxWords);
	} else {
	    int n_titles = titles.size();
	    if (lines == null)
		lines = DocumentoDTO.getTexto().split("\n");

	    List<CapituloDTO> lista = new ArrayList<>(n_titles + 1);
	    {

		String[] sections = new String[n_titles + 1];
		List<Integer> titlePositions = titles.stream().map(TitleTag::getPosition).collect(Collectors.toList());

		for (int i = 0; i < n_titles; i++) {
		    // magic java
		    int start = i == 0 ? 0 : titlePositions.get(i - 1); // inclui inicio
		    int end = i == (n_titles - 1) ? lines.length : titlePositions.get(i); // inclui parte final
		    List<String> linesList = new ArrayList<>();
		    for (int j = start; j < end; j++) {
			linesList.add(lines[j]);
		    }
		    sections[i] = String.join("\n", linesList);
		}

		for (String parag : sections) {
		    if (parag == null || parag.isEmpty())
			continue;
		    CapituloDTO parte = new CapituloDTO();
		    DocumentoDTO.addParte(parte);
		    parte.setConteudo(parag);
		    lista.add(parte);
		}
	    }
	    return lista;
	}
    }


    public List<CapituloDTO> splitBySize(DocumentoDTO documento, int maxWords) {
	logger.debug("Splitting document by size with maxWords: {}", maxWords);

	// Use ContentSplitter como base para uma implementação robusta
	ContentSplitter contentSplitter = new ContentSplitter();
	List<CapituloDTO> chapters = contentSplitter.splitContent(documento.getTexto(), false);

	// Ajustar os capítulos para o limite de palavras especificado
	List<CapituloDTO> adjustedChapters = new ArrayList<>();
	int chapterNumber = 1;

	for (CapituloDTO chapter : chapters) {
	    String content = chapter.getConteudo();
	    int wordCount = countWords(content);

	    if (wordCount <= maxWords) {
		// Capítulo dentro do limite
		chapter.setOrdemDoc(chapterNumber++);
		adjustedChapters.add(chapter);
	    } else {
		// Dividir capítulo em partes menores
		String[] paragraphs = splitIntoParagraphs(content);
		StringBuilder currentChapter = new StringBuilder();
		int currentWords = 0;

		for (String paragraph : paragraphs) {
		    int paragraphWords = countWords(paragraph);

		    if (currentWords + paragraphWords > maxWords && currentWords > 0) {
			// Criar novo capítulo
			CapituloDTO newChapter = new CapituloDTO();
			newChapter.setTitulo(chapter.getTitulo() + " (Parte " + chapterNumber + ")");
			newChapter.setConteudo(currentChapter.toString().trim());
			newChapter.setOrdemDoc(chapterNumber++);
			adjustedChapters.add(newChapter);

			// Reiniciar
			currentChapter = new StringBuilder();
			currentWords = 0;
		    }

		    currentChapter.append(paragraph).append("\n\n");
		    currentWords += paragraphWords;
		}

		// Adicionar último fragmento
		if (currentChapter.length() > 0) {
		    CapituloDTO newChapter = new CapituloDTO();
		    newChapter.setTitulo(chapter.getTitulo() + " (Parte " + chapterNumber + ")");
		    newChapter.setConteudo(currentChapter.toString().trim());
		    newChapter.setOrdemDoc(chapterNumber++);
		    adjustedChapters.add(newChapter);
		}
	    }
	}

	logger.debug("Split completed. Generated {} chapters", adjustedChapters.size());
	return adjustedChapters;
    }

    /**
     * Detecta títulos no texto do DocumentoDTO.
     * 
     *
     * @param text texto do DocumentoDTO, em formato MarkDown ou texto plano
     * @return Map com numero da linha e títulos
     **/
    public List<TitleTag> detectTitles(String[] lines) {
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

	return titles;
    }


   

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

	int maxWords = this.maxWords; // Define your maximum words per paragraph limit
	// verifique se a quantidade de palavras em um paragrafo excede o limite maximo
	// maxWords
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
