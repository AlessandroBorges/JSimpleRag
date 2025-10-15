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
public interface UserLibraryRepository extends JpaRepository<UserLibrary, Integer> {

    /**
     * Busca todas as associações de um usuário
     */
    @Query("SELECT ul FROM UserLibrary ul WHERE ul.userId = :userId")
    List<UserLibrary> findByUserId(Integer userId);

    /**
     * Busca todas as associações de uma biblioteca
     */
    @Query("SELECT ul FROM UserLibrary ul WHERE ul.libraryId = :libraryId")
    List<UserLibrary> findByLibraryId(Integer libraryId);

    /**
     * Busca associação específica entre usuário e biblioteca
     */
    @Query("SELECT ul FROM UserLibrary ul WHERE ul.userId = :userId AND ul.libraryId = :libraryId")
    Optional<UserLibrary> findByUserIdAndLibraryId(Integer userId, Integer libraryId);

    /**
     * Busca associações de um usuário por tipo
     */
    @Query("SELECT ul FROM UserLibrary ul WHERE ul.userId = :userId AND ul.tipoAssociacao = :tipoAssociacao")
    List<UserLibrary> findByUserIdAndTipoAssociacao(Integer userId, TipoAssociacao tipoAssociacao);

    /**
     * Busca associações de uma biblioteca por tipo
     */
    @Query("SELECT ul FROM UserLibrary ul WHERE ul.libraryId = :libraryId AND ul.tipoAssociacao = :tipoAssociacao")
    List<UserLibrary> findByLibraryIdAndTipoAssociacao(Integer libraryId, TipoAssociacao tipoAssociacao);

    /**
     * Verifica se existe associação entre usuário e biblioteca
     */
    @Query("SELECT CASE WHEN COUNT(ub) > 0 THEN true ELSE false END FROM UserLibrary ub WHERE ub.userId = :userId AND ub.libraryId = :libraryId")
    boolean existsByUserIdAndBibliotecaId(Integer userId, Integer libraryId);

    /**
     * Busca todos os proprietários de uma biblioteca
     */
    @Query("SELECT ub FROM UserLibrary ub WHERE ub.libraryId = :libraryId AND ub.tipoAssociacao = 'PROPRIETARIO'")
    List<UserLibrary> findProprietariosByBibliotecaId(@Param("libraryId") Integer libraryId);

    /**
     * Busca todas as bibliotecas que um usuário é proprietário
     */
    @Query("SELECT ub FROM UserLibrary ub WHERE ub.userId = :userId AND ub.tipoAssociacao = 'PROPRIETARIO'")
    List<UserLibrary> findBibliotecasPropriedadeByUserId(@Param("userId") Integer userId);

    /**
     * Conta quantas bibliotecas um usuário possui
     */
    @Query("SELECT COUNT(ub) FROM UserLibrary ub WHERE ub.userId = :userId")
    long countByUserId(@Param("userId") Integer userId);

    /**
     * Conta quantos usuários uma biblioteca possui
     */    
    @Query("SELECT COUNT(ub) FROM UserLibrary ub WHERE ub.libraryId = :libraryId")
    long countByBibliotecaId(@Param("libraryId") Integer libraryId);

    /**
     * Remove todas as associações de um usuário
     */
    @Query("DELETE FROM UserLibrary ul WHERE ul.userId = :userId")    
    void deleteByUserId(Integer userId);

    /**
     * Remove todas as associações de uma biblioteca
     */
    @Query("DELETE FROM UserLibrary ul WHERE ul.libraryId = :libraryId")
    void deleteByBibliotecaId(Integer libraryId);

    /**
     * Remove associação específica entre usuário e biblioteca
     */
    @Query("DELETE FROM UserLibrary ul WHERE ul.libraryId = :libraryId AND ul.userId = :userId")
    void deletebyLibraryIdAndUserId(Integer libraryId, Integer userId); 
}
