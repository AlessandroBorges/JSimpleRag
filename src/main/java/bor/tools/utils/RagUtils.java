package bor.tools.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.apache.tika.sax.ToXMLContentHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bor.tools.simplellm.*;
import bor.tools.simplellm.exceptions.LLMException;

import java.text.SimpleDateFormat;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import bor.tools.simplerag.entity.*;

import java.util.zip.CRC32;
import java.util.zip.Checksum;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Classe utilitária com métodos de uso geral.
 *
 * @todo - mover todos os métodos de conversão/extracao de texto para uma classe
 *       separada RAGParser.
 *
 */
public class RagUtils {

    /**
     * Tamanho máximo de conversão para Tika
     */
    public static int MAX_STRING_LENGTH = 1024 * 200;

    public static final String[] EXTENSOES_TEXTO = { ".txt", ".md", ".markdown", ".html", ".xhtml", ".pdf", ".doc",
	    ".docx", ".xls", ".xlsx", ".ppt", ".pptx" };

    protected static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    public static boolean removerTachado = true;
    
    protected static LLMService llmService = null;
    
    
    /**
     * Tipos MIME de arquivos MS-Office
     *
     */
    public static final String MIME_MS_WORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
	    MIME_MS_EXCEl = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
	    MIME_MS_PPT = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
	    MIME_DOC = "application/msword", MIME_XLS = "application/vnd.ms-excel",
	    MIME_PPT = "application/vnd.ms-power";

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(RagUtils.class);

    /**
     * Valor do cliente HTTP ACCEPT
     */
    public static final String ACCEPT_VALUE = "text/html,application/xhtml+xml,application/xml,text/json;";

    /**
     * header HTTP ACCEPT
     */
    public static final String ACCEPT = "Accept";

    /**
     * header HTTP USER_AGENT
     */
    public static final String USER_AGENT = "User-Agent";
    /**
     * Valor do header USER_AGENT
     */
    public static final String USER_AGENT_VALUE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0";

    public static LLMService getLLMService(String provider) {
	// Return cached service if no specific provider requested
	if (llmService != null && provider == null) {
	    return llmService;
	}

	LLMService service;

	if (provider == null) {
	    // Try providers in order of preference when no specific provider is requested
	    service = tryProviders("lmstudio", "ollama", "openai");
	} else {
	    // Create specific provider
	    service = createProviderService(provider.toLowerCase());
	}

	if (service != null && service.isOnline()) {
	    llmService = service;
	    return service;
	}

	throw new RuntimeException("No LLM service is available or online");
    }

    /**
     * 
     * @param providers
     * @return
     */
    public static LLMService tryProviders(String... providers) {
	for (String provider : providers) {
	    try {
		LLMService service = createProviderService(provider);
		if (service != null && service.isOnline()) {
		    return service;
		}
	    } catch (Exception e) {
		logger.warn("Failed to create or connect to provider: {}", provider);
	    }
	}
	return null;
    }

    public static LLMService createProviderService(String provider) {
	return switch (provider) {
	case "openai" -> LLMServiceFactory.createOpenAI(null);
	case "lmstudio" -> LLMServiceFactory.createLMStudio();
	case "ollama" -> LLMServiceFactory.createOllama();
	default -> throw new IllegalArgumentException("Provedor LLM desconhecido: " + provider);
	};
    }

    /**
     * MARKDOWN parser
     */
    private static XHTMLToMarkdownParser markdownParser = new XHTMLToMarkdownParser();

    /**
     * Testa de um nome contem uma das substrings listadas.
     * 
     * @param name            - nome de teste
     * @param substringsArray - array de substrings
     * @return
     */
    public static boolean contains(String name, String... substringsArray) {
	String nameLo = name.toLowerCase();
	for (String s : substringsArray) {
	    if (nameLo.contains(s.toLowerCase()))
		return true;
	}
	return false;
    }

    /**
     * Check se um nome está na lista
     * 
     * @param name        - verifica se este nome está na lista
     * @param outrosNomes - array de nomes a serem testados
     * @return true se deu match em algum falor da lista
     */
    public static boolean naLista(String name, String... outrosNomes) {
	String nameLo = name.trim().toLowerCase();
	for (String s : outrosNomes) {
	    if (nameLo.equalsIgnoreCase(s.trim()))
		return true;
	}
	return false;
    }

    /**
     * Recupera menor valor positivo entre a e b.<br>
     * Se ambos forem negativos, retorna -1
     * 
     * @param a - primeiro valor
     * @param b - segundo valor
     * @return menor valor positivo
     */
    public static int menorPositivo(int a, int b) {
	int menorValorPositivo = (a > 0 && b > 0) ? Math.min(a, b) : (a > 0) ? a : (b > 0) ? b : -1;
	return menorValorPositivo;
    }

    /**
     * Retorna uma instância de Library com os dados da Wiki.<br>
     * Esta biblioteca usará a configuração de embeddings padrão, dada por
     * {@link #createEmbeddingConfigDefault()} <br>
     *
     * <pre>
     * id  |uuid  |nivel_acesso_id|embeddings_config_id|     nome                       |descricao                              |area                  |
    * ----+------+---------------+--------------------+--------------------------------+---------------------------------------+----------------------+
    *  1  | -    |      0        |          2         |Conhecimentos Gerais e Wikidata |Base de conhecimentos gerais, Wikidata |Conhecimentos Gerais  |
     * </pre>
     *
     *
     * @return Library default, configurada para conhecimentos gerais e Wikidata
     *
     * @see Library
     * @see EmbeddingsConfig
     * @see #createEmbeddingConfigDefault()
     */
    public static Library createBibliotecaDefault() {
	Library biblioteca = new Library();
	biblioteca.setId(1);	
	biblioteca.setNome("Conhecimentos Gerais e Wikidata");
	//biblioteca.setDescricao("Base de conhecimentos gerais, Wikidata");
	biblioteca.setAreaConhecimento("Conhecimentos Gerais");	
	return biblioteca;
    }

    
    /**
     * Retorna a posição de um nome na lista.<br>
     * Retorna -1 se não for encontrado.
     *
     * @param name        - verifica se este nome está na lista
     * @param outrosNomes - array de nomes a serem testados
     * @return índice de nome na lista outrosNomes, -1 se não foi encontrado.
     */
    public static int ordemLista(String name, String... outrosNomes) {
	String nameLo = name.trim().toLowerCase();
	for (int i = 0; i < outrosNomes.length; i++) {
	    String s = outrosNomes[i];
	    if (nameLo.equalsIgnoreCase(s.trim()))
		return i;
	}
	return -1;
    }

    /**
     * Verifica se um objeto é nulo ou vazio.
     * 
     * @param obj - instancia de String, Collection ou Array
     * @return true se objeto for nulo ou vazio
     *
     * @throws UnsupportedOperationException se obj for um container desconhecido.
     */
    @SuppressWarnings("rawtypes")
    public static boolean isEmpty(Object obj) {
	if (obj == null)
	    return true;

	if (obj instanceof String) {
	    return ((String) obj).isBlank();
	}

	if (obj instanceof Collection) {
	    return ((Collection) obj).size() == 0;
	}

	if (obj.getClass().isArray()) {
	    return ((Object[]) obj).length == 0;
	}

	if (obj instanceof Map) {
	    return ((Map) obj).size() == 0;
	}

	// return false;
	throw new UnsupportedOperationException(
		"Erro em RagUtils#isEmpty() \n Objeto desconhecido: " + obj.getClass() + " - " + obj.toString());
    }

    /**
     * Print simplificado
     * 
     * @param obj
     */
    public static void print(Object obj) {
	System.out.println(obj);
    }

    /**
     * Print simplificado
     * 
     * @param obj
     */
    public static void print(Object obj, int limit) {
	if (obj == null) {
	    System.out.println("null");
	    return;
	}
	{
	    String s = obj.toString();
	    if (s.length() > limit) {
		s = s.substring(0, limit) + "...";
	    }
	    System.out.println(s);
	}
    }

    /**
     * Print colorido
     * 
     * @param cor - cor ANSI de LogColor
     * @param obj
     */
    public static void print(LogColor cor, Object obj) {
	print(cor.colorIt(obj));
    }

    /**
     *
     * @param color Cor ANSI
     * @param obj   - objeto a ser impresso
     */
    public static void print(String color, Object obj) {
	print(LogColor.custom(obj, color));
    }

    /**
     * Print colorido de DEBUG
     * 
     * @param obj
     */
    public static void debug(Object obj) {
	print(LogColor.CYAN_BOLD_BRIGHT, "## DEBUG:\n" + (obj == null ? "null" : obj.toString()));
    }

    /**
     *
     * @param color Cor ANSI
     * @param obj   - objeto a ser impresso
     */
    public static void print(String color, Object obj, int limit) {
	print(LogColor.custom(obj, color), limit);
    }

    /**
     * Input da console
     * 
     * @return
     * @throws IOException
     */
    public static String input() throws IOException {
	BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
	// Reading data using readLine
	String input = reader.readLine();
	return input;
    }

    /**
     * Identifica o tipo MIME do objeto string, possivelmente de um arquivo textual,
     * usando Apache Tika.
     * 
     * @param header - cabeçalho do arquivo
     * @return tipo MIME
     * @throws IOException
     */
    public static String detectMimeTypeTika(String header) throws IOException {
	int size = Math.min(header.length(), 256);
	String prefix = header.substring(0, size);
	return detectMimeTypeTika(prefix.getBytes());
    }

    /**
     * Tika - instância única
     **/
    private static Tika tika;

    /**
     * Identifica o tipo MIME do objeto binario data, possivelmente de um arquivo,
     * usando Apache Tika.
     *
     * @return
     * @throws IOException
     */
    public static String detectMimeTypeTika(byte[] data) throws IOException {
	if (tika == null)
	    tika = new Tika();
	int size = Math.min(data.length, 256);
	byte[] prefix = new byte[size];
	System.arraycopy(data, 0, prefix, 0, size);
	return tika.detect(data);
    }

    /**
     * Converte um array de bytes de um documento em um Markdown simples, usando
     * Apache Tika para converter tipos MIME suportados
     *
     * @param data - data binário
     * @return texto extraído do arquivo binário
     * @throws IOException
     * @throws Exception
     * @throws TikaException
     */
    public static String convertToMarkdown(byte[] data) throws IOException, TikaException, Exception {
	String html = convertToHTML(data);
	if (html == null || html.isEmpty())
	    return null;
	String mime = detectMimeTypeTika(html);
	if (mime.equals("text/html") || mime.equals("application/xhtml+xml")) {
	    return convertHTMLtoMarkdown(html);
	}
	int len = Math.min(html.length(), 100);
	logger.info("MIME: {} - Content: {}", mime, html.substring(0, len));
	// retorna texto plano
	return html;
    }

    /**
     * Converte um texto HTML/XHTML para Markdown.
     * 
     * @param html_content - conteudo HTML
     * @return
     * @throws IOException
     * @throws TikaException
     * @throws Exception
     */
    public static String convertToMarkdown(String html_content) throws IOException, TikaException, Exception {
	String mime = detectMimeTypeTika(html_content);
	String content = "";
	if (mime.equals("text/html") || mime.equals("application/xhtml+xml")) {
	    content = convertHTMLtoMarkdown(html_content);
	} else {
	    // possivelmente é texto plano
	    content = html_content;
	}
	int len = Math.min(content.length(), 100);
	logger.info("MIME: {} - Content: {}", mime, content.substring(0, len));
	return content;

    }

    /**
     * Converte um array de bytes de um documento em uma HTML simples. Pode retornar
     * apenas texto plano simples, se o formato original não for suportado.
     *
     * @param data - data binário
     * @return texto HTML extraído do arquivo binário
     *
     * @throws IOException
     * @throws TikaException
     * @throws Exception
     */
    public static String convertToHTML(byte[] data) throws IOException, TikaException, Exception {
	String mime = detectMimeTypeTika(data);
	String content_html = "";

	// use Apache Tika to extract text from the binary data, possible a PDF file
	// or a docx file
	if (mime.equals("application/pdf")) {
	    content_html = convertPDFtoHTML(data);
	} else
	// converte arquivos MS-Office
	if (mime.equals(MIME_MS_WORD) || mime.equals(MIME_MS_EXCEl) || mime.equals(MIME_MS_PPT) || mime.equals(MIME_DOC)
		|| mime.equals(MIME_XLS) || mime.equals(MIME_PPT)) {
	    content_html = convertOfficeToXHTML(data);
	    if (removerTachado)
		content_html = removeStrikeThrough(content_html);

	} else if (mime.equals("text/plain")) {
	    // Use Tika to detect the charset encoding
	    CharsetDetector detector = new CharsetDetector();
	    CharsetMatch match = detector.setText(data).detect();
	    String charset = match.getName();
	    content_html = new String(data, charset);
	} else {
	    // Use Tika to extract text from other types of files
	    Tika tika = new Tika();
	    tika.setMaxStringLength(MAX_STRING_LENGTH); // 200KB
	    content_html = tika.parseToString(new ByteArrayInputStream(data));
	}

	if (removerTachado)
	    content_html = removeStrikeThrough(content_html);

	return content_html;
    }

    /**
     * Converte um array de bytes de um documento em uma string simples, usando
     * Apache Tika para converter tipos MIME suportados
     *
     * @param data - data binário
     * @return texto extraído do arquivo binário
     * @throws IOException
     * @throws Exception
     * @throws TikaException
     */
    public static String extractPlainText(byte[] data) throws IOException, TikaException, Exception {
	String mime = detectMimeTypeTika(data);
	String content_html = convertToHTML(data);

	if (content_html == null || content_html.isEmpty())
	    return null;

	String content = convertHTMLtoPlainText(content_html);
	int len = Math.min(content.length(), 100);
	logger.info("Extract plain text MIME: {} - Content: {}", mime, content.substring(0, len));
	return content;
    }

    /**
     * Converte um array de bytes de um documento em uma string simples. Nota: - Se
     * o arquivo for um PDF, o texto será extraído usando Apache Tika. - Tabelas e
     * cabeçalhos serão tratados de forma especial.
     *
     * @param data - dados binários do arquivo
     * @return texto plano extraído do arquivo
     *
     * @throws IOException
     * @throws TikaException
     * @throws Exception
     */
    public static String extractPlainTextFromPDF(byte[] data) throws IOException, TikaException, Exception {
	String html = convertPDFtoHTML(data);
	return convertHTMLtoPlainText(html);
    }

    /**
     * Converte HTML/XHTML para Markdown.
     *
     * @param html
     * @return
     * @throws Exception
     * @throws TikaException
     * @throws IOException
     */
    public static String convertHTMLtoMarkdown(String html) throws IOException, TikaException, Exception {
	String mime = detectMimeTypeTika(html);
	if (!mime.equals("text/html") && !mime.equals("application/xhtml+xml")) {
	    throw new IllegalArgumentException("Unsupported content type: " + mime);
	}
	if (removerTachado)
	    html = removeStrikeThrough(html);

	String md = markdownParser.convertToMarkdown(html, mime);
	return md;
    }

    /**
     * Remove texto tachado de um texto HTML, Markdown ou texto plano.
     *
     *
     * @param text - texto de entrada
     * @return texto limpo
     */
    public static String removeStrikeThrough(String text) {
	if (text == null || text.isEmpty())
	    return text;
	if (isHtml(text)) {
	    // Remove strikethrough tags using JSOUP
	    Document doc = Jsoup.parse(text);
	    Elements strikeElements = doc.select("s, strike, del");
	    for (Element strikeElement : strikeElements) {
		strikeElement.unwrap();
	    }
	    return doc.toString();
	} else if (isMarkdown(text)) {
	    // Remove strikethrough formatting in markdown
	    return text.replaceAll("~~(.*?)~~", "$1");
	} else {
	    // Remove strikethrough formatting in plain text
	    return text.replaceAll("~(.*?)~", "$1");
	}
    }

    
    /**
     * Simple format detector based on file extension
     * @param fileName
     * @return MIME Types or null if unknown
     */
    public static  String simpleFormatDetector(String fileName) {
	String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
	
	switch (ext) {
	    case "pdf":
		return "application/pdf";
		
	    case "doc":
		return "application/msword";
		
	    case "docx":
		return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		
	    case "txt":
	    case "sql":
	    case "log":
		return "text/plain";
		
	    case "java":
		return "text/x-java-source";
		
	    case "py":
		return "text/x-python";
		
	    case "js":	
		return "application/javascript";
		
	    case "csv":
		return "text/csv";
		
	    case "md":
	    case "markdown":
		return "text/markdown";
		
	    case "html":
	    case "htm":
		return "text/html";
		
	    case "xml":
		return "application/xml";
		
	    case "xhtml":	
		return "application/xhtml+xml";
		
	    case "ppt":
		return "application/vnd.ms-powerpoint";
		
	    case "pptx":
		return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
		
	    case "xls":
	    case "xlm":
		return "application/vnd.ms-excel";
		
	    case "xlsx":
		return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";	    
	    default:
		return null;
	}
    }

    
    /**
     * Extrai texto de um arquivo MS-Office
     * 
     * @param data - data binária do arquivo
     * @return texto extraído do arquivo
     *
     * @throws Exception
     * @throws TikaException
     * @throws IOException
     */
    public static String extractPlainTextFromMSOffice(byte[] data) throws IOException, TikaException, Exception {
	String html = convertOfficeToXHTML(data);
	return convertHTMLtoPlainText(html);
    }

    /**
     * Extracts text in HTML format from a PDF document using Apache Tika.
     *
     * @param data byte array containing the PDF document
     * @return HTML formatted text extracted from the PDF, or empty string if
     *         extraction fails
     * @throws TikaException if there is an error during parsing
     * @throws IOException   if there is an error reading the input
     * @throws Exception  if there is an error processing the document
     */
    public static String convertPDFtoHTML(byte[] data) throws IOException, TikaException, Exception {
	if (data == null || data.length == 0) {
	    throw new IllegalArgumentException("Input data cannot be null or empty");
	}

	try (InputStream input = new ByteArrayInputStream(data)) {
	    // Use ToXMLContentHandler for HTML output instead of BodyContentHandler
	    ToXMLContentHandler handler = new ToXMLContentHandler();

	    // Configure metadata
	    Metadata metadata = new Metadata();
	    metadata.set(HttpHeaders.CONTENT_TYPE, "application/pdf");

	    // Create parse context with PDF parser
	    ParseContext parseContext = new ParseContext();
	    PDFParser pdfParser = new PDFParser();
	    parseContext.set(PDFParser.class, pdfParser);

	    // Parse the document
	    pdfParser.parse(input, handler, metadata, parseContext);
	    String html = handler.toString();
	    if (removerTachado)
		html = removeStrikeThrough(html);
	    return html;
	}
    }

    /**
     * Extracts plain text from HTML/XHTML with special handling for tables and
     * headers.
     *
     * @param html_content HTML or XHTML content
     * @return Extracted text with tables in markdown and properly spaced headers
     */
    public static String convertHTMLtoPlainText(String html_content) {
	if (html_content == null || html_content.trim().isEmpty()) {
	    // throw new IllegalArgumentException("Content cannot be null or empty");
	    return "";
	}

	try {
	    // Parse HTML/XHTML
	    Document doc = Jsoup.parse(html_content);
	    StringBuilder result = new StringBuilder();
	    FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();

	    // Process each element in the document
	    for (Element element : doc.body().select("*")) {
		String tagName = element.tagName().toLowerCase();

		// Handle tables - convert to markdown
		if (tagName.equals("table")) {
		    // Use Flexmark to convert table to markdown
		    String markdownTable = converter.convert(element.outerHtml());
		    result.append("\n").append(markdownTable).append("\n\n");
		    continue;
		}

		// Handle headers and title-like elements
		if (tagName.matches("h[1-6]|title")) {
		    result.append("\n\n").append(element.text()).append("\n\n");
		    continue;
		}

		// Handle paragraphs and other block elements
		if (tagName.equals("p") || tagName.equals("div") || tagName.equals("br") || tagName.equals("li")) {
		    result.append(element.text()).append("\n");
		    continue;
		}

		// Handle inline elements
		// Handle inline elements
		var parent = element == null ? null : element.parent();
		if (element != null && !element.text().trim().isEmpty() && (parent != null && parent.tagName() != null
			&& !parent.tagName().matches("p|div|h[1-6]|li|table"))) {
		    result.append(element.text()).append(" ");
		}

	    }

	    // Clean up the result
	    return cleanupText(result.toString());
	} catch (Exception e) {
	    throw new RuntimeException("Error processing HTML/XHTML content", e);
	}
    }

    /**
     * Cleans up the extracted text by removing extra whitespace and normalizing
     * line breaks.
     */
    protected static String cleanupText(String text) {
	return text
		// Remove multiple blank lines
		.replaceAll("\\n{3,}", "\n\n")
		// Remove multiple spaces
		.replaceAll("\\s+", " ")
		// Fix spacing around markdown tables
		.replaceAll("\\n\\s*\\|", "\n|")
		// Ensure proper spacing around headers
		.replaceAll("\\n\\s*([A-Z][^\\n]{0,100}:?\\s*)\\n", "\n\n$1\n\n")
		// Final trim
		.trim();
    }

    /**
     * Converts Microsoft Office documents (DOCX, PPTX, XLSX) to XHTML format. The
     * document type is automatically detected at runtime.
     *
     * @param data byte array containing the document
     * @return XHTML formatted text extracted from the document, or empty string if
     *         extraction fails
     * @throws TikaException if there is an error during parsing
     * @throws IOException   if there is an error reading the input
     * @throws Exception  if there is an error processing the document
     */
    public static String convertOfficeToXHTML(byte[] data) throws IOException, TikaException, Exception {
	if (data == null || data.length == 0) {
	    // throw new IllegalArgumentException("Input data cannot be null or empty");
	    return null;
	}

	try (InputStream input = new ByteArrayInputStream(data)) {
	    // Create the XML content handler
	    ToXMLContentHandler handler = new ToXMLContentHandler();

	    // Configure metadata
	    Metadata metadata = new Metadata();

	    // Create parse context with AutoDetectParser
	    ParseContext parseContext = new ParseContext();
	    Parser parser = new AutoDetectParser();
	    parseContext.set(Parser.class, parser);

	    // Parse the document
	    parser.parse(input, handler, metadata, parseContext);

	    // Get detected document type from metadata
	    String detectedType = metadata.get(HttpHeaders.CONTENT_TYPE);

	    // Log or handle the detected type if needed
	    // System.out.println("Detected document type: " + detectedType);
	    logger.info("Detected document type: {}", detectedType);
	    String html = handler.toString();
	    if (removerTachado)
		html = removeStrikeThrough(html);
	    return html;
	}
    }

    /**
     * Converte um arquivo MS-Office para Markdown.
     * 
     * @param data - dados binários do arquivo
     * @return texto em formato Markdown
     *
     * @throws IOException
     * @throws TikaException
     * @throws Exception
     */
    public static String convertOfficeToMarkDown(byte[] data) throws IOException, TikaException, Exception {
	String html = convertOfficeToXHTML(data);
	return convertHTMLtoMarkdown(html);
    }

    /**
     * Converte um arquivo PDF para Markdown.
     *
     * @param data - dados binários do arquivo
     * @return texto em formato Markdown
     *
     * @throws IOException
     *
     */
    public static String convertPDFToMarkDown(byte[] data) throws IOException, TikaException, Exception {
	String html = convertPDFtoHTML(data);
	return convertHTMLtoMarkdown(html);
    }

    /**
     * Realiza um parse simples de um texto, recuperando blocos de texto. <br>
     * 
     * <pre>
     * Exemplo:
     * String texto =
     * """
     * reino: animal
     * filo: cordata
     * especie: Felis silvestris catus
     * """;
     *
     * Map<String,String> map = parseTexto(texto, "reino","filo", "especie");
     * System.out.println(map.toString());
    *  // {especie=Felis silvestris catus, filo=cordata, reino=animal}
     *
     * </pre>
     *
     *
     * @param texto    - texto a ser feito o parse
     * @param keywords - palavras Chave, ou delimitadores.
     * @return map com texto entre os marcadores
     */
    public static Map<String, String> parseTexto(String texto, String... keywords) {
	Map<String, String> res = new LinkedHashMap<>();

	TreeMap<Integer, String> mapIndexToText = new TreeMap<>(Collections.reverseOrder());
	String textLo = texto.toLowerCase();
	for (String key : keywords) {
	    int pos = textLo.indexOf(key.toLowerCase());
	    if (pos >= 0) {
		mapIndexToText.put(pos, key);
	    }
	}
	// recupera do fim para o inicio
	Set<Integer> posicoes = mapIndexToText.keySet();
	Integer end = texto.length();

	for (Integer pos : posicoes) {
	    String trecho = texto.substring(pos, end);
	    String kword = mapIndexToText.get(pos);
	    end = pos;

	    trecho = trecho.replaceFirst(kword, "").trim();
	    if (trecho.startsWith(":"))
		trecho = trecho.replaceFirst(":", "").trim();
	    if (trecho.startsWith("{"))
		trecho = trecho.replaceFirst("{", "").trim();

	    if (trecho.endsWith("-"))
		trecho = trecho.substring(0, trecho.length() - 1).trim();
	    if (trecho.endsWith("*"))
		trecho = trecho.substring(0, trecho.length() - 1).trim();
	    if (trecho.endsWith("}"))
		trecho = trecho.substring(0, trecho.length() - 1).trim();

	    res.put(kword, trecho);
	}
	return res;
    }

    /**
     * Faz quotes
     * 
     * @param s
     * @return
     */
    public static String quote(Object obj) {
	if (isEmpty(obj))
	    return "";
	if (obj instanceof String) {
	    String s = obj.toString();
	    s = s.replaceAll("\"", "\\\'");
	    s = s.replaceAll("\n", "\\\n ");
	    return "\"" + s.trim() + "\"";
	}
	if (obj instanceof Number) {
	    return ((Number) (obj)).toString();
	}

	return "\"" + obj.toString().trim() + "\"";
    }

    /**
     * Cria JSON a apartir de um texto semi estruturado
     * 
     * @param texto  - texto com marcadores
     * @param asList - true para retornar
     * @param campos
     * @return
     */
    public static String createJSON(String texto, String nomeLista, boolean asList, String... campos) {
	// remover marcações json
	texto = texto.replace("{", "\n").replace("},", "\n").replace("}", "\n").replace("[", "\n").replace("]", "\n")
		.replace("\n\n", "\n").trim();
	List<String[]> uplas = extractUplas(texto, true, campos);

	StringBuilder sb = new StringBuilder(1024);
	String quoteNome = quote(nomeLista);
	if (asList) {
	    if (!isEmpty(quoteNome)) {
		sb.append("{ ");
		sb.append(quoteNome);
		sb.append(": ");
	    }
	    sb.append("[ ");
	}
	int len = uplas.size();
	for (int ii = 0; ii < len; ii++) {
	    String[] dados = uplas.get(ii);
	    String acc = "";
	    for (int i = 0; i < campos.length; i++) {
		String c = campos[i];
		String dado = dados[i];
		if (!isEmpty(c) && !isEmpty(dado)) {
		    acc += quote(c);
		    acc += ": ";
		    acc += quote(dado);
		    if (i < (dados.length - 1))
			acc += ", \n  ";
		    else
			acc += "\n";
		}
	    }
	    if (!isEmpty(acc)) {
		sb.append("{ ");
		sb.append(acc);
		sb.append("}");
		if (ii < (len - 1)) {
		    sb.append(", \n ");
		}
	    }
	} // uplas
	if (asList) {
	    sb.append(" ]");
	    if (!isEmpty(quoteNome)) {
		sb.append("\n }");

	    }
	}
	return sb.toString().trim();
    }

    /**
     * Converte uma String estruturada em uma instância do tipo T.<br>
     *
     * @param <T>       - classe POJO a ser criada
     * @param tipo      - tipo do objeto.
     * @param texto     - texto semiestruturado
     * @param nomeLista - nome da lista, se aplicavel. Pode ser null ou vazia.
     * @param asList    - considera os dados como lista, acrescentando "[" e "]" ao
     *                  JSON gerado.
     * @param campos    - nome dos campos JSON, que devem coincidir com os atributos
     *                  do Objeto Tipo.
     *
     * @return instância de T
     */
    public static <T> T createJSONObject(Class<T> tipo, String texto, String nomeLista, boolean asList,
	    String... campos) {

	// primeiro, tentaremos converter diretamente
	{
	    var texto2 = removeEnumeracao(texto);
	    texto2 = checaFormato(texto2);
	    {
		T t = JsonUtil.converte(texto2, tipo);
		if (t != null)
		    return t;
	    }
	}

	String json = createJSON(texto, nomeLista, asList, campos);
	return JsonUtil.converte(json, tipo);
    }

    /**
     * remove agrupamento JSON, para facilitar a conversão
     * 
     * @param texto
     * @return
     */
    private static String checaFormato(String texto) {
	if (texto.contains("}"))
	    texto = texto.replaceAll("\\}\\s*\\{", "}, {");
	return texto;
    }

    private static String removeEnumeracao(String texto) {

	String[] linhas = texto.split("\n");

	for (int i = 0; i < linhas.length; i++) {
	    String l = linhas[i];
	    for (String c : LIXO_enum) {
		if (l.startsWith(c)) {
		    l = l.replaceFirst(c, "").trim();
		    linhas[i] = l;
		}
	    }
	}
	String acc = "";
	for (String element : linhas) {
	    acc += element;
	    acc += "\n";
	}

	return acc.trim();
    }

    /**
     * Tipos de prefixos e sufixos mais indesejados
     */
    private static String[] LIXO = { "-", "*", ",", "{", "}", "[", "]", ":", "###", "##", "#", "\t", "\"", "1.", "2.",
	    "3.", "4.", "5.", "6.", "7.", "8.", "9.", "10.", "1 ", "2 ", "3 ", "4 ", "5 ", "6 ", "7 ", "8 ", "9 ",
	    "10 " };

    private static String[] LIXO_enum = { "-", "*", ",", ":", "###", "##", "#", "\t", "1.", "2.", "3.", "4.", "5.",
	    "6.", "7.", "8.", "9.", "10.", "1 ", "2 ", "3 ", "4 ", "5 ", "6 ", "7 ", "8 ", "9 ", "10 " };

    /**
     *
     * @param texto
     * @return
     */
    public static String clean(String texto) {
	return clean(texto, LIXO);
    }

    /**
     * Remove partes prefixos e sufixos
     * 
     * @param texto - texto a ser limpo
     * @param lixo  - sufixos e apendices indesejados
     * @return
     */
    public static String clean(String texto, String... lixo) {
	String res = texto.trim();
	for (int ii = 0; ii < 2; ii++) {
	    for (int i = 0; i < lixo.length; i++) {
		for (String cc : lixo) {
		    if (res.startsWith(cc))
			res = res.replaceFirst(cc, "").trim();
		    if (res.endsWith(cc)) {
			res = res.substring(0, res.length() - cc.length()).trim();
		    }
		}
	    }
	}
	return res;
    }

    /**
     * Extrai uplas de texto
     * 
     * @param texto  semi estruturado -
     * @param asList - para retornar como lista
     * @param uplas  - nomes nos campos
     *
     * @return
     */
    public static List<String[]> extractUplas(String qa, boolean asList, String[] uplas) {
	if (qa.contains("{"))
	    qa = qa.replace("{", " ");
	if (qa.contains("}"))
	    qa = qa.replace("}", " ");
	Map<Integer, String> mapaUplas = new TreeMap<>(Collections.reverseOrder());
	String redu = qa.toLowerCase();
	int mark = 0;
	int max = 0;
	boolean end = false;
	do {
	    max = -1;
	    for (String u : uplas) {
		u = u.toLowerCase();
		int pos = redu.indexOf(u, mark);
		if (pos >= 0) {
		    pos = pos + u.length();
		    if (mapaUplas.containsKey(pos)) {
			end = true;
			break;
		    }
		    mapaUplas.put(pos, u);
		    max = Math.max(max, pos);
		}
	    }
	    mark = max;// leap-frog
	} while (max > 0 && !end);

	List<String[]> lista = new ArrayList<>();
	int len = uplas.length;
	String[] data = new String[len];
	redu = qa;
	lista.add(data);
	for (Map.Entry<Integer, String> entry : mapaUplas.entrySet()) {
	    Integer key = entry.getKey();
	    String val = entry.getValue();
	    String xtract = redu.substring(key);

	    xtract = xtract.replace(val, " ").trim();
	    xtract = clean(xtract);
	    xtract = escapeJsonSpecialCharacters(xtract);
	    int gap = val.length();
	    gap = (key - gap) < 0 ? key : key - gap;
	    redu = redu.substring(0, gap).trim();

	    int index = indexOf(val, uplas);
	    if (index >= 0) {
		if (data[index] == null)
		    data[index] = xtract;
		else {
		    data = new String[len];
		    data[index] = xtract;
		    lista.add(data);
		}
	    }
	}
	Collections.reverse(lista);
	return lista;
    }

    /**
     * Aplica scape de caracteres especiais para JSON
     * 
     * @param input
     * @return
     */
    public static String escapeJsonSpecialCharacters(String input) {
	if (input == null || input.isEmpty()) {
	    return input;
	}

	StringBuilder output = new StringBuilder();
	for (char c : input.toCharArray()) {
	    switch (c) {
	    case '"':
		output.append("\\\"");
		break;
	    case '\\':
		output.append("\\\\");
		break;
	    case '\b':
		output.append("\\b");
		break;
	    case '\f':
		output.append("\\f");
		break;
	    case '\n':
		output.append("\\n");
		break;
	    case '\r':
		output.append("\\r");
		break;
	    case '\t':
		output.append("\\t");
		break;
	    // Handle other special characters as needed
	    // Add cases for Unicode characters if necessary
	    default:
		output.append(c);
		break;
	    }
	}
	return output.toString();
    }

    /**
     * Colocar a thread requisitante em sleep por ms milisegundos
     * 
     * @param ms
     */
    public static void sleep(long ms) {
	try {
	    Thread.sleep(ms);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
    }

    /**
     * Pesquisa um valor em um array
     * 
     * @param s
     * @param arr
     * @return
     */
    public static int indexOf(String s, String[] arr) {
	for (int i = 0; i < arr.length; i++) {
	    if (s.equalsIgnoreCase(arr[i]))
		return i;
	}
	return -1;
    }

    /**
     * Método para remover palavras repetidas.<br>
     * Exemplo: <br>
     * entrada: "abelha abelha abelha rainha" <br>
     * saida: "abelha rainha" <br>
     *
     * @param input String de entrada
     * @return string sem palavras repetidas
     */
    public static String removeRepeatedWords(String input) {
	input = input + " ";
	Pattern pattern = Pattern.compile("\\b(\\w+)\\s+\\1\\b", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
	Matcher matcher = pattern.matcher(input);
	while (matcher.find()) {
	    input = input.replaceAll(matcher.group(), matcher.group(1));
	    matcher = pattern.matcher(input);
	}
	if (input.endsWith(" ")) {
	    input = input.substring(0, input.length() - 1);
	}
	return input;
    }

    /**
     * Método para remover sequências de palavras repetidas Exemplo: <br>
     * entrada: "abelha rainha abelha rainha" <br>
     * saida: "abelha rainha" <br>
     *
     * @param input String de entrada
     * @return string sem sequências de palavras repetidas
     *
     */
    public static String removeRepeatedPhrases(String input) {
	input = input.replace(".", " .").replace(",", " ,").replace("\n", " \n").replace("?", " ?").replace(";", " ;");

	input = input + " ";
	String regex = "((?:\\b\\w+\\b[\\n\\s\\.,\\?]*)+)\\1";
	Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.COMMENTS);
	Matcher matcher = pattern.matcher(input);
	while (matcher.find()) {
	    input = input.replaceAll(Pattern.quote(matcher.group()), matcher.group(1).trim());
	    matcher = pattern.matcher(input);
	}

	input = input.replace(" .", ".").replace(" ,", ",").replace(" \n", "\n").replace(" ?", "?").replace(" ;", ";");

	if (input.endsWith(" ")) {
	    input = input.substring(0, input.length() - 1);
	}
	return input;
    }

    /**
     * Elimita repetições de palavras e frases.<br>
     *
     * Exemplo: <br>
     * entrada: "abelha abelha abelha rainha rainha abelha rainha" <br>
     * saida: "abelha rainha" <br>
     *
     * @param texto
     * @return
     */
    public static String removerRepeticoes(String texto) {
	texto = removeRepeatedWords(texto);
	texto = removeRepeatedPhrases(texto);
	return texto;
    }

    /**
     * 
     * @param texto
     * @return
     * @throws LLMException 
     */
    public static int countTokens(String texto) throws LLMException {
	if (texto == null || texto.isEmpty()) {
	    return 0;
	}	
	LLMService service = getLLMService(null);
	return service.tokenCount(texto,"gpt-3.5");
    }
    
    /**
     * Fast token count estimation
     * @param texto
     * @return
     * @throws LLMException 
     */
    public static int countTokensFast(String texto) {
	if (texto == null || texto.isEmpty()) {
	    return 0;
	}	
	return texto.length() / 4; // estimativa simples
    }

    /**
     * Encontra o último delimitador em um texto, considerando que os delimitadores
     * são:<br>
     * "\n\n", "\n" e ".", "!" e ";".
     *
     * @param text - texto a ser analisado
     * @return índice do último delimitador encontrado ou -1 se não houver
     *         delimitador
     */
    public static int findLastDelimiter(String text) {
	if (text == null || text.isEmpty()) {
	    return -1; // Retorna -1 se o texto for nulo ou vazio
	}

	// Expressão regular para encontrar delimitadores na ordem de preferência
	String regex = "(\\n\\n)|(\\n)|([!.;])";
	Pattern pattern = Pattern.compile(regex);
	Matcher matcher = pattern.matcher(text);

	int lastIndex = -1;

	// Itera sobre todas as correspondências e armazena o índice da última
	while (matcher.find()) {
	    lastIndex = matcher.start();
	}

	return lastIndex; // Retorna o índice do último delimitador encontrado
    }

    /**
     * Concatena duas strings, removendo a eventual sobreposição entre elas. <br>
     * Exemplo: <br>
     * s1 = "Coloque os ovos numa panela" <br>
     * s2 = "numa panela e ferva em fogo alto" <br>
     * resultado = "Coloque os ovos numa panela e ferva em fogo alto" <br>
     *
     *
     * @param s1 - primeira string
     * @param s2 - segunda string
     * @return string s1 e s2 concatenadas, sem sobreposição
     */
    public static String concatenarSemRepetição(String s1, String s2) {
	if (s1 == null || s1.isEmpty())
	    return s2 == null ? "" : s2;
	if (s2 == null || s2.isEmpty())
	    return s1;

	// Verifica a maior sobreposição possível entre s1 e s2
	for (int i = 0; i < s1.length(); i++) {
	    // Se uma parte de s1 a partir de 'i' é igual ao começo de s2
	    if (s2.startsWith(s1.substring(i))) {
		// Retorna s1 até 'i' mais todo o s2
		return s1.substring(0, i) + s2;
	    }
	}
	// Se não houver sobreposição, apenas concatena s1 e s2
	return s1 + s2;
    }

    /**
     * Carrega um arquivo de texto.
     * 
     * @param caminhoDoArquivo
     * @return conteúdo do arquivo como String em UTF-8
     */
    public static String lerArquivoTexto(String caminhoDoArquivo) {
	String conteudo = "";
	if (caminhoDoArquivo == null || caminhoDoArquivo.isEmpty()) {
	    return conteudo;
	}
	if (caminhoDoArquivo.startsWith("http")) {
	    try {
		URL url = new URI(caminhoDoArquivo).toURL();
		return lerArquivoTexto(url);
	    } catch (Exception e) {
		e.printStackTrace();
		logger.error("Erro ao ler o arquivo: " + e.getMessage(), e);
		return null;
	    }
	}
	///
	try {
	    byte[] fileBytes = Files.readAllBytes(Paths.get(caminhoDoArquivo));

	    // Detect charset using Apache Tika
	    CharsetDetector detector = new CharsetDetector();
	    detector.setText(fileBytes);
	    CharsetMatch match = detector.detect();

	    // Convert file content to UTF-8
	    conteudo = new String(fileBytes, match.getName());
	    conteudo = new String(conteudo.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
	} catch (IOException e) {
	    e.printStackTrace();
	    logger.error("Erro ao ler o arquivo: " + e.getMessage(), e);
	}
	return conteudo;
    }

    public static String lerArquivoTexto(URL url) throws Exception {
	String conteudo = "";
	byte[] fileBytes = null;
	try {
	    // ler url http/https
	    OkHttpClient client = OkHttpProvider.getInstance().getOkHttpClient();
	    Request request = new Request.Builder().url(url).build();
	    try (Response response = client.newCall(request).execute()) {
		if (!response.isSuccessful()) {
		    throw new IOException("Unexpected code " + response);
		} else {
		    String contentType = response.header("Content-Type");
		    System.out.println("Content-Type: " + contentType);

		    if (contentType == null || !contentType.contains("html")) {
			throw new IOException("Unexpected content type " + contentType + " for URL " + url);
		    }
		    fileBytes = response.body().bytes();
		    // conteudo = response.body().string();
		}
	    }
	    // fileBytes = conteudo.getBytes();

	    // Detect charset using Apache Tika
	    CharsetDetector detector = new CharsetDetector();
	    detector.setText(fileBytes);
	    CharsetMatch match = detector.detect();

	    // Convert file content to UTF-8
	    if (!match.getName().equalsIgnoreCase("UTF-8")) {
		conteudo = new String(fileBytes, match.getName());
		conteudo = new String(conteudo.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
	    } else {
		conteudo = new String(fileBytes, StandardCharsets.UTF_8);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    logger.error("Erro ao ler o arquivo: " + e.getMessage(), e);
	    throw e;
	}
	return conteudo;
    }

    /**
     * Carrega um arquivo binário
     *
     * @param path - caminho do arquivo
     * @return byte array contendo os dados do arquivo
     */
    public static byte[] lerArquivoBinario(String path) {
	byte[] data = null;
	try {
	    data = Files.readAllBytes(Paths.get(path));
	} catch (IOException e) {
	    e.printStackTrace();
	    logger.error("Erro ao ler o arquivo: " + e.getMessage(), e);
	}
	return data;
    }

    /**
     * Cliente HTTP para uso seguro
     *
     * @param caminhoDoArquivo
     * @return
     */
    private static OkHttpClient client;

    /**
     * Recupera um cliente HTTP para uso seguro
     *
     * @return instancia de OkHttpClient, com cache e timeout configurados.
     */
    public static OkHttpClient getUnsafeOkHttpClient() {
	if (client == null) {
	    client = OkHttpProvider.getInstance().getOkHttpClient();
	}
	return client;
    }

    /**
     * Extrai um artigo da Wikipedia
     * 
     * @param articleTitle
     * @param language
     * @return
     */
    @Deprecated
    public static WikiParse wikiArticleXtract(String articleTitle, String language) {
	// Configura o cliente HTTP
	OkHttpClient client = RagUtils.getUnsafeOkHttpClient();

	// Monta a URL da API da Wikipedia para obter o conteúdo do artigo
	String url = String.format("https://%s.wikipedia.org/w/api.php?action=parse&page=%s&format=json&prop=text",
		language, articleTitle);

	print(LogColor.Purple, url);
	// Cria a requisição HTTP
	Request request = new Request.Builder().url(url).header("User-Agent",
		"Mozilla/5.0 (Windows NT 10; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.183 Safari/537.36")
		.header("Accept", "text/html,application/xhtml+xml,application/xml,text/json;").build();

	// Executa a requisição
	try (Response response = client.newCall(request).execute()) {
	    if (!response.isSuccessful())
		throw new IOException("Unexpected code " + response);
	    // Extrai a resposta
	    String responseData = response.body().string();
	    WikiParse wiki = JsonUtil.converte(responseData, WikiParse.class);
	    return wiki;
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }

    /**
     * Carrega o conteúdo de uma página da Wikipedia
     * 
     * @param url
     * @return
     * @throws IOException
     */
    public static String loadStringContent(String url) throws Exception {
	// Cria a requisição HTTP
	Request request = new Request.Builder().url(url).header(USER_AGENT, USER_AGENT_VALUE)
		.header(ACCEPT, ACCEPT_VALUE).build();

	// Executa a requisição
	try (Response response = getUnsafeOkHttpClient().newCall(request).execute()) {
	    if (!response.isSuccessful()) {
		System.err.println("## Deu ruim: " + url + "\n" + response.body().string());
		throw new IOException("Unexpected code " + response);
	    }

	    String responseData = response.body().string();
	    return responseData;
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }

    /**
     * Converte um objeto para String JSON
     * 
     * @param obj
     * @return
     */
    public static String toJson(Object obj) {
	return JsonUtil.toJson(obj);
    }

    /**
     * Converte um objeto para String JSON imprimivel
     * 
     * @param obj - objeto a ser convertido
     * @return string JSON
     */
    public static String toJsonPrint(Object obj) {
	return JsonUtil.toJsonPrint(obj);
    }

    public static <T> T convertFromJson(String json_string, Class<T> tipo) {
	return JsonUtil.fromJson(json_string, tipo);
    }

    /**
     * Limita o tamanho de um texto
     * 
     * @param texto - texto a ser limitado
     * @param i
     * @return
     */
    public static String limit(Object texto, int max) {
	if (texto == null)
	    return "";
	String text = texto.toString();
	if (text.length() <= max) {
	    return text;
	} else {
	    max = Math.min(max, text.length());
	    return text.substring(0, max) + "...";
	}
    }

    /**
     * Verifica se um texto é MarkDown
     * 
     * @param text texto a ser verificado
     * @return verdadeiro ou falso
     */
    public static boolean isMarkdown(String text) {
	return MarkdownDetector.isMarkdown(text);
    }

    /**
     * Detect if a text is markdown
     */
    public static class MarkdownDetector {
	private static final Pattern[] MARKDOWN_PATTERNS = { Pattern.compile("^#{1,6}\\s"), // Headers
		Pattern.compile("(?m)^\\s*[-*+]\\s"), // Unordered lists
		Pattern.compile("(?m)^\\s*\\d+\\.\\s"), // Ordered lists
		Pattern.compile("\\[.+?\\]\\(.+?\\)"), // Links
		Pattern.compile("(?m)^>\\s"), // Blockquotes
		Pattern.compile("`[^`]+`"), // Inline code
		Pattern.compile("(?m)^```"), // Code blocks
		Pattern.compile("\\*\\*[^*]+\\*\\*"), // Bold text
		Pattern.compile("\\*[^*]+\\*"), // Italic text
		Pattern.compile("!\\[.+?\\]\\(.+?\\)") // Images
	};

	public static boolean isMarkdown(String text) {
	    if (text == null || text.trim().isEmpty()) {
		return false;
	    }

	    for (Pattern pattern : MARKDOWN_PATTERNS) {
		if (pattern.matcher(text).find()) {
		    return true;
		}
	    }

	    return false;
	}
    }

    /**
     * Detecta o nome do arquivo a partir do caminho completo, que pode ser uma URL
     * ou um caminho de arquivo.
     *
     * @param pathName - string com url ou caminho de arquivo
     * @return nome isolado do arquivo
     */
    public static String detectaNome(String pathName) {
	if (pathName == null || pathName.isEmpty()) {
	    return null;
	}
	// Normalize path separators
	pathName = pathName.replace("\\", "/");
	// Find the last occurrence of the path separator
	int lastIndex = pathName.lastIndexOf("/");
	if (lastIndex >= 0 && lastIndex < pathName.length() - 1) {
	    return pathName.substring(lastIndex + 1);
	}
	return pathName;
    }

    /**
     * Veririfica se um texto é HTML / XHTML
     *
     * @param fileName - nome do arquivo
     * @return extensão do arquivo
     */
    public static boolean isHtml(String html) {
	if (html == null || html.isEmpty())
	    return false;
	String header = html.substring(0, Math.min(html.length(), 256));
	return header.toLowerCase().contains("<html");
    }

    /**
     * Formata uma data para o padrão yyyy-MM-dd
     * 
     * @param data
     * @return
     */
    public static String format(Date data) {
	return sdf.format(data);
    }

    /**
     * Formata uma data do padrão yyyy-MM-dd para Date
     * 
     * @param data_string
     * @return
     */
    public static Date parse(String data_string) {
	try {
	    return sdf.parse(data_string);
	} catch (ParseException e) {
	    e.printStackTrace();
	    return null;
	}
    }

    /**
     * 
     * @param data
     * @return
     */
    public static String convertBytes2Hexa(byte[] data) {
	if (data == null || data.length == 0) {
	    return "";
	}
	StringBuilder hexString = new StringBuilder();
	for (byte b : data) {
	    String hex = Integer.toHexString(0xFF & b);
	    if (hex.length() == 1) {
		hexString.append('0');
	    }
	    hexString.append(hex);
	}
	return hexString.toString().toLowerCase();
    }

    /**
     * Converte uma string hexadecimal para um array de bytes.
     *
     * @param hexa - string hexadecimal
     * @return array de bytes correspondente
     */
    public static byte[] convertHexa2Bytes(String hexa) {
	if (hexa == null || hexa.isEmpty()) {
	    return null;
	}
	byte[] data = new byte[hexa.length() / 2];
	for (int i = 0, j = 0; i < hexa.length(); i += 2) {
	    String byteString = hexa.substring(i, i + 2);
	    data[j++] = (byte) Integer.parseInt(byteString, 16);
	}
	return data;
    }

    /**
     * Calcula o checksum CRC32 de um array de bytes.<br>
     * 
     * Este método retorna o valor do checksum em formato hexadecimal, em letras
     * minúsculas. Para converter em formato inteiro, use:
     * 
     * <pre>
     * int crcInt = (int) Long.parseLong(crc32, 16);
     * 
     * </pre>
     *
     * @param data - array de bytes
     * @return valor do checksum CRC32, em formato hexadecimal
     */
    public static String getCRC32Checksum(byte[] data) {
	Checksum crc32 = new CRC32();
	crc32.update(data, 0, data.length);
	String hexString = Long.toHexString(crc32.getValue());
	return hexString.toLowerCase();
    }

    /**
     * Calcula o checksum CRC64 de um array de bytes.<br>
     * 
     * @param data
     * @return
     */
    public static String getCRC64Checksum(byte[] data) {
	if (data == null || data.length == 0) {
	    return "";
	}
	// CRC64
	Checksum crc64 = new CRC64();
	crc64.update(data);
	String hexString = Long.toHexString(crc64.getValue());
	return hexString.toLowerCase();
    }

    /**
     * Calcula o checksum MD5 de um array de bytes.
     * 
     * @param data
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String getMD5Checksum(byte[] data) throws NoSuchAlgorithmException {
	MessageDigest md = MessageDigest.getInstance("MD5");
	byte[] digest = md.digest(data);

	// Converte para hexadecimal
	StringBuilder sb = new StringBuilder();
	for (byte b : digest) {
	    sb.append(String.format("%02x", b));
	}
	return sb.toString().toLowerCase();
    }

    /**
     * Calcula o checksum SHA-256 de um array de bytes.
     *
     * @param data - array de bytes
     * @return valor do checksum SHA-256
     * @throws Exception
     */
    public static String getSHA256Checksum(byte[] data) throws Exception {
	MessageDigest digest = MessageDigest.getInstance("SHA-256");
	byte[] hash = digest.digest(data);

	StringBuilder hexString = new StringBuilder();
	for (byte b : hash) {
	    hexString.append(String.format("%02x", b));
	}
	return hexString.toString().toLowerCase();
    }

    /**
     * Verifica a integridade de um array de bytes, comparando com o checksum
     * fornecido.<br>
     * São suportados os seguintes tipos de checksum:<br>
     * 
     * - CRC32: 8 caracteres hexadecimais<br>
     * - CRC64: 16 caracteres hexadecimais<br>
     * - MD5: 32 caracteres hexadecimais<br>
     * - SHA-256: 64 caracteres hexadecimais<br>
     * 
     * @param data
     * @param checksum
     * @return
     * @throws Exception
     */
    public static boolean isChecksumValid(byte[] data, String checksum) throws Exception {

	if (data == null || data.length == 0 || checksum == null || checksum.isEmpty()) {
	    return false;
	}
	checksum = checksum.trim().toLowerCase();
	int len = checksum.length();
	// checksum CRC32 - 8 caracteres hexadecimais
	if (len <= 10) {
	    String crc32 = getCRC32Checksum(data);
	    if (crc32.equalsIgnoreCase(checksum))
		return true;
	    // lets check if the CRC was a plain integer value, as string, like "12345678"
	    int crcInt = (int) Long.parseLong(crc32, 16);
	    if (String.valueOf(crcInt).equalsIgnoreCase(checksum))
		return true;
	    return false;
	}
	// crc64 - 16 caracteres hexadecimais
	if (len <= 20) {
	    String crc64 = getCRC64Checksum(data);
	    return crc64.equalsIgnoreCase(checksum);
	}

	// checksum MD5 - 32 caracteres hexadecimais
	if (len <= 40) {
	    String md5 = getMD5Checksum(data);
	    return md5.equalsIgnoreCase(checksum);
	}
	// checksum SHA-256 - 64 caracteres hexadecimais
	if (len <= 70) {
	    String sha256 = getSHA256Checksum(data);
	    return sha256.equalsIgnoreCase(checksum);
	}

	return false;
    }

}
