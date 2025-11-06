/**
 * 
 */
package bor.tools.simplerag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import bor.tools.simplerag.entity.MetaDoc;

/**
 * DTO for Chapter entity with associations.<br>
 * The associations included are:
 * - List of DocEmbeddings associated with the Chapter.
 * - The Chapter's library Object
 * 
 *  @see bor.tools.simplerag.dto.ChapterDTO
 *  @see bor.tools.simplerag.dto.LibraryDTO
 *  @see bor.tools.simplerag.dto.DocumentEmbeddingDTO
 *  
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChapterWithAssociationsDTO {

    private ChapterDTO chapter;
    
    private LibraryDTO library;
    
    private List<DocumentEmbeddingDTO> embeddings;
    
    /**
     * Static factory method to create ChapterWithAssociationsDTO from its components.
     * @param chapter
     * @param library
     * @param embeddings
     * @return
     */
    public static ChapterWithAssociationsDTO from(ChapterDTO chapter, LibraryDTO library, List<DocumentEmbeddingDTO> embeddings) {
	return ChapterWithAssociationsDTO.builder()
		.chapter(chapter)
		.library(library)
		.embeddings(embeddings)
		.build();
    }
    
    // utility methods can be added here as needed
    /**
     * Sets the chapter title in both the ChapterDTO and all associated DocumentEmbeddingDTOs.
     * @param title The title to set.
     */
    public void setChapterTitle(String title) {
	if (this.chapter != null) {
	    this.chapter.getMetadados().setCapitulo(title); ;
	}
	if(this.embeddings != null && !this.embeddings.isEmpty()) {
	    for (DocumentEmbeddingDTO embedding : this.embeddings) {
	       embedding.getMetadados().setCapitulo(title);	
	    }
	}
    }
    
    /**
     * Sets the chapter ID in both the ChapterDTO and all associated DocumentEmbeddingDTOs.
     * @param chapterId The chapter ID to set.
     */
    public void setChapterId(Integer chapterId) {
	if (this.chapter != null) {
	    this.chapter.setId(chapterId);	
	}
	if(this.embeddings != null && !this.embeddings.isEmpty()) {
	    for (DocumentEmbeddingDTO embedding : this.embeddings) {
	       embedding.setCapituloId(chapterId);	
	    }
	}
    }
    
    /**
     * Sets the library ID in both the LibraryDTO and all associated DocumentEmbeddingDTOs.
     * @param libraryId The library ID to set.
     */
    public void setLibraryId(Integer libraryId) {
	if (this.library != null) {
	    this.library.setId(libraryId);	
	}
	if(this.embeddings != null && !this.embeddings.isEmpty()) {
	    for (DocumentEmbeddingDTO embedding : this.embeddings) {
	       embedding.setBibliotecaId(libraryId);	
	    }
	}
    }
    
    /**
     * Sets the document ID in both the ChapterDTO and all associated DocumentEmbeddingDTOs.
     * @param documentId The document ID to set.
     */
    public void setDocumentId(Integer documentId) {
	if (this.chapter != null) {
	    this.chapter.setDocumentoId(documentId);
	}
	if (this.embeddings != null && !this.embeddings.isEmpty()) {
	    for (DocumentEmbeddingDTO embedding : this.embeddings) {
		embedding.setDocumentoId(documentId);
	    }
	}
    }
    
    /**
     * Synchronizes the IDs between the ChapterDTO, LibraryDTO, and DocumentEmbeddingDTOs.<br>
     * 
     * Ensures that the IDs in the ChapterDTO and LibraryDTO are reflected 
     * in all associated DocumentEmbeddingDTOs.
     */
    public void sincronizedIds() {	
	Integer libraryId = getLibrary() != null ? getLibrary().getId() : null;
	if(libraryId != null) {
	    setLibraryId(libraryId);
	}
	
	Integer chapterId = getChapter() != null ? getChapter().getId() : null;
	if(chapterId != null) {
	    setChapterId(chapterId);
	}
	
	Integer documentId = getChapter() != null ? getChapter().getDocumentoId() : null;
	if(documentId != null) {
	    setDocumentId(documentId);
	}
	
	String chapterTitle = getChapter() != null ? getChapter().getMetadados().getCapitulo() : null;
	if(chapterTitle != null) {
	    setChapterTitle(chapterTitle);
	}
    }
    
    /**
     * Create chapter-level embedding DTO for this chapter
     * 
     * @param content Text content for the embedding
     * @param ordemCap Order of the chapter embedding
     * 
     * @return DocumentEmbeddingDTO
     */	
    public DocumentEmbeddingDTO createChapterLevelEmbedding(String content, Integer ordemCap) {
	DocumentEmbeddingDTO embedding = this.chapter.createChapterLevelEmbedding(content, ordemCap);
	embedding.setOrdemCap(embeddings.size() + 1); // set ordemCap based on current size
	this.embeddings.add(embedding);
	return embedding;
    }
    
    
	
} //class