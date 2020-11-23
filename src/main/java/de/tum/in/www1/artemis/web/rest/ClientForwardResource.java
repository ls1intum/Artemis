package de.tum.in.www1.artemis.web.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ClientForwardResource {
    /**
     * Forwards any unmapped paths (except those containing a period) to the client index.html
     */
    @GetMapping(value = "/**/{path:[^\\.]*}")
    public String forward() {
        return "forward:/";
    }
}
