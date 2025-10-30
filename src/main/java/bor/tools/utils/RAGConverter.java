package bor.tools.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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


import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

/**
 * Class to convert input raw  RAG data to digesteble formats, like
 * Markdown, HTML, plain text, etc.
 * Esta versão usará o Apache Tika para converter os documentos
 * 
 */
public class RAGConverter {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(RAGConverter.class);
	/**
	 * MARKDOWN parser
	 */
	private static XHTMLToMarkdownParser markdownParser = new XHTMLToMarkdownParser();
	
	/**
	 * Tamanho máximo de conversão para Tika
	 */
	public static int MAX_STRING_LENGTH = 1024*200;
	
	public static final String[] EXTENSOES_TEXTO = { ".txt", ".md", ".markdown", ".html",
            ".xhtml", ".pdf", ".doc", ".docx",
            ".xls", ".xlsx", ".ppt", ".pptx" };

	protected static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

	public static boolean removerTachado = true;
	
	/**
	 * Tipos MIME de arquivos MS-Office
     *
	 */
	public static final String
	
	        MIME_MS_WORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			MIME_MS_EXCEl="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			MIME_MS_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
			MIME_MS_DOC = "application/msword",
			MIME_MS_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			MIME_MS_XLS = "application/vnd.ms-excel",
			MIME_MS_PPT_LEGACY = "application/vnd.ms-powerpoint",
			MIME_MS_PPT = "application/vnd.ms-power",
			
			// outros tipos MIME	
			MIME_PDF = "application/pdf",
			MIME_HTML = "text/html",
			MIME_XHTML = "application/xhtml+xml",
			MIME_TEXT = "text/plain",
			MIME_MARKDOWN = "text/markdown",
			MIME_XML = "application/xml",
			MIME_JSON = "application/json",
			MIME_RTF = "application/rtf"
			;

	/**
	 * Tipos MIME suportados
	 * @author aless
	 *
	 */
	public enum MimeType {
		// Mime MS-Office
	    MIME_MS_WORD("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
	    MIME_MS_DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
	    MIME_MS_DOC("application/msword"),
	    MIME_MS_EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
	    MIME_MS_PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
	    MIME_MS_PPT_LEGACY("application/vnd.ms-power"),
	    MIME_MS_PPT("application/vnd.ms-powerpoint"),
	   
	    MIME_MS_XLS("application/vnd.ms-excel"),
	    MIME_MS_XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
	   
	    
	    // outros tipos MIME
	    MIME_PDF("application/pdf"),
	    MIME_HTML("text/html"),
	    MIME_XHTML("application/xhtml+xml"),
	    MIME_TEXT("text/plain"),
	    MIME_MARKDOWN("text/markdown"),
	    MIME_XML("application/xml"),
	    MIME_JSON("application/json"),
	    MIME_RTF("application/rtf"),	    
	    DESCONHECIDO("application/octet-stream");

	    private final String mimeType;

	    MimeType(String mimeType) {
	        this.mimeType = mimeType;
	    }

	    public String getMimeType() {
	        return mimeType;
	    }
	    
	    /**
	     * Retorna o tipo MIME como uma string.
	     *
	     * @return string do tipo MIME
	     */
	    public String toString() {
	        return mimeType;
	    }
	    
	    public boolean isMSOffice() {
	        return this==MimeType.MIME_MS_WORD ||
	               this==MimeType.MIME_MS_EXCEL ||
	               this==MimeType.MIME_MS_PPTX ||
	               this==MimeType.MIME_MS_PPT ||
	               this==MimeType.MIME_MS_DOCX ||
	               this==MimeType.MIME_MS_DOC ||
	               this==MimeType.MIME_MS_XLS ||
	               this==MimeType.MIME_MS_XLSX;
	    }
	    
	    
	    public boolean isPDF() {
	        return this==MimeType.MIME_PDF;
	    }
	    
	    public boolean isTextual() {
	        return this==MimeType.MIME_TEXT ||
	               this==MimeType.MIME_HTML ||
	               this==MimeType.MIME_XHTML ||
	               this==MimeType.MIME_MARKDOWN ||
	               this==MimeType.MIME_XML ||
	               this==MimeType.MIME_JSON ;
	    }
	    
	    
	    /**
	     * Converte uma string para o tipo MIME correspondente.
	     *
	     * @param mimeType - string do tipo MIME
	     * @return MimeType correspondente ou DESCONHECIDO se não encontrado
	     */
	    public static MimeType fromString(String mimeType) {
	        for (MimeType type : MimeType.values()) {
	            if (type.mimeType.equalsIgnoreCase(mimeType)) {
	                return type;
	            }
	        }
	        logger.warn("Tipo MIME desconhecido: {}", mimeType);
	        return DESCONHECIDO;
	    }
	}

	
	
	/**
	 * Identifica o tipo MIME do objeto string, possivelmente de um arquivo textual,
	 * usando Apache Tika.
	 * @param header - cabeçalho do arquivo
	 * @return tipo MIME
	 * @throws IOException
	 */
	public static MimeType detectMimeTypeTika(String header ) throws IOException {
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
	 *  usando Apache Tika.
	 *
	 * @return
	 * @throws IOException
	 */
	public static MimeType detectMimeTypeTika(byte[] data) throws IOException {
		 if(tika==null)
		   tika = new Tika();
		 int size = Math.min(data.length, 256);
		 byte[] prefix = new byte[size];
		 System.arraycopy(data, 0, prefix, 0, size);
		 String mime =  tika.detect(data);
		 if(mime==null || mime.isEmpty()) {
			 logger.info("MIME detectado: {}", mime);
			 logger.warn("MIME não detectado, retornando DESCONHECIDO");
			 return MimeType.DESCONHECIDO;
		 }
		 logger.info("MIME detectado: {}", mime);
		 MimeType mt = MimeType.fromString(mime);
		 return mt;
		 
	}
	
	/**
	 * Converte um array de bytes de um documento em um Markdown simples,
	 * usando Apache Tika para converter tipos MIME suportados
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
		MimeType mime = detectMimeTypeTika(html);
		if (mime.equals(MimeType.MIME_HTML) || mime.equals(MimeType.MIME_XHTML)) {
			return convertHTMLtoMarkdown(html);
		}
		int len = Math.min(html.length(), 100);
		logger.info("MIME: {} - Content: {}", mime, html.substring(0, len));
		// retorna texto plano
		return html;
	}
	
	/**
	 * Converte um texto HTML/XHTML para Markdown.
	 * @param html_content - conteudo HTML
	 * @return
	 * @throws IOException
	 * @throws TikaException
	 * @throws Exception
	 */
	public static String convertToMarkdown(String html_content) throws IOException, TikaException, Exception {
		MimeType mime = detectMimeTypeTika(html_content);
		String content = "";
		if (mime.equals(MimeType.MIME_HTML) || mime.equals(MimeType.MIME_XHTML)) {
			content = convertHTMLtoMarkdown(html_content);
		}
		else {
			// possivelmente é texto plano
			content = html_content;
		}
		int len = Math.min(content.length(), 100);
		logger.info("MIME: {} - Content: {}", mime, content.substring(0, len));
		return content;

	}
	
	/**
	 * Converte um array de bytes de um documento em uma HTML simples.
	 * Pode retornar apenas texto plano simples, se o formato original não for suportado.
	 *
	 * @param data - data binário
	 * @return texto HTML extraído do arquivo binário
	 *
	 * @throws IOException
	 * @throws TikaException
	 * @throws Exception
	 */
	public static String convertToHTML(byte[] data) throws IOException, TikaException, Exception {
		MimeType mime = detectMimeTypeTika(data);
		String content_html = "";

		// use Apache Tika to extract text from the binary data, possible a PDF file
		// or a docx file
		if (mime == MimeType.MIME_PDF) {
			content_html = convertPDFtoHTML(data);
		}
		else
			// converte arquivos MS-Office
		if (mime.isMSOffice())  // MIME_MS_WORD, MIME_MS_EXCEL, MIME_MS_PPTX, MIME_MS_DOCX, MIME_MS_DOC, MIME_MS_XLS
		{
			content_html = convertOfficeToXHTML(data);
			if (removerTachado)
				content_html = removeStrikeThrough(content_html);

		} else if (mime.isTextual()) {
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

		if(removerTachado)
            content_html = removeStrikeThrough(content_html);

		return content_html;
	}
	
	/**
	 * Converte um array de bytes de um documento em uma string simples,
	 * usando Apache Tika para converter tipos MIME suportados
	 *
	 * @param data - data binário
	 * @return texto extraído do arquivo binário
	 * @throws IOException
	 * @throws Exception
	 * @throws TikaException
	 */
	public static String extractPlainText(byte[] data, boolean removerTachado) throws IOException, TikaException, Exception {
		MimeType mime = detectMimeTypeTika(data);
		String content_html = convertToHTML(data);

		if (content_html == null || content_html.isEmpty())
            return null;

		if(removerTachado)
            content_html = removeStrikeThrough(content_html);
		
		String content = convertHTMLtoPlainText(content_html);
		int len = Math.min(content.length(), 100);
		logger.info("Extract plain text MIME: {} - Content: {}", mime, content.substring(0, len));
		return content;
	}
	
	/**
	 * Converte um array de bytes de um documento em uma string simples.
	 * Nota:
	 * - Se o arquivo for um PDF, o texto será extraído usando Apache Tika.
	 * - Tabelas e cabeçalhos serão tratados de forma especial.
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
		MimeType mime = detectMimeTypeTika(html);
		if (!(mime==MimeType.MIME_HTML) && !(mime == MimeType.MIME_XHTML)) {
			throw new IllegalArgumentException("Unsupported content type: " + mime);
		}
		if(removerTachado)
            html = removeStrikeThrough(html);

		String md = markdownParser.convertToMarkdown(html, mime.toString());
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
	 *  Extrai texto de um arquivo MS-Office
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
	 * @return HTML formatted text extracted from the PDF, or empty string if extraction fails
	 * @throws TikaException if there is an error during parsing
	 * @throws IOException if there is an error reading the input
	 * @throws Exception if there is an error processing the document
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
	 * Extracts plain text from HTML/XHTML with special handling for tables and headers.
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
	            if (tagName.equals("p") || tagName.equals("div") ||
	                tagName.equals("br") || tagName.equals("li")) {
	                result.append(element.text()).append("\n");
	                continue;
	            }

	            // Handle inline elements
	         // Handle inline elements
	            var parent = element==null ? null : element.parent();
	            if (element != null && !element.text().trim().isEmpty() &&
	                (parent !=null && parent.tagName() != null 
	                 && !parent.tagName().matches("p|div|h[1-6]|li|table")
	                 )
	                ) 
	            {
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
	 * Cleans up the extracted text by removing extra whitespace and normalizing line breaks.
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
	 * Converts Microsoft Office documents (DOCX, PPTX, XLSX) to XHTML format.
	 * The document type is automatically detected at runtime.
	 *
	 * @param data byte array containing the document
	 * @return XHTML formatted text extracted from the document, or empty string if extraction fails
	 * @throws TikaException if there is an error during parsing
	 * @throws IOException if there is an error reading the input
	 * @throws Exception if there is an error processing the document
	 */
	public static String convertOfficeToXHTML(byte[] data) throws IOException, TikaException, Exception {
	    if (data == null || data.length == 0) {
	        //throw new IllegalArgumentException("Input data cannot be null or empty");
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
	 * @param texto - texto a ser feito o parse
	 * @param keywords - palavras Chave, ou delimitadores.
	 * @return map com texto entre os marcadores
	 */
	public static Map<String,String> parseTexto(String texto, String...keywords){
		Map<String,String> res = new LinkedHashMap<>();

		TreeMap<Integer,String> mapIndexToText = new TreeMap<>(Collections.reverseOrder());
		String textLo = texto.toLowerCase();
		for(String key : keywords) {
			int pos = textLo.indexOf(key.toLowerCase());
			if(pos>=0) {
				mapIndexToText.put(pos, key);
			}
		}
		// recupera do fim para o inicio
		Set<Integer> posicoes = mapIndexToText.keySet();
		Integer end = texto.length();

		for(Integer pos : posicoes) {
			String trecho = texto.substring(pos, end);
			String kword = mapIndexToText.get(pos);
			end = pos;

			trecho = trecho.replaceFirst(kword, "").trim();
			if(trecho.startsWith(":")) trecho = trecho.replaceFirst(":", "").trim();
			if(trecho.startsWith("{")) trecho = trecho.replaceFirst("{", "").trim();

			if(trecho.endsWith("-")) trecho = trecho.substring(0, trecho.length()-1).trim();
			if(trecho.endsWith("*")) trecho = trecho.substring(0, trecho.length()-1).trim();
			if(trecho.endsWith("}")) trecho = trecho.substring(0, trecho.length()-1).trim();


			res.put(kword, trecho);
		}
		return res;
	}

	/**
	 * Faz quotes
	 * @param s
	 * @return
	 */
	public static String quote(Object obj) {
		if(RagUtils.isEmpty(obj))
			return "";
		if(obj instanceof String) {
			String s = obj.toString();
			s = s.replaceAll("\"","\\\'");
			s = s.replaceAll("\n", "\\\n ");
			return "\"" + s.trim() + "\"" ;
		}
		if(obj instanceof Number) {
			return ((Number)(obj)).toString();
		}

		return "\"" + obj.toString().trim() + "\"" ;
	}


	/**
	 * Veririfica se um texto é HTML / XHTML
	 *
	 * @param fileName - nome do arquivo
	 * @return extensão do arquivo
	 */
	public static boolean isHtml(String html) {
		if(html==null || html.isEmpty())
            return false;
		String header = html.substring(0, Math.min(html.length(), 256));
		return  header.toLowerCase().contains("<html") 
				|| header.toLowerCase().contains("<!doctype html")
				|| header.toLowerCase().contains("<head")
				|| header.toLowerCase().contains("<body")
				|| header.toLowerCase().contains("<html xmlns")
				|| header.toLowerCase().contains("<?xml");
	}
	
	/**
     * Verifica se um texto é MarkDown
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
	    private static final Pattern[] MARKDOWN_PATTERNS = {
	        Pattern.compile("^#{1,6}\\s"),              // Headers
	        Pattern.compile("(?m)^\\s*[-*+]\\s"),       // Unordered lists
	        Pattern.compile("(?m)^\\s*\\d+\\.\\s"),     // Ordered lists
	        Pattern.compile("\\[.+?\\]\\(.+?\\)"),      // Links
	        Pattern.compile("(?m)^>\\s"),               // Blockquotes
	        Pattern.compile("`[^`]+`"),                 // Inline code
	        Pattern.compile("(?m)^```"),                // Code blocks
	        Pattern.compile("\\*\\*[^*]+\\*\\*"),       // Bold text
	        Pattern.compile("\\*[^*]+\\*"),             // Italic text
	        Pattern.compile("!\\[.+?\\]\\(.+?\\)")      // Images
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


	

}
