package bor.tools.simplerag.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Root controller for redirecting to Swagger UI.
 *
 * When accessing the root URL (http://localhost:8080/),
 * redirects to the Swagger UI documentation page.
 */
@Controller
public class RootController {

    /**
     * Redirect root URL to Swagger UI
     *
     * @return redirect to swagger-ui.html
     */
    @GetMapping("/")
    public String redirectToSwagger() {
        return "redirect:/swagger-ui.html";
    }
}
