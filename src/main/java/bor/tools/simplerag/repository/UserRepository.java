package bor.tools.simplerag.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import bor.tools.simplerag.entity.User;

/**
 * Repositório JPA para a entidade User.
 *
 * Provides standard CRUD operations and custom queries for User entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Busca usuário por UUID
     */
    @Query("SELECT u FROM User u WHERE u.uuid = :uuid")
    Optional<User> findByUuid(String uuid);

    /**
     * Busca usuário por email
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(String email);

    /**
     * Verifica se existe usuário com o email especificado
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(String email);

    /**
     * Busca usuários ativos
     */
    @Query("SELECT u FROM User u WHERE u.ativo = :ativo")
    List<User> findByAtivo(Boolean ativo);

    /**
     * Busca usuários por nome (contendo texto, case-insensitive)
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.nome) LIKE LOWER(CONCAT('%', :nome, '%'))")
    List<User> findByNomeContainingIgnoreCase(String nome);

    /**
     * Busca todos os usuários ativos
     */
    @Query("SELECT u FROM User u WHERE u.ativo = true ORDER BY u.nome")
    List<User> findAllAtivos();

    /**
     * Busca todos os usuários associados a uma biblioteca específica
     */
    @Query("SELECT u FROM User u JOIN UserLibrary ul ON u.id = ul.userId WHERE ul.libraryId = :libraryId")
    List<User> findByLibraryId(Integer libraryId);
    
    /**
     * Busca usuários por uma lista de IDs
     */
    @Query("SELECT u FROM User u WHERE u.id IN :ids")
    List<User> findByIdIn(List<Integer> ids);

    /**
     * Busca usuário por email (case-insensitive)
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(String email);
}
