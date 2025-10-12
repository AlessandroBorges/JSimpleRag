package bor.tools.simplerag.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * MappedSuperClass to add createdAt and updatedAt fields to entities.
 * Not mapped to a specific table.
 */
@MappedSuperclass
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder

public class Updatable {

  /**
   * Timestamp when the record was created.
   */
   @Column(name = "created_at", updatable = false)
   private LocalDateTime createdAt;
   
  /**
   * Timestamp when the record was last updated.
   */
   @Column(name = "updated_at", updatable = true)
   private LocalDateTime updatedAt;
   
   /**
    * Timestamp when the record was deleted (for soft deletes).
    */
   @Column(name = "deleted_at", updatable = true)
   private LocalDateTime deletedAt; // For soft deletes, if needed
   
   
   @PrePersist
   protected void onCreate() {
       createdAt = LocalDateTime.now();
       updatedAt = createdAt;
   }
   
   @PreUpdate
   protected void onUpdate() {
       updatedAt = LocalDateTime.now();
   }	
   

   protected void onDelete() {
       deletedAt = LocalDateTime.now();
   }
   
}