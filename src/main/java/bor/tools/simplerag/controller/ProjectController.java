package bor.tools.simplerag.controller;

import bor.tools.simplerag.dto.ChatDTO;
import bor.tools.simplerag.dto.ProjectDTO;
import bor.tools.simplerag.dto.ProjectWithChatsDTO;
import bor.tools.simplerag.entity.Project;
import bor.tools.simplerag.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Project management.
 * Provides CRUD operations and chat association management via MetaProject.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Projects", description = "Project management")
public class ProjectController {

    private final ProjectService projectService;

    /**
     * Create or update project
     */
    @PostMapping
    @Operation(summary = "Create or update project",
               description = "Saves project with auto-increment ordem if not provided")
    public ResponseEntity<ProjectDTO> save(@Valid @RequestBody ProjectDTO dto) {
        log.info("Saving project: {}", dto.getTitulo());

        try {
            // Convert DTO to Entity
            Project project = toEntity(dto);

            // Save (ordem will be auto-generated if null)
            Project saved = projectService.save(project);

            // Convert back to DTO
            ProjectDTO response = ProjectDTO.from(saved);

            log.info("Project saved: id={}, ordem={}", saved.getId(), saved.getOrdem());

            return ResponseEntity.status(dto.getId() == null ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error saving project: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error saving project: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao salvar projeto: " + e.getMessage(), e);
        }
    }

    /**
     * Delete project (soft or hard delete)
     */
    @DeleteMapping("/{uuid}")
    @Operation(summary = "Delete project (soft or hard)",
               description = "Soft delete sets deletedAt, hard delete removes project")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
                                       @RequestParam(defaultValue = "false") boolean hard) {
        log.info("Deleting project: uuid={}, hard={}", uuid, hard);

        try {
            Project project = projectService.findById(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Projeto não encontrado: " + uuid));

            projectService.delete(project, hard);

            log.info("Project deleted: uuid={}, hard={}", uuid, hard);

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            log.error("Project not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting project: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao deletar projeto: " + e.getMessage(), e);
        }
    }

    /**
     * Get project by UUID
     */
    @GetMapping("/{uuid}")
    @Operation(summary = "Get project by UUID")
    public ResponseEntity<ProjectDTO> findById(@PathVariable UUID uuid) {
        log.debug("Finding project by UUID: {}", uuid);

        Project project = projectService.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Projeto não encontrado: " + uuid));

        return ResponseEntity.ok(ProjectDTO.from(project));
    }

    /**
     * Get project with associated chats (from MetaProject)
     */
    @GetMapping("/{uuid}/with-chats")
    @Operation(summary = "Get project with associated chats",
               description = "Returns project with chat IDs extracted from MetaProject metadata")
    public ResponseEntity<ProjectWithChatsDTO> getWithChats(@PathVariable UUID uuid) {
        log.debug("Finding project with chats: uuid={}", uuid);

        try {
            ProjectService.ProjectWithChats projectWithChats = projectService.loadProjectWithChats(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Projeto não encontrado: " + uuid));

            // Convert to DTO
            ProjectDTO projectDTO = ProjectDTO.from(projectWithChats.getProject());

            List<ChatDTO> chatDTOs = projectWithChats.getChats().stream()
                    .map(ChatDTO::from)
                    .collect(Collectors.toList());

            ProjectWithChatsDTO response = ProjectWithChatsDTO.builder()
                    .project(projectDTO)
                    .chats(chatDTOs)
                    .build();

            log.debug("Project with chats found: {} chats", response.getChatCount());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Project not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error loading project with chats: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao carregar projeto com chats: " + e.getMessage(), e);
        }
    }

    /**
     * Add chat to project (via MetaProject)
     */
    @PutMapping("/{projectId}/chats/{chatId}")
    @Operation(summary = "Add chat to project",
               description = "Adds chat ID to project's MetaProject metadata")
    public ResponseEntity<ProjectDTO> addChat(@PathVariable UUID projectId,
                                              @PathVariable UUID chatId) {
        log.info("Adding chat {} to project {}", chatId, projectId);

        try {
            Project updated = projectService.addChatToProject(projectId, chatId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Projeto %s ou chat %s não encontrado", projectId, chatId)));

            log.info("Chat {} added to project {}", chatId, projectId);

            return ResponseEntity.ok(ProjectDTO.from(updated));

        } catch (IllegalArgumentException e) {
            log.error("Error adding chat to project: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error adding chat to project: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao adicionar chat ao projeto: " + e.getMessage(), e);
        }
    }

    /**
     * Remove chat from project (via MetaProject)
     */
    @DeleteMapping("/{projectId}/chats/{chatId}")
    @Operation(summary = "Remove chat from project",
               description = "Removes chat ID from project's MetaProject metadata")
    public ResponseEntity<ProjectDTO> removeChat(@PathVariable UUID projectId,
                                                  @PathVariable UUID chatId) {
        log.info("Removing chat {} from project {}", chatId, projectId);

        try {
            Project updated = projectService.removeChatFromProject(projectId, chatId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Projeto %s ou chat %s não encontrado", projectId, chatId)));

            log.info("Chat {} removed from project {}", chatId, projectId);

            return ResponseEntity.ok(ProjectDTO.from(updated));

        } catch (IllegalArgumentException e) {
            log.error("Error removing chat from project: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error removing chat from project: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao remover chat do projeto: " + e.getMessage(), e);
        }
    }

    /**
     * Get user projects ordered by ordem
     */
    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Get user projects ordered by ordem")
    public ResponseEntity<List<ProjectDTO>> getUserProjects(@PathVariable UUID userId) {
        log.debug("Finding projects for user: {}", userId);

        List<Project> projects = projectService.loadUserProjects(userId);

        List<ProjectDTO> response = projects.stream()
                .map(ProjectDTO::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get recent projects for user
     */
    @GetMapping("/by-user/{userId}/recent")
    @Operation(summary = "Get recent projects for user",
               description = "Returns most recent projects (default limit: 10)")
    public ResponseEntity<List<ProjectDTO>> getRecentProjects(@PathVariable UUID userId,
                                                               @RequestParam(defaultValue = "10") int limit) {
        log.debug("Finding recent projects for user: {} (limit={})", userId, limit);

        List<Project> projects = projectService.findRecentProjects(userId, limit);

        List<ProjectDTO> response = projects.stream()
                .map(ProjectDTO::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Convert DTO to Entity
     */
    private Project toEntity(ProjectDTO dto) {
        Project project = new Project();
        project.setId(dto.getId());
        project.setUser_id(dto.getUserId());
        project.setTitulo(dto.getTitulo());
        project.setDescricao(dto.getDescricao());
        project.setOrdem(dto.getOrdem());
        project.setBiblioteca_privativa(dto.getBibliotecaPrivativa());
        project.setMetadata(dto.getMetadata());
        return project;
    }
}
