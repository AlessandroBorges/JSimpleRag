/**
 * Context classes for document processing.
 *
 * <p>This package contains context classes that encapsulate validated LLM services
 * and models for document processing operations. Contexts are created once per document
 * and reused throughout the processing pipeline to avoid redundant model lookups and
 * validations.</p>
 *
 * <h2>Main Components:</h2>
 * <ul>
 *   <li>{@link bor.tools.simplerag.service.processing.context.LLMContext} -
 *       Context for LLM completion operations (summaries, Q&A, token counting)</li>
 *   <li>{@link bor.tools.simplerag.service.processing.context.EmbeddingContext} -
 *       Context for embedding generation operations (batch and individual)</li>
 * </ul>
 *
 * <h2>Usage Pattern:</h2>
 * <pre>{@code
 * // Create contexts once
 * LLMContext llmContext = LLMContext.create(library, llmServiceManager);
 * EmbeddingContext embContext = EmbeddingContext.create(library, llmServiceManager);
 *
 * // Reuse for all operations
 * String summary = llmContext.generateCompletion(systemPrompt, userPrompt);
 * int tokens = llmContext.tokenCount(text, "fast");
 * float[][] vectors = embContext.generateEmbeddingsBatch(texts, Embeddings_Op.DOCUMENT);
 * }</pre>
 *
 * @since 0.0.3
 * @version 1.1
 */
package bor.tools.simplerag.service.processing.context;
