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
    * Null means the record is active (not deleted).
    */
   @Column(name = "deleted_at", nullable = true)
   private LocalDateTime deletedAt;
   
   
   @PrePersist
   protected void onCreate() {
       createdAt = LocalDateTime.now();
       updatedAt = createdAt;
   }
   
   @PreUpdate
   protected void onUpdate() {
       updatedAt = LocalDateTime.now();
   }

   /**
    * Marks this entity as deleted (soft delete).
    * Sets deletedAt to current timestamp.
    */
   public void markAsDeleted() {
       deletedAt = LocalDateTime.now();
   }

   /**
    * Restores a soft-deleted entity.
    * Sets deletedAt back to null.
    */
   public void restore() {
       deletedAt = null;
   }

   /**
    * Checks if this entity is soft-deleted.
    * @return true if deletedAt is not null
    */
   public boolean isDeleted() {
       return deletedAt != null;
   }
   
}