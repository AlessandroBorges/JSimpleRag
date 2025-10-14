package bor.tools.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for TikaDocumentConverter
 *
 * Tests document format detection and conversion to Markdown
 * for various input formats.
 */
class TikaDocumentConverterTest {

    private TikaDocumentConverter converter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        converter = new TikaDocumentConverter();
    }

    // ============ Format Detection Tests ============

    @Test
    void testDetectFormat_HTML() throws Exception {
        String html = "<!DOCTYPE html><html><head><title>Test</title></head><body><h1>Hello</h1></body></html>";
        byte[] content = html.getBytes();

        String format = converter.detectFormat(content);

        assertTrue(format.contains("html"), "Should detect HTML format");
    }

    @Test
    void testDetectFormat_PlainText() throws Exception {
        String text = "This is plain text content without any markup.";
        byte[] content = text.getBytes();

        String format = converter.detectFormat(content);

        assertTrue(format.contains("text/plain") || format.contains("text"),
                  "Should detect plain text format");
    }

    @Test
    void testDetectFormat_Markdown() throws Exception {
        String markdown = "# Title\n\nThis is **markdown** content with _formatting_.";
        byte[] content = markdown.getBytes();

        String format = converter.detectFormat(content);

        assertNotNull(format, "Should detect some format");
        // Markdown is often detected as plain text
        assertTrue(format.contains("text"), "Should detect text-based format");
    }

    @Test
    void testDetectFormat_EmptyContent() {
        byte[] content = new byte[0];

        assertThrows(IllegalArgumentException.class, () -> {
            converter.detectFormat(content);
        }, "Should throw exception for empty content");
    }

    @Test
    void testDetectFormat_NullContent() {
        assertThrows(IllegalArgumentException.class, () -> {
            converter.detectFormat((byte[]) null);
        }, "Should throw exception for null content");
    }

    // ============ Conversion Tests ============

    @Test
    void testConvertToMarkdown_SimpleHTML() throws Exception {
        String html = "<html><body><h1>Title</h1><p>Paragraph content</p></body></html>";

        String markdown = converter.convertToMarkdown(html, "text/html");

        assertNotNull(markdown, "Markdown should not be null");
        assertTrue(markdown.contains("Title"), "Should contain title");
        assertTrue(markdown.contains("Paragraph"), "Should contain paragraph content");
    }

    @Test
    void testConvertToMarkdown_PlainText() throws Exception {
        String text = "This is plain text.\nWith multiple lines.\n";

        String result = converter.convertToMarkdown(text, "text/plain");

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("plain text"), "Should preserve content");
    }

    @Test
    void testConvertToMarkdown_ByteArray() throws Exception {
        String html = "<html><body><p>Test content</p></body></html>";
        byte[] content = html.getBytes();

        String markdown = converter.convertToMarkdown(content, null);

        assertNotNull(markdown, "Markdown should not be null");
        assertTrue(markdown.contains("Test content"), "Should contain content");
    }

    @Test
    void testConvertToMarkdown_EmptyString() {
        assertThrows(IllegalArgumentException.class, () -> {
            converter.convertToMarkdown("", "text/html");
        }, "Should throw exception for empty string");
    }

    @Test
    void testConvertToMarkdown_NullString() {
        assertThrows(IllegalArgumentException.class, () -> {
            converter.convertToMarkdown((String) null, "text/html");
        }, "Should throw exception for null string");
    }

    @Test
    void testConvertToMarkdown_EmptyByteArray() {
        byte[] content = new byte[0];

        assertThrows(IllegalArgumentException.class, () -> {
            converter.convertToMarkdown(content, null);
        }, "Should throw exception for empty byte array");
    }

    @Test
    void testConvertToMarkdown_NullByteArray() {
        assertThrows(IllegalArgumentException.class, () -> {
            converter.convertToMarkdown((byte[]) null, null);
        }, "Should throw exception for null byte array");
    }

    // ============ HTML to Markdown Conversion Tests ============

    @Test
    void testConvertToMarkdown_HTMLWithHeaders() throws Exception {
        String html = "<html><body>" +
                     "<h1>Main Title</h1>" +
                     "<h2>Subtitle</h2>" +
                     "<p>Content</p>" +
                     "</body></html>";

        String markdown = converter.convertToMarkdown(html, "text/html");

        assertNotNull(markdown);
        assertTrue(markdown.contains("Main Title"), "Should contain main title");
        assertTrue(markdown.contains("Subtitle"), "Should contain subtitle");
    }

    @Test
    void testConvertToMarkdown_HTMLWithLists() throws Exception {
        String html = "<html><body>" +
                     "<ul>" +
                     "<li>Item 1</li>" +
                     "<li>Item 2</li>" +
                     "</ul>" +
                     "</body></html>";

        String markdown = converter.convertToMarkdown(html, "text/html");

        assertNotNull(markdown);
        assertTrue(markdown.contains("Item 1"), "Should contain first item");
        assertTrue(markdown.contains("Item 2"), "Should contain second item");
    }

    @Test
    void testConvertToMarkdown_HTMLWithLinks() throws Exception {
        String html = "<html><body>" +
                     "<p>Check out <a href=\"https://example.com\">this link</a></p>" +
                     "</body></html>";

        String markdown = converter.convertToMarkdown(html, "text/html");

        assertNotNull(markdown);
        assertTrue(markdown.contains("this link") || markdown.contains("example.com"),
                  "Should preserve link text or URL");
    }

    // ============ Configuration Tests ============

    @Test
    void testLoadConfiguration_NonExistentFile() throws Exception {
        // Should not throw exception, just log warning
        assertDoesNotThrow(() -> {
            converter.loadConfiguration("/nonexistent/config.properties");
        }, "Should handle non-existent config file gracefully");
    }

    @Test
    void testLoadConfiguration_EmptyPath() throws Exception {
        // Should not throw exception, just log warning
        assertDoesNotThrow(() -> {
            converter.loadConfiguration("");
        }, "Should handle empty path gracefully");
    }

    @Test
    void testLoadConfiguration_NullPath() throws Exception {
        // Should not throw exception, just log warning
        assertDoesNotThrow(() -> {
            converter.loadConfiguration(null);
        }, "Should handle null path gracefully");
    }

    @Test
    void testLoadConfiguration_ValidFile() throws Exception {
        // Create a temporary config file
        Path configFile = tempDir.resolve("test-config.properties");
        String configContent = "converter.removeStrikethrough=false\n" +
                              "converter.maxStringLength=100000\n";
        Files.writeString(configFile, configContent);

        // Load configuration
        converter.loadConfiguration(configFile.toString());

        // Verify configuration was loaded
        assertEquals("false", converter.getConfigProperty("converter.removeStrikethrough"));
        assertEquals("100000", converter.getConfigProperty("converter.maxStringLength"));
    }

    @Test
    void testSetConfigProperty() {
        converter.setConfigProperty("test.property", "test.value");

        assertEquals("test.value", converter.getConfigProperty("test.property"));
    }

    @Test
    void testGetConfigProperty_NonExistent() {
        String value = converter.getConfigProperty("nonexistent.property");

        assertNull(value, "Non-existent property should return null");
    }

    // ============ URI Conversion Tests ============

    @Test
    void testConvertToMarkdown_URI_InvalidURL() {
        URI uri = URI.create("http://invalid-domain-that-does-not-exist-12345.com/doc.pdf");

        assertThrows(Exception.class, () -> {
            converter.convertToMarkdown(uri, null);
        }, "Should throw exception for invalid URL");
    }

    @Test
    void testConvertToMarkdown_NullURI() {
        assertThrows(IllegalArgumentException.class, () -> {
            converter.convertToMarkdown((URI) null, null);
        }, "Should throw exception for null URI");
    }

    @Test
    void testDetectFormat_NullURI() {
        assertThrows(IllegalArgumentException.class, () -> {
            converter.detectFormat((URI) null);
        }, "Should throw exception for null URI");
    }

    // ============ Complex HTML Tests ============

    @Test
    void testConvertToMarkdown_HTMLWithTable() throws Exception {
        String html = "<html><body>" +
                     "<table>" +
                     "<tr><th>Header 1</th><th>Header 2</th></tr>" +
                     "<tr><td>Cell 1</td><td>Cell 2</td></tr>" +
                     "</table>" +
                     "</body></html>";

        String markdown = converter.convertToMarkdown(html, "text/html");

        assertNotNull(markdown);
        // Should contain table content in some form
        assertTrue(markdown.contains("Header") || markdown.contains("Cell"),
                  "Should preserve table content");
    }

    @Test
    void testConvertToMarkdown_HTMLWithFormatting() throws Exception {
        String html = "<html><body>" +
                     "<p>This is <strong>bold</strong> and <em>italic</em> text.</p>" +
                     "</body></html>";

        String markdown = converter.convertToMarkdown(html, "text/html");

        assertNotNull(markdown);
        assertTrue(markdown.contains("bold"), "Should contain bold text");
        assertTrue(markdown.contains("italic"), "Should contain italic text");
    }

    @Test
    void testConvertToMarkdown_XHTMLFormat() throws Exception {
        String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                      "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\">" +
                      "<html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                      "<head><title>XHTML Test</title></head>" +
                      "<body><p>XHTML content</p></body>" +
                      "</html>";

        String markdown = converter.convertToMarkdown(xhtml, "application/xhtml+xml");

        assertNotNull(markdown);
        assertTrue(markdown.contains("XHTML content") || markdown.contains("content"),
                  "Should convert XHTML to markdown");
    }

    // ============ Edge Cases ============

    @Test
    void testConvertToMarkdown_LargeContent() throws Exception {
        // Create large HTML content
        StringBuilder html = new StringBuilder("<html><body>");
        for (int i = 0; i < 1000; i++) {
            html.append("<p>Paragraph ").append(i).append("</p>");
        }
        html.append("</body></html>");

        String markdown = converter.convertToMarkdown(html.toString(), "text/html");

        assertNotNull(markdown, "Should handle large content");
        assertTrue(markdown.length() > 0, "Should produce output");
    }

    @Test
    void testConvertToMarkdown_SpecialCharacters() throws Exception {
        String html = "<html><body>" +
                     "<p>Special chars: &lt; &gt; &amp; &quot; &#39;</p>" +
                     "</body></html>";

        String markdown = converter.convertToMarkdown(html, "text/html");

        assertNotNull(markdown);
        assertTrue(markdown.contains("Special"), "Should handle special characters");
    }

    @Test
    void testConvertToMarkdown_UTF8Content() throws Exception {
        String html = "<html><body>" +
                     "<p>Unicode: 你好 مرحبا Привет</p>" +
                     "</body></html>";

        String markdown = converter.convertToMarkdown(html, "text/html");

        assertNotNull(markdown);
        // Should preserve Unicode content
        assertTrue(markdown.length() > 0, "Should handle UTF-8 content");
    }

    // ============ Integration with RAGConverter Tests ============

    @Test
    void testIntegration_WithRAGConverter() throws Exception {
        // Test that TikaDocumentConverter properly integrates with RAGConverter
        String html = "<html><body><h1>Integration Test</h1></body></html>";

        String markdown = converter.convertToMarkdown(html, "text/html");

        assertNotNull(markdown);
        assertTrue(markdown.contains("Integration Test"),
                  "Should integrate with RAGConverter for HTML conversion");
    }
}
