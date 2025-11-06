package bor.tools.utils;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Singleton class para Prover serviços HTTP
 */
public class OkHttpProvider {

	private static OkHttpProvider instance;
	private static final int TIMEOUT = 120;
	private static final int CACHE_SIZE = 60 * 1024 * 1024; // 60 MiB
	/**
	 * user-agent default
	 */
	public  static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0";//"Mozilla/5.0 (Linux; Android 8.0.0)";
	public  static final String ACCEPT_LANGUAGE = "pt-BR,pt;q=0.8,en-US;q=0.5,en;q=0.3";
	public  static final String ACCEPT_ENCODING = "gzip, deflate, br";
	
	
	private Cache cache;

	private boolean useCache = false;
	private int CACHE_AGE = 2; // 2 * cadias
	private TimeUnit CACHE_TIME_UNIT = TimeUnit.HOURS;
	private OkHttpClient client;

	/**
	 *
	 */
	private OkHttpProvider() {

		Interceptor cacheResponseInterceptor = null;
		Interceptor cacheRequestInterceptor = null;
		if (useCache) {
			try {
				final CacheControl cacheControl = new CacheControl.Builder()
				                                     .maxAge(CACHE_AGE, CACHE_TIME_UNIT)
				                                     .maxStale(CACHE_SIZE, CACHE_TIME_UNIT)
				                                     .build();

				// Interceptor to ask for cached only responses
				cacheResponseInterceptor = chain -> {
				    Response response = chain.proceed(chain.request());
				    return response.newBuilder()
				            .header("Cache-Control", cacheControl.toString())
				            .header("user-agent", USER_AGENT)
				            .build();
				};
				// Interceptor to cache responses
				cacheRequestInterceptor = chain -> {
				    Request request = chain.request();
				    request = request.newBuilder()
				            .cacheControl(CacheControl.FORCE_CACHE)
				            .header("user-agent", USER_AGENT)
				            .build();

				    return chain.proceed(request);
				};

				String tempDir = System.getProperty("java.io.tmpdir");
				tempDir = tempDir + File.separator + "okhttp.cache";
				System.err.println("Cache htpp em tempDir: " + tempDir);
				File cacheDirectory = new File(tempDir);
				cache = new Cache(cacheDirectory, CACHE_SIZE);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		OkHttpClient.Builder builder = new OkHttpClient.Builder().readTimeout(TIMEOUT, TimeUnit.SECONDS);
		if (cache != null) {
			builder = builder.cache(cache)
					         .addNetworkInterceptor(cacheResponseInterceptor)
					         .addNetworkInterceptor(cacheRequestInterceptor);
		}
		// cria o cliente
		client = builder.build();
	}

	/**
	 * Recupera a instância do provider
	 *
	 * @return
	 */
	public static OkHttpProvider getInstance() {
		if (instance == null) {
			instance = new OkHttpProvider();
		}
		return instance;
	}

	/**
	 * Recupera o cliente HTTP
	 *
	 * @return
	 */
	public OkHttpClient getOkHttpClient() {
		return client;
	}

	/**
	 * Limpa o cache
	 */
	public void clearCache() {
		try {
			cache.evictAll();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Remove uma URL do cache, por exemplo:<br>
	 * <li>"https://www.google.com/"
	 * @param url_prefix - prefixo de URL
	 */
	public void removeURL(String url_prefix) {
		if (cache == null)
			return;
		Iterator<String> urlIterator;
		try {
			urlIterator = cache.urls();
			while (urlIterator.hasNext()) {
				if (urlIterator.next().startsWith(url_prefix)) {
					urlIterator.remove();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
