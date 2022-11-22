package de.tum.in.www1.artemis.web.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import de.tum.in.www1.artemis.security.annotations.EnforceNothing;

@Controller
public class ClientForwardResource {

    /**
     * Forwards any unmapped paths (except those containing a period) to the client index.html
     * @return Forward Instruction for Browser
     */
    @RequestMapping({ "{path:[^\\.]*}", "{path:^(?!websocket).*}/**/{path:[^\\.]*}" })
    @EnforceNothing
    public String forward() {
        return "forward:/";
    }
}
