package bor.tools.simplerag.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the type of document section.
 * Maps to PostgreSQL enum tipo_secao ('introducao', 'metodologia', 'desenvolvimento', 'conclusao', 'anexo', 'outros')
 */
public enum TipoSecao {
    CABECALHO(1, "cabecalho"),
    EMENTA(2, "ementa"),
    SUMARIO(3, "sumario"),
    INTRODUCAO(4, "introducao"),
    PLANEJAMENTO(5, "planejamento"),
    METODOLOGIA(6, "metodologia"),
    DESENVOLVIMENTO(7, "desenvolvimento"),
    ACHADOS(8, "achados"),
    DEMONSTRATIVO(9, "demonstrativo"),
    RECOMENDACOES(10, "recomendacoes"),
    RESSALVAS_DIVERGENCIAS(11, "ressalvas_divergencias"),
    CONCLUSAO(12, "conclusao"),
    RELATORIO(13, "relatorio"),
    VOTO(14, "voto"),
    DECISAO(15, "decisao"),
    REFERENCIAS_BIBLIOGRAFIAS(16, "referencias_bibliografias"),
    ANEXO(17, "anexo"),
    OUTROS(18, "outros");

    private final int codigo;
    private final String dbValue;

    TipoSecao(int codigo, String dbValue) {
        this.codigo = codigo;
        this.dbValue = dbValue;
    }

    public int getCodigo() {
        return codigo;
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /**
     * Get enum from database value
     */
    @JsonCreator
    public static TipoSecao fromString(String dbValue) {
        for (TipoSecao tipo : values()) {
            if (tipo.dbValue.equals(dbValue))
				return tipo;
        }
        throw new IllegalArgumentException("Unknown tipo_secao value: " + dbValue);
    }

    /**
     * Get enum from code
     */
    public static TipoSecao fromCodigo(int codigo) {
        for (TipoSecao tipo : values()) {
            if (tipo.codigo == codigo)
				return tipo;
        }
        throw new IllegalArgumentException("Unknown tipo_secao code: " + codigo);
    }
}