package bor.tools.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
//import org.xml.sax.SAXException;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;


/**
 * This class is responsible for parsing XHTML content and converting it to
 * Markdown format. It extends the AbstractParser class from Apache Tika.
 * Example usage:<br>
 *
 * <pre>
 * String htmlContent = "..."; // Your HTML/XHTML content
 * XHTMLToMarkdownParser parser = new XHTMLToMarkdownParser();
 * String markdown = parser.convertToMarkdown(htmlContent);
 *
 * </pre>
 */
public class XHTMLToMarkdownParser {

	private static final Set<MediaType> SUPPORTED_TYPES = new HashSet<>();
    static {
        SUPPORTED_TYPES.add(MediaType.application("xhtml+xml"));
        SUPPORTED_TYPES.add(MediaType.text("html"));
        SUPPORTED_TYPES.add(MediaType.application("html"));
    }

    private FlexmarkHtmlConverter converter;

	/**
	 * Constructor
	 */
	public XHTMLToMarkdownParser() {
		// Configure Flexmark converter with options for both HTML and XHTML
		MutableDataSet options = new MutableDataSet();
		options.set(Parser.EXTENSIONS,
		            Arrays.asList(TablesExtension.create(),
		                          AutolinkExtension.create(),
		                          StrikethroughExtension.create()));
        this.converter = FlexmarkHtmlConverter.builder(options).build();
	}


    /**
     * Retrieves the set of supported media types for this parser.
     *
     * @return A set of MediaType objects representing the supported content types,
     *         which include XHTML and HTML formats.
     */
    public Set<MediaType> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }



    /**
     * Checks if the content type is supported by this parser
     *
     * @param contentType the MIME type to check
     * @return true if the content type is supported
     */
    public boolean isSupported(String contentType) {
        return SUPPORTED_TYPES.stream()
                .anyMatch(type -> type.toString().equals(contentType));
    }

    /**
     * Converts HTML or XHTML content to Markdown format.
     * @param content
     * @return
     * @throws IOException
     * @throws TikaException
     * @throws SAXException
     */
	public String convertToMarkdown(String content) throws IOException,Exception{
		String contentType = RAGUtil.detectMimeTypeTika(content);
		return convertToMarkdown(content, contentType);
    }


    /**
     * Converts HTML or XHTML content to Markdown format.
     *
     * @param content The HTML or XHTML content to convert
     * @param contentType The content type ("text/html" or "application/xhtml+xml")
     * @return The converted Markdown content
     *
     * @throws IllegalArgumentException if the content is null or not supported
     * @throws TikaException if there is an error during parsing
     * @throws IOException if there is an error reading the input
     * @throws SAXException if there is an error processing the document
     */
    public String convertToMarkdown(String content, String contentType)
            throws IOException, Exception {

        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }

        if (!this.isSupported(contentType)) {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
        String markdown = this.converter.convert(content);
        return markdown;

    }

}

