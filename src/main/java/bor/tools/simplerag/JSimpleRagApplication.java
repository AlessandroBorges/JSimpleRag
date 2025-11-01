package bor.tools.simplerag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for JSimpleRag.
 *
 * JSimpleRag is a hierarchical RAG (Retrieval-Augmented Generation) system
 * that provides intelligent document search and retrieval capabilities
 * through a combination of semantic (embedding-based) and textual (full-text) search.
 *
 * Features:
 * - Hierarchical document structure: Library → Documento → Capítulo → DocumentEmbedding
 * - Hybrid search combining semantic similarity and full-text search
 * - Asynchronous document processing and embedding generation
 * - PostgreSQL with PGVector for efficient vector operations
 * - RESTful API with OpenAPI/Swagger documentation
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
    "bor.tools.simplerag",  // Main application package
    "bor.tools.utils",       // Utility classes (DocumentConverter, RAGConverter, etc.)
    "bor.tools.splitter"     // Document splitter services
})
public class JSimpleRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(JSimpleRagApplication.class, args);
    }
}
