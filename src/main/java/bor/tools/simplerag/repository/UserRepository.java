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
    Optional<User> findByUuid(String uuid);

    /**
     * Busca usuário por email
     */
    Optional<User> findByEmail(String email);

    /**
     * Verifica se existe usuário com o email especificado
     */
    boolean existsByEmail(String email);

    /**
     * Busca usuários ativos
     */
    List<User> findByAtivo(Boolean ativo);

    /**
     * Busca usuários por nome (contendo texto, case-insensitive)
     */
    List<User> findByNomeContainingIgnoreCase(String nome);

    /**
     * Busca todos os usuários ativos
     */
    @Query("SELECT u FROM User u WHERE u.ativo = true ORDER BY u.nome")
    List<User> findAllAtivos();

    /**
     * Busca usuário por email (case-insensitive)
     */
    Optional<User> findByEmailIgnoreCase(String email);
}
