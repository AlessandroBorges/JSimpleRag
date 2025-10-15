package bor.tools.simplerag.service;

import bor.tools.simplerag.entity.Chat;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.MetaProject;
import bor.tools.simplerag.entity.ChatProject;
import bor.tools.simplerag.repository.ChatRepository;
import bor.tools.simplerag.repository.LibraryRepository;
import bor.tools.simplerag.repository.ChatProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for ChatProject entity operations.
 * Handles project organization, chat grouping via metadata, and library associations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatProjectService {

    private final ChatProjectRepository projectRepository;
    private final ChatRepository chatRepository;
    private final LibraryRepository libraryRepository;

    /**
     * Save (create or update) project entity
     * @param project - project to save
     * @return saved project
     */
    @Transactional
    public ChatProject save(ChatProject project) {
        log.debug("Saving project: {}", project.getTitulo());

        // Validate user ID
        if (project.getUser_uuid() == null) {
            throw new IllegalArgumentException("User ID é obrigatório");
        }

        // Generate UUID if not present (handled by @PrePersist but ensuring)
        if (project.getId() == null) {
            project.setId(UUID.randomUUID());
        }

        // Set ordem if not present
        if (project.getOrdem() == null) {
            Integer nextOrdem = projectRepository.findNextOrdem(project.getUser_uuid());
            project.setOrdem(nextOrdem);
        }

        return projectRepository.save(project);
    }

    /**
     * Delete project (soft or hard delete)
     * @param project - project to delete
     * @param isHardDelete - true for physical delete, false for soft delete
     */
    @Transactional
    public void delete(ChatProject project, boolean isHardDelete) {
        log.debug("Deleting project: {} (hard={})", project.getTitulo(), isHardDelete);

        if (isHardDelete) {
            projectRepository.delete(project);
            log.info("ChatProject hard deleted: {}", project.getTitulo());
        } else {
            project.setDeletedAt(LocalDateTime.now());
            projectRepository.save(project);
            log.info("ChatProject soft deleted: {}", project.getTitulo());
        }
    }

    /**
     * Find project by ID
     * @param id - project UUID
     * @return Optional project
     */
    public Optional<ChatProject> findById(UUID id) {
        return projectRepository.findById(id);
    }

    /**
     * Load user projects ordered by ordem
     * @param userId - user UUID
     * @return List of projects
     */
    public List<ChatProject> loadUserProjects(UUID userId) {
        return projectRepository.findByUserIdOrderByOrdemAsc(userId);
    }

    /**
     * Get next available ordem for user's projects
     * @param userId - user UUID
     * @return next ordem number
     */
    public Integer getNextOrdem(UUID userId) {
        return projectRepository.findNextOrdem(userId);
    }

    /**
     * Load project with associated chats
     * @param projectId - project UUID
     * @return ProjectWithChats wrapper
     */
    @Transactional(readOnly = true)
    public Optional<ProjectWithChats> loadProjectWithChats(UUID projectId) {
        Optional<ChatProject> projectOpt = findById(projectId);

        if (projectOpt.isEmpty()) {
            return Optional.empty();
        }

        ChatProject project = projectOpt.get();
        List<Chat> chats = Collections.emptyList();

        // Extract chat IDs from metadata if MetaProject
        if (project.getMetadata() instanceof MetaProject) {
            MetaProject metaProject = (MetaProject) project.getMetadata();
            Object chatsObj = metaProject.get(MetaProject.CHAT_KEY);

            if (chatsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<UUID> chatIds = (List<UUID>) chatsObj;

                if (!chatIds.isEmpty()) {
                    chats = chatRepository.findAllById(chatIds);
                }
            }
        }

        return Optional.of(new ProjectWithChats(project, chats));
    }

    /**
     * Load project's library if bibliotecaPrivativa is set
     * @param projectId - project UUID
     * @return Optional library
     */
    @Transactional(readOnly = true)
    public Optional<Library> loadProjectLibrary(UUID projectId) {
        Optional<ChatProject> projectOpt = findById(projectId);

        if (projectOpt.isEmpty() || projectOpt.get().getBiblioteca_privativa() == null) {
            return Optional.empty();
        }

        UUID libraryUuid = projectOpt.get().getBiblioteca_privativa();

        return libraryRepository.findAll().stream()
                .filter(lib -> libraryUuid.equals(lib.getUuid()))
                .findFirst();
    }

    /**
     * Add chat to project metadata
     * @param projectId - project UUID
     * @param chatId - chat UUID
     * @return updated project
     */
    @Transactional
    public Optional<ChatProject> addChatToProject(UUID projectId, UUID chatId) {
        Optional<ChatProject> projectOpt = findById(projectId);
        Optional<Chat> chatOpt = chatRepository.findById(chatId);

        if (projectOpt.isEmpty() || chatOpt.isEmpty()) {
            return Optional.empty();
        }

        ChatProject project = projectOpt.get();
        Chat chat = chatOpt.get();

        // Ensure metadata is MetaProject
        if (!(project.getMetadata() instanceof MetaProject)) {
            MetaProject metaProject = new MetaProject();
            project.setMetadata(metaProject);
        }

        MetaProject metaProject = (MetaProject) project.getMetadata();
        metaProject.addChat(chat);

        return Optional.of(save(project));
    }

    /**
     * Remove chat from project metadata
     * @param projectId - project UUID
     * @param chatId - chat UUID
     * @return updated project
     */
    @Transactional
    public Optional<ChatProject> removeChatFromProject(UUID projectId, UUID chatId) {
        Optional<ChatProject> projectOpt = findById(projectId);
        Optional<Chat> chatOpt = chatRepository.findById(chatId);

        if (projectOpt.isEmpty() || chatOpt.isEmpty()) {
            return Optional.empty();
        }

        ChatProject project = projectOpt.get();
        Chat chat = chatOpt.get();

        if (!(project.getMetadata() instanceof MetaProject)) {
            return Optional.of(project); // No metadata, nothing to remove
        }

        MetaProject metaProject = (MetaProject) project.getMetadata();
        metaProject.removeChat(chat);

        return Optional.of(save(project));
    }

    /**
     * Reorder projects for a user
     * @param userId - user UUID
     * @param projectOrders - map of project UUID to new ordem
     * @return updated projects
     */
    @Transactional
    public List<ChatProject> reorderProjects(UUID userId, Map<UUID, Integer> projectOrders) {
        List<ChatProject> projects = projectRepository.findByUserId(userId);

        projects.forEach(project -> {
            if (projectOrders.containsKey(project.getId())) {
                project.setOrdem(projectOrders.get(project.getId()));
            }
        });

        return projectRepository.saveAll(projects);
    }

    /**
     * Find project by user and title
     * @param userId - user UUID
     * @param titulo - project title
     * @return Optional project
     */
    public List<ChatProject> findByUserIdAndTitulo(UUID userId, String titulo) {
        return projectRepository.findByUserIdAndTitulo(userId, titulo);
    }

    /**
     * Count user's projects
     * @param userId - user UUID
     * @return project count
     */
    public long countUserProjects(UUID userUuid) {
        return projectRepository.countByUserUuid(userUuid);
    }
    
    public long countUserProjectsByUserId(Integer userId) {
        return projectRepository.countByUserId(userId);
    }
    
    public long countUserProjectsByUserId(UUID userUuid) {
        return projectRepository.countByUserUuid(userUuid);
    }

    /**
     * Find recent projects for user
     * @param userId - user UUID
     * @param limit - number of projects to return
     * @return List of recent projects
     */
    public List<ChatProject> findRecentProjects(UUID userId, int limit) {
        return projectRepository.findTopNRecentProjects(userId, limit);
    }

    /**
     * DTO class to return project with chats
     */
    public static class ProjectWithChats {
        private final ChatProject project;
        private final List<Chat> chats;

        public ProjectWithChats(ChatProject project, List<Chat> chats) {
            this.project = project;
            this.chats = chats;
        }

        public ChatProject getProject() {
            return project;
        }

        public List<Chat> getChats() {
            return chats;
        }

        public int getChatCount() {
            return chats.size();
        }

        public List<UUID> getChatIds() {
            return chats.stream()
                    .map(Chat::getId)
                    .collect(Collectors.toList());
        }
    }
}
