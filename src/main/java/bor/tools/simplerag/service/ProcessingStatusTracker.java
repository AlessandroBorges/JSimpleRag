package bor.tools.simplerag.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory tracker for document processing status.
 * Uses ConcurrentHashMap for thread-safe operations.
 */
@Service
@Slf4j
public class ProcessingStatusTracker {

    private final Map<Integer, ProcessingStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * Start tracking processing for a document
     */
    public void startProcessing(Integer documentId, String titulo) {
        ProcessingStatus status = ProcessingStatus.builder()
                .documentId(documentId)
                .titulo(titulo)
                .status(Status.PROCESSING)
                .startedAt(LocalDateTime.now())
                .progress(0)
                .message("Processing started")
                .build();

        statusMap.put(documentId, status);
        log.info("Started tracking processing for document {}: {}", documentId, titulo);
    }

    /**
     * Update processing progress
     */
    public void updateProgress(Integer documentId, int progress, String message) {
        ProcessingStatus status = statusMap.get(documentId);
        if (status != null) {
            status.setProgress(progress);
            status.setMessage(message);
            status.setUpdatedAt(LocalDateTime.now());
            log.debug("Updated progress for document {}: {}% - {}", documentId, progress, message);
        }
    }

    /**
     * Mark processing as completed
     */
    public void markCompleted(Integer documentId, String message) {
        ProcessingStatus status = statusMap.get(documentId);
        if (status != null) {
            status.setStatus(Status.COMPLETED);
            status.setProgress(100);
            status.setMessage(message);
            status.setCompletedAt(LocalDateTime.now());
            status.setUpdatedAt(LocalDateTime.now());
            log.info("Completed processing for document {}", documentId);
        }
    }

    /**
     * Mark processing as failed
     */
    public void markFailed(Integer documentId, String errorMessage) {
        ProcessingStatus status = statusMap.get(documentId);
        if (status != null) {
            status.setStatus(Status.FAILED);
            status.setMessage(errorMessage);
            status.setErrorMessage(errorMessage);
            status.setCompletedAt(LocalDateTime.now());
            status.setUpdatedAt(LocalDateTime.now());
            log.error("Failed processing for document {}: {}", documentId, errorMessage);
        }
    }

    /**
     * Get processing status for a document
     */
    public ProcessingStatus getStatus(Integer documentId) {
        ProcessingStatus status = statusMap.get(documentId);
        if (status == null) {
            // Return NOT_STARTED status if not found
            return ProcessingStatus.builder()
                    .documentId(documentId)
                    .status(Status.NOT_STARTED)
                    .message("Processing not started or already cleaned up")
                    .build();
        }
        return status;
    }

    /**
     * Remove status from tracker (cleanup after completion/failure)
     */
    public void removeStatus(Integer documentId) {
        statusMap.remove(documentId);
        log.debug("Removed status tracking for document {}", documentId);
    }

    /**
     * Clear all completed/failed statuses older than specified minutes
     */
    public void cleanupOldStatuses(int olderThanMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(olderThanMinutes);
        statusMap.entrySet().removeIf(entry -> {
            ProcessingStatus status = entry.getValue();
            return (status.getStatus() == Status.COMPLETED || status.getStatus() == Status.FAILED)
                    && status.getCompletedAt() != null
                    && status.getCompletedAt().isBefore(threshold);
        });
        log.info("Cleaned up statuses older than {} minutes", olderThanMinutes);
    }

    /**
     * Processing status enum
     */
    public enum Status {
        NOT_STARTED("Processing not started"),
        PROCESSING("Processing in progress"),
        COMPLETED("Processing completed successfully"),
        FAILED("Processing failed");

        private final String description;

        Status(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Processing status data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingStatus {
        private Integer documentId;
        private String titulo;
        private Status status;
        private Integer progress; // 0-100
        private String message;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime updatedAt;
        private LocalDateTime completedAt;
    }
}
