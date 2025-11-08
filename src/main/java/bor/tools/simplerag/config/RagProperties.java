package bor.tools.simplerag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RAG (Retrieval-Augmented Generation) system.
 * This class holds all RAG-related configuration including document processing,
 * embedding generation, and search settings.
 */
@ConfigurationProperties(prefix = "rag")
@lombok.Data
public class RagProperties {
    
    private ProcessamentoConfig processamento;
    private EmbeddingConfig embedding;
    private PesquisaConfig pesquisa;
    
    /**
     * Document processing configuration.
     * Controls chunk sizes, chapter settings, and asynchronous processing.
     */
    @lombok.Data
    public static class ProcessamentoConfig {
        private Integer chunkSizeMaximo;
        private Integer capituloSizePadrao;
        private AsyncConfig async;
        
        /**
         * Asynchronous processing configuration.
         * Defines thread pool settings and scheduling parameters.
         */
        @lombok.Data
        public static class AsyncConfig {
            private Integer corePoolSize;
            private Integer maxPoolSize;
            private Integer queueCapacity;
            private ScheduleConfig schedule;
            
            /**
             * Scheduling configuration.
             * Controls timing for scheduled tasks.
             */
            @lombok.Data
            public static class ScheduleConfig {
                private Long fixedDelay;
            }
        }
    }
    
    /**
     * Embedding generation configuration.
     * Controls batch processing and timeout settings for embeddings.
     */
    @lombok.Data
    public static class EmbeddingConfig {
        private Integer batchSize;
        private Integer timeoutSeconds;
    }
    
    /**
     * Search configuration.
     * Defines default weights and limits for hybrid search.
     */
    @lombok.Data
    public static class PesquisaConfig {
        private DefaultConfig defaultConfig;
        
        /**
         * Default search configuration.
         * Sets semantic/textual weights and result limits.
         */
        @lombok.Data
        public static class DefaultConfig {
            private Double pesoSemantico;
            private Double pesoTextual;
            private Integer limite;
        }
    }
}