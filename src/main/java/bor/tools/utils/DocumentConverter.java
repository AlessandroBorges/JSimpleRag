package bor.tools.utils;

import java.net.URI;

/**
 * Interface for converting documents between different formats. <br>
 * It may include methods for converting from formats like PDF, DOCX, TXT, HTML, etc.
 * to MarkDown. <br>	
 * 
 *   
 *  <ul> It may be implemented using one or more of the following libraries/tools:  
 *  <li> Apache Tika, as Java direct implementation - https://tika.apache.org/
 *  <li> Pandoc -  http://pandoc.org/
 *  <li> Docling Service - https://docling.ai/
 *  <li> Web services, like Cloudmersive - https://www.cloudmersive.com/convert-api
 *  <li> Custom parsers for specific formats, like the XHTMLToMarkdownParser class.
 *  <li> Local installation of LibreOffice/OpenOffice with JODConverter -
 *  </ul> 
 *  
 *  Aditional libraries that may be useful:
 *  <ul>
 *  <li> Flexmark - 
 *  <li> Apache POI - https://poi.apache.org/ (for DOCX)
 *  <li> PDFBox - https://pdfbox.apache.org/ (for PDF)
 *  <li> iText - https://itextpdf.com/en (for PDF)
 *  <li> docx4j - https://www.docx4java.org/trac/docx4j (for DOCX)
 *  <li> JODConverter - 
 *  </ul>
 *  
 *  Create implementations that can be configured via constructor parameters or 
 *  setter methods.
 *  Or use a Property file to configure the implementation. <br>
 *  Suggested filename: document-converter.properties<br>
 *  
 *  <ul> Configuration options may include:
 *  <li> Choice of conversion library/tool
 *  <li> API keys for web services
 *  <li> Timeout settings for web requests
 *  <li> Fallback strategies if one method fails
 *  <li> Keep or discard strikethrough text
 *  <li> Handle images (embed as base64, link to external files, etc)
 *  <li> Batch Processing: Consider adding methods for batch conversion of multiple documents.
 *  <li> Asynchronous Processing: For larger documents, asynchronous processing methods would be beneficial.
 *  </ul>
 *  
 */	
public interface DocumentConverter {
    
    /**
     * Default configuration file name for the DocumentConverter implementations.
     */	
    public static final String DEFAULT_CONFIG_FILE = "document-converter.properties";
    
    
    /**
     * Loads configuration settings from the specified file path.
     * 
     * @param configFilePath The path to the configuration file.
     * @throws Exception If an error occurs while loading the configuration.
     */
    void loadConfiguration(String configFilePath) throws Exception;
    

    /**
     * Converts the input document content to MarkDown format.
     * 
     * @param inputContent The content of the document to be converted.
     * @param inputFormat  The format of the input document (e.g., "pdf", "docx",
     *                     "txt").
     * @return The converted content in MarkDown format.
     * @throws Exception If an error occurs during conversion.
     */
    String convertToMarkdown(String inputContent, String inputFormat) throws Exception;
    
       
    /**
     * Converts the document located at the given URI to MarkDown format.
     * 
     * @param contentSource The URI of the document to be converted.
     * @param inputFormat   The format of the input document (e.g., "pdf", "docx",
     *                      "txt"). Null or empty if format should be auto-detected.
     * @return The converted content in MarkDown format.
     * @throws Exception If an error occurs during conversion.
     */	
    String convertToMarkdown(URI contentSource, String inputFormat) throws Exception;
    
    /**
     * Converts the input document content (as byte array) to MarkDown format.
     * 
     * @param content     The byte array representing the document content to be
     *                    converted.
     * @param inputFormat The format of the input document (e.g., "pdf", "docx",
     *                    "txt"). null or empty if format should be auto-detected.
     *                    
     * @return The converted content in MarkDown format.
     * 
     * @throws Exception If an error occurs during conversion.
     */
    String convertToMarkdown(byte[] content, String inputFormat) throws Exception;
    
    /**
     * Detects the format of the document located at the given URI.
     * Easily extensible to detect format from byte array or string content.
     * Default implementation may use Apache Tika or similar library.
     * 
     * @param contentSource The URI of the document whose format is to be detected.
     * @return The detected format of the document (e.g., "pdf", "docx", "txt").
     * @throws Exception If an error occurs during format detection.
     */
    String detectFormat(URI contentSource) throws Exception;
    
    /**
     * <h3>Detect using just a small sample of the header</h3>
     * <p> There is no need to send the whole document, just a small 
     * sample of the header is enough.
     * </p>
     * <p>
     * This method is useful when you have the document content as a byte array
     * </p>
     * 
     * Detects the format of the document represented by the given byte array.<br>
     * Easily extensible to detect format from URI or string content. <br>
     * Default implementation may use Apache Tika or similar library. <br>
     * 
     * @param contentSample The document header as byte array (sample) whose format is to be detected.
     * 
     * @return The detected format of the document (e.g., "pdf", "docx", "txt").
     * 
     * @throws Exception If an error occurs during format detection.
     */
    String detectFormat(byte[] contentSample) throws Exception;
    
}
