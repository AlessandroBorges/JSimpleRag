package bor.tools.splitter;

import java.util.List;

import bor.tools.simplerag.entity.*;
import bor.tools.simplellm.ModelEmbedding.Embeddings_Op;
import bor.tools.simplerag.dto.*;


/**
 * Interface for embedding operations.
 *
 * @deprecated Use {@link bor.tools.simplerag.service.embedding.EmbeddingService} instead.
 *             This interface is being phased out in favor of the new embedding service
 *             architecture which provides better separation of concerns, retry logic,
 *             and integration with LLMServiceManager pool.
 *
 * Migration guide:
 * - Replace EmbeddingProcessorInterface with EmbeddingService
 * - Use EmbeddingContext instead of passing LibraryDTO directly
 * - Use EmbeddingOrchestrator for full document processing
 *
 * @see bor.tools.simplerag.service.embedding.EmbeddingService
 * @see bor.tools.simplerag.service.embedding.EmbeddingOrchestrator
 * @see bor.tools.simplerag.service.embedding.model.EmbeddingContext
 */
@Deprecated(since = "0.0.2", forRemoval = true)
public interface EmbeddingProcessorInterface {

	/**
	 * Flags for embedding generation.
     *
	 */
	public static final int FLAG_FULL_TEXT_METADATA = 1,
			        FLAG_ONLY_METADATA = 2,
			        FLAG_ONLY_TEXT = 3,
			        FLAG_SPLIT_TEXT_METADATA = 4,
	                        FLAG_AUTO = 5;  // default is full text metadata


	/**
     * Creates embeddings for a document.
     * @param document - Document to process
     * @param embedding_config_id - the ID of EmbeddingsConfig used on this
     * @return either -  error response or the list  of DocEmbeddings
     *
     * @see EmbeddingsConfig
     * @see DocEmbeddings
     * @see Documento
     */
    List<DocumentEmbeddingDTO> createChapterEmbeddings(ChapterDTO document,
    		                                 LibraryDTO library,
    		                                 int flagGeneration);
    
    


	/**
     * Creates embeddings for a document.
     * @param document - Document to process
     * @param embedding_config_id - the ID of EmbeddingsConfig used on this
     * @return either -  error response or the list  of DocEmbeddings
     *
     * @see EmbeddingsConfig
     * @see DocEmbeddings
     * @see Documento
     */
    List<DocumentEmbeddingDTO> createChunkEmbeddings(DocumentEmbeddingDTO document,
    		                                 LibraryDTO library,
    		                                 int flagGeneration);

    /**
     * Creates embeddings for a question-answer pair.
     * @param document - the chapter with text data and metadata
     * @param library - the basic configuration of embeddings, as model name, vector length 
     * @param k - number of questions to generate. Default is 3.
     * @return
     */
    List<DocumentEmbeddingDTO>createQAEmbeddings(ChapterDTO document,
	                                    LibraryDTO library,
	                                    Integer k);


    /**
     * Creates embeddings for a search.
     * @param pesquisa - search string
     * @param config Embedding configuration
     *
     * @return either error response or the list  of DocEmbeddings
     *
     * @see EmbeddingsConfig
     **/
     float[] createSearchEmbeddings(String pesquisa,
	     	    		    LibraryDTO library	
                                    );

     /**
      * Create customized embeddings
      * @param op
      * @param pesquisa
      * @param library
      * @return
      */
     float[] createEmbeddings(Embeddings_Op op,
	     String pesquisa,
  	     LibraryDTO library	
             );
    
     /**
      * Creates embeddings for chapter summaries.
      * This method generates a summary of the chapter content and then creates embeddings for it.
      * @param chapter - ChapterDTO to summarize and create embeddings for
      * @param library - LibraryDTO containing embedding configuration (model name, vector length, etc.)
      * @param maxSummaryLength - Maximum length for the generated summary (optional, can be null for default)
      * @param summaryInstructions - Custom instructions for summarization (optional, can be null for default)
      * @return List of DocumentEmbeddingDTO containing the summary embeddings
      */
     List<DocumentEmbeddingDTO> createSummaryEmbeddings(ChapterDTO chapter,
                                                   LibraryDTO library,
                                                   Integer maxSummaryLength,
                                                   String summaryInstructions);

}