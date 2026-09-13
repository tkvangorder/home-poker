package org.homepoker.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards the Angular client's routes to {@code index.html} so a browser
 * refresh or a deep link on a client route loads the SPA shell.
 * <p>
 * The route list is explicit on purpose: a catch-all would turn every unknown
 * API path into a 200 with the SPA shell. When a route is added to
 * {@code poker-client-angular/src/app/app.routes.ts}, add it here as well.
 */
@Controller
public class SpaForwardingController {

  @GetMapping({"/home", "/game/{gameId}"})
  public String forwardToIndex() {
    return "forward:/index.html";
  }
}
