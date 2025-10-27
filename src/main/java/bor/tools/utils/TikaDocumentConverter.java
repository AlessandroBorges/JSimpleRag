package bor.tools.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Apache Tika-based implementation of the DocumentConverter interface.
 *
 * Provides document format detection and conversion to Markdown format.
 * Supports various document formats including PDF, MS Office (DOCX, XLSX, PPTX),
 * HTML, XHTML, and plain text.
 *
 * This implementation leverages the existing RAGConverter class which already
 * implements Tika-based conversion functionality.
 *
 * @see DocumentConverter
 * @see RAGConverter
 */
@Component
public class TikaDocumentConverter implements DocumentConverter {

    private static final Logger logger = LoggerFactory.getLogger(TikaDocumentConverter.class);

    /**
     * Configuration properties
     */
    private Properties config;

    /**
     * Default constructor with default configuration
     */
    public TikaDocumentConverter() {
        this.config = new Properties();
        // Set default configuration
        config.setProperty("converter.removeStrikethrough", "true");
        config.setProperty("converter.maxStringLength", String.valueOf(RAGConverter.MAX_STRING_LENGTH));
    }

    /**
     * Constructor with custom configuration file
     *
     * @param configFilePath Path to configuration file
     * @throws Exception If configuration loading fails
     */
    public TikaDocumentConverter(String configFilePath) throws Exception {
        this();
        loadConfiguration(configFilePath);
    }

    @Override
    public void loadConfiguration(String configFilePath) throws Exception {
        if (configFilePath == null || configFilePath.trim().isEmpty()) {
            logger.warn("Empty configuration file path, using default configuration");
            return;
        }

        try {
            Path configPath = Paths.get(configFilePath);

            if (!Files.exists(configPath)) {
                logger.warn("Configuration file not found: {}, using default configuration", configFilePath);
                return;
            }

            try (InputStream input = Files.newInputStream(configPath)) {
                config.load(input);
                logger.info("Loaded configuration from: {}", configFilePath);

                // Apply configuration to RAGConverter
                applyConfiguration();
            }

        } catch (IOException e) {
            logger.error("Failed to load configuration from: {}", configFilePath, e);
            throw new Exception("Failed to load configuration", e);
        }
    }

    /**
     * Apply loaded configuration to RAGConverter
     */
    private void applyConfiguration() {
        // Configure RAGConverter based on properties
        String removeStrikethrough = config.getProperty("converter.removeStrikethrough", "true");
        RAGConverter.removerTachado = Boolean.parseBoolean(removeStrikethrough);

        String maxStringLength = config.getProperty("converter.maxStringLength");
        if (maxStringLength != null) {
            try {
                RAGConverter.MAX_STRING_LENGTH = Integer.parseInt(maxStringLength);
                logger.info("Set MAX_STRING_LENGTH to: {}", RAGConverter.MAX_STRING_LENGTH);
            } catch (NumberFormatException e) {
                logger.warn("Invalid maxStringLength value: {}, using default", maxStringLength);
            }
        }
    }

    @Override
    public String convertToMarkdown(String inputContent, String inputFormat) throws Exception {
	
	if(inputFormat != null && inputFormat.contains("markdown")) {
	    logger.debug("Input format is already markdown, returning original content");
	    return inputContent;
	}
	
        if (inputContent == null || inputContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Input content cannot be null or empty");
        }

        try {
            logger.debug("Converting string content to markdown (format hint: {})", inputFormat);

            // If format is specified and known to be HTML/XHTML, use direct conversion
            if (inputFormat != null) {
                String formatLower = inputFormat.toLowerCase();
                if (formatLower.contains("html") || formatLower.contains("xhtml")) {
                    return RAGConverter.convertHTMLtoMarkdown(inputContent);
                }
            }

            // Otherwise, let RAGConverter detect and convert
            return RAGConverter.convertToMarkdown(inputContent);

        } catch (Exception e) {
            logger.error("Failed to convert string content to markdown", e);
            throw new Exception("Conversion to markdown failed", e);
        }
    }

    @Override
    public String convertToMarkdown(URI contentSource, String inputFormat) throws Exception {
        if (contentSource == null) {
            throw new IllegalArgumentException("Content source URI cannot be null");
        }

        try {
            logger.debug("Converting URI content to markdown: {}", contentSource);

            // Download content from URI
            byte[] content = downloadContent(contentSource);

	    // Convert using byte array method
	    if (inputFormat != null && inputFormat.contains("markdown")) {
		logger.debug("Input format is already markdown, returning original content");
		return content != null ? new String(content) : null;
	    }
   
            return convertToMarkdown(content, inputFormat);

        } catch (Exception e) {
            logger.error("Failed to convert URI content to markdown: {}", contentSource, e);
            throw new Exception("Conversion from URI failed", e);
        }
    }

    @Override
    public String convertToMarkdown(byte[] content, String inputFormat) throws Exception {
	
	if(inputFormat != null && inputFormat.contains("markdown")) {
	    logger.debug("Input format is already markdown, returning original content");
	    return content != null ? new String(content) : null;
	}
	
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Content byte array cannot be null or empty");
        }

        try {
            logger.debug("Converting byte array to markdown (size: {} bytes, format hint: {})",
                        content.length, inputFormat);

            // Use RAGConverter to handle the conversion
            // It will auto-detect format if not specified
            String markdown = RAGConverter.convertToMarkdown(content);

            if (markdown == null || markdown.trim().isEmpty()) {
                logger.warn("Conversion resulted in empty content");
                return "";
            }

            logger.debug("Successfully converted {} bytes to {} characters of markdown",
                        content.length, markdown.length());

            return markdown;

        } catch (Exception e) {
            logger.error("Failed to convert byte array to markdown", e);
            throw new Exception("Conversion to markdown failed", e);
        }
    }

    /**
     * Detects the format of the document located at the given URI.
     * It downloads a sample of the content for detection.
     */
    @Override
    public String detectFormat(URI contentSource) throws Exception {
        if (contentSource == null) {
            throw new IllegalArgumentException("Content source URI cannot be null");
        }

        try {
            logger.debug("Detecting format for URI: {}", contentSource);

            // Download a sample of the content (first 256 bytes)
            byte[] sample = downloadContentSample(contentSource, 256);

            // Detect format from sample
            return detectFormat(sample);

        } catch (Exception e) {
            logger.error("Failed to detect format for URI: {}", contentSource, e);
            throw new Exception("Format detection failed", e);
        }
    }

    @Override
    public String detectFormat(byte[] contentSample) throws Exception {
        if (contentSample == null || contentSample.length == 0) {
            throw new IllegalArgumentException("Content sample cannot be null or empty");
        }

        try {
            logger.debug("Detecting format from byte array sample (size: {} bytes)", contentSample.length);

            // Use RAGConverter's Tika-based detection
            RAGConverter.MimeType mimeType = RAGConverter.detectMimeTypeTika(contentSample);

            String detectedFormat = mimeType.toString();
            logger.debug("Detected format: {}", detectedFormat);

            return detectedFormat;

        } catch (Exception e) {
            logger.error("Failed to detect format from byte array", e);
            throw new Exception("Format detection failed", e);
        }
    }

    /**
     * Downloads complete content from a URI
     *
     * @param uri Source URI
     * @return Downloaded content as byte array
     * @throws IOException If download fails
     */
    private byte[] downloadContent(URI uri) throws IOException {
        logger.debug("Downloading content from: {}", uri);

        try {
            URL url = uri.toURL();
            try (InputStream input = url.openStream()) {
                byte[] content = input.readAllBytes();
                logger.debug("Downloaded {} bytes from: {}", content.length, uri);
                return content;
            }
        } catch (IOException e) {
            logger.error("Failed to download content from: {}", uri, e);
            throw e;
        }
    }

    /**
     * Downloads a sample of content from a URI (for format detection)
     *
     * @param uri Source URI
     * @param sampleSize Number of bytes to download
     * @return Downloaded sample as byte array
     * @throws IOException If download fails
     */
    private byte[] downloadContentSample(URI uri, int sampleSize) throws IOException {
        logger.debug("Downloading {} byte sample from: {}", sampleSize, uri);

        try {
            URL url = uri.toURL();
            try (InputStream input = url.openStream()) {
                byte[] sample = new byte[sampleSize];
                int bytesRead = input.read(sample);

                if (bytesRead < sampleSize) {
                    // Return only the bytes actually read
                    byte[] actualSample = new byte[bytesRead];
                    System.arraycopy(sample, 0, actualSample, 0, bytesRead);
                    return actualSample;
                }

                logger.debug("Downloaded {} byte sample from: {}", bytesRead, uri);
                return sample;
            }
        } catch (IOException e) {
            logger.error("Failed to download content sample from: {}", uri, e);
            throw e;
        }
    }

    /**
     * Gets the current configuration
     *
     * @return Current configuration properties
     */
    public Properties getConfiguration() {
        return new Properties(config);
    }

    /**
     * Sets a configuration property
     *
     * @param key Property key
     * @param value Property value
     */
    public void setConfigProperty(String key, String value) {
        config.setProperty(key, value);
        applyConfiguration();
    }

    /**
     * Gets a configuration property
     *
     * @param key Property key
     * @return Property value or null if not found
     */
    public String getConfigProperty(String key) {
        return config.getProperty(key);
    }
}
