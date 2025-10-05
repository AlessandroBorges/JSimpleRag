package bor.tools.simplerag.entity.enums;

/**
 * Enum representing the type of document content.
 * Maps to PostgreSQL enum tipo_conteudo ('livro', 'normativo', 'artigo', 'manual', 'outros')
 */
public enum TipoConteudo {
	CONSTITUICAO(1, "constituicao"),
    LEI(2, "lei"),
    DECRETO(3, "decreto"),
    SUMULA(4, "sumula"),
    ACORDAO(5, "acordao"),
    INSTRUCAO_NORMATIVA(6, "instrucao_normativa"),
    PORTARIA(7, "portaria"),
    NORMATIVO(8, "normativo"),
    TESE(9, "tese"),
    ARTIGO(10, "artigo"),
    LIVRO(11, "livro"),
    RELATORIO(12, "relatorio"),
    NOTA_TECNICA(13, "nota_tecnica"),
    NOTA_AUDITORIA(14, "nota_auditoria"),
    MANUAL(15, "manual"),
    PAPEL_TRABALHO(16, "papel_trabalho"),
    RESUMO(17, "resumo"),
    WIKI(18, "wiki"),
    PROJETO(19, "projeto"),
    DOCUMENTO_INTERNO(20, "documento_interno"),
    OUTROS(21, "outros");

    private final int codigo;
    private final String dbValue;

    TipoConteudo(int codigo, String dbValue) {
        this.codigo = codigo;
        this.dbValue = dbValue;
    }

    public int getCodigo() {
        return codigo;
    }

    public String getDbValue() {
        return dbValue;
    }

    /**
     * Get enum from database value
     */
    public static TipoConteudo fromDbValue(String dbValue) {
        for (TipoConteudo tipo : values()) {
            if (tipo.dbValue.equals(dbValue))
				return tipo;
        }
        throw new IllegalArgumentException("Unknown tipo_conteudo value: " + dbValue);
    }

    /**
     * Get enum from code
     */
    public static TipoConteudo fromCodigo(int codigo) {
        for (TipoConteudo tipo : values()) {
            if (tipo.codigo == codigo)
				return tipo;
        }
        throw new IllegalArgumentException("Unknown tipo_conteudo code: " + codigo);
    }
}