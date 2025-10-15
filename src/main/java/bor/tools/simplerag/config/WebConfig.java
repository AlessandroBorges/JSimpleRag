package bor.tools.simplerag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration for JSimpleRag.
 *
 * Configures CORS settings to allow cross-origin requests.
 */
@Configuration
public class WebConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*") // Libera todas as origens
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
    
    /*
     // Exemplo de configuração   
     // caso esta API aceite cookies/autenticação e saiba os IPs fixos da rede, é possível especificar padrões:
         
     @Override
     public void addCorsMappings(CorsRegistry registry) {
    	registry.addMapping("/**")
            .allowedOriginPatterns("http://192.168.*:*") // Padrão para toda a LAN IPv4
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
     }
     */
    
    
}