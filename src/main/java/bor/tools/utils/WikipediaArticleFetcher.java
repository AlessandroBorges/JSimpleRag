package bor.tools.utils;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Deprecated
public class WikipediaArticleFetcher {


    public static String wikiArticleDownloader(String articleTitle, String language){
        // Configura o cliente HTTP
        OkHttpClient client = RAGUtil.getUnsafeOkHttpClient();

        // Monta a URL da API da Wikipedia para obter o conteúdo do artigo
        String url = String.format("https://%s.wikipedia.org/w/api.php?action=parse&page=%s&format=json&prop=text", language, articleTitle);

        // Cria a requisição HTTP
        Request request = new Request.Builder().url(url).build();

        // Executa a requisição
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Extrai a resposta
            String responseData = response.body().string();

            // Aqui você pode processar o JSON e extrair o conteúdo do artigo
            // Nota: Você precisará de uma biblioteca de parsing de JSON como Gson ou Jackson para extrair os dados específicos do JSON
            System.out.println(responseData);
            return responseData;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}

