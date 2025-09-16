package bor.tools.splitter;

import java.util.List;

import bor.tools.simplerag.entity.*;
import bor.tools.simplellm.ModelEmbedding.Embeddings_Op;
import bor.tools.simplerag.dto.*;


/**
 * Interface for embedding operations.
 */
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
    List<DocEmbeddingDTO> createSimpleEmbeddings(CapituloDTO document,
    		                                 BibliotecaDTO library,
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
    List<DocEmbeddingDTO> createSimpleEmbeddings(DocEmbeddingDTO document,
    		                                 BibliotecaDTO library,
    		                                 int flagGeneration);

    /**
     * Creates embeddings for a question-answer pair.
     * @param partes
     * @param config
     * @return
     */
    List<DocEmbeddingDTO>createQAEmbeddings(CapituloDTO metadataAware,
	                                    BibliotecaDTO library);


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
	     	    		    BibliotecaDTO library	
                                    );

     float[] createEmbeddings(Embeddings_Op op,
	     String pesquisa,
  		    BibliotecaDTO library	
             );
    

}

