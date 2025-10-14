
 # Carga de Documentos
 Apresentaremos os dados mínimo esperados e o O fluxo de carga de documentos para o RAG.

 ## 1. Carga Normal
 
 ### Dados mínimos:
 
	texto - texto em formato HTML, XHTML, MarkDown ou txt a ser tratado. Confirmar internamente o formato e converter para MarkDown, se for preciso. 
	nome_documento - (opcional) Nome descritivo do documento. Será metadado.
	url - (opcional) origem do documento ou nome do arquivo. Será metadado.
	library_id - (opcional, se houver valor library_uuid) chave primária da biblioteca  
	library_uuid - (opcional, se houver valor library_id) chave uuid da biblioteca
	metadados - (opcional) - objeto JSON do tipo Map (dictionary), com metadados.

 #### Metadados recomendados:
 
	* nome_documento - nome do documento
	* capitulo - capitulo ou sub título
	* descricao - descrição do documento
	* area_conhecimento - area de conhecimento
	* palavras_chave - palavras chave para acesso a este documento
	* autor - autor
	* data_publicacao - data da publicação
	
 	
 ## 2. Carga alternativa 	
 
	Os mesmos dados acima, exceto o campo texto:
	Neste caso a carga poderá ser:
	
	(A) por url - endereço web válido, onde será carregado o arquivo. Poderá ser HTML, TXT, markdown, PDF, MS-Word,	MS-Excel ou PowerPoint
	
	(B) por file upload do tipo multipart.
	
 ### Fluxo de carga alternativa. 
 
	Em ambos os casos (A) e (B), o conteúdo  carregado será convertido em markdown, usando Apache Tika ou Dockling e inserido no fluxo.
	Avalie possibilidade de aproveitamento das classes RAGConverter, XHTMLToMarkdownParser
    Considere a interface bor.tools.utils.DocumentConverter	
	
 ### Sobre DocumentConverter (em inglês)

	Implementation Considerations:

	1. Apache Tika Implementation: This seems like a strong first choice due to its comprehensive format support and Java integration.
	2. Proper Resource Management: Ensure that implementations properly manage resources, especially when dealing with external processes like Pandoc.
	3. Caching Strategy: Consider implementing caching for frequently accessed documents to improve performance.
	4. Testing Strategy: Create a comprehensive test suite with various document formats to ensure reliability.

    Ideas Steps:

	1. Create a basic implementation class, perhaps called TikaDocumentConverter, that uses Apache Tika for format detection and conversion.
	2. Develop a properties file structure for configuration (document-converter.properties).
	3. Integrate with your existing XHTMLToMarkdownParser for HTML/XHTML conversions.
	4. Consider implementing a fallback strategy that tries different conversion methods if the primary one fails.

The DocumentConverter interface is well-designed and aligns with good software engineering practices. With a few refinements and proper implementations, it should serve as an excellent foundation for document conversion functionality in your JSimpleRag project.

 ## Fluxo Sugerido de carga
 
  (a) Carga do documento (upload), por uma das vias acima.
  (b) Detectar formato do documento.
  (b) Para documentos que não são MarkDown padrão, converter usando implementação de DocumentConverter
  (c) Escolher Splitter adequado, usando DocumentRouter.
  (d) usar, onde for necessário, as funcionalidades de SplitterLLMServices,que são implementados em AbstractSplitter e suas subclasses. Metadados, sumarização, perguntas-resposta para fins de geração de DocumentoEmbeddings.
  (e) persitir Documentos, Capitulo (Chapter) e DocEmbeddings.
  (f) Usar serviços de LLM para gerar embeddings, de forma assíncrona.
  (g) Atualizar no banco de dados os embeddings dos objeto DocumentEmbeddings.
	
	
	
	