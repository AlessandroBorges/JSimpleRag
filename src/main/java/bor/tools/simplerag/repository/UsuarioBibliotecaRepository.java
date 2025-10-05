package bor.tools.simplerag.repository;

import bor.tools.simplerag.entity.UserLibrary;
import bor.tools.simplerag.entity.enums.TipoAssociacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para a entidade UserLibrary.
 *
 * Provides operations for managing the N:N relationship between User and Library.
 */
@Repository
public interface UsuarioBibliotecaRepository extends JpaRepository<UserLibrary, Integer> {

    /**
     * Busca todas as associações de um usuário
     */
    List<UserLibrary> findByUsuarioId(Integer usuarioId);

    /**
     * Busca todas as associações de uma biblioteca
     */
    List<UserLibrary> findByBibliotecaId(Integer bibliotecaId);

    /**
     * Busca associação específica entre usuário e biblioteca
     */
    Optional<UserLibrary> findByUsuarioIdAndBibliotecaId(Integer usuarioId, Integer bibliotecaId);

    /**
     * Busca associações de um usuário por tipo
     */
    List<UserLibrary> findByUsuarioIdAndTipoAssociacao(Integer usuarioId, TipoAssociacao tipoAssociacao);

    /**
     * Busca associações de uma biblioteca por tipo
     */
    List<UserLibrary> findByBibliotecaIdAndTipoAssociacao(Integer bibliotecaId, TipoAssociacao tipoAssociacao);

    /**
     * Verifica se existe associação entre usuário e biblioteca
     */
    boolean existsByUsuarioIdAndBibliotecaId(Integer usuarioId, Integer bibliotecaId);

    /**
     * Busca todos os proprietários de uma biblioteca
     */
    @Query("SELECT ub FROM UserLibrary ub WHERE ub.bibliotecaId = :bibliotecaId AND ub.tipoAssociacao = 'PROPRIETARIO'")
    List<UserLibrary> findProprietariosByBibliotecaId(@Param("bibliotecaId") Integer bibliotecaId);

    /**
     * Busca todas as bibliotecas que um usuário é proprietário
     */
    @Query("SELECT ub FROM UserLibrary ub WHERE ub.usuarioId = :usuarioId AND ub.tipoAssociacao = 'PROPRIETARIO'")
    List<UserLibrary> findBibliotecasPropriedadeByUsuarioId(@Param("usuarioId") Integer usuarioId);

    /**
     * Conta quantas bibliotecas um usuário possui
     */
    @Query("SELECT COUNT(ub) FROM UserLibrary ub WHERE ub.usuarioId = :usuarioId")
    long countByUsuarioId(@Param("usuarioId") Integer usuarioId);

    /**
     * Conta quantos usuários uma biblioteca possui
     */
    @Query("SELECT COUNT(ub) FROM UserLibrary ub WHERE ub.bibliotecaId = :bibliotecaId")
    long countByBibliotecaId(@Param("bibliotecaId") Integer bibliotecaId);

    /**
     * Remove todas as associações de um usuário
     */
    void deleteByUsuarioId(Integer usuarioId);

    /**
     * Remove todas as associações de uma biblioteca
     */
    void deleteByBibliotecaId(Integer bibliotecaId);
}
