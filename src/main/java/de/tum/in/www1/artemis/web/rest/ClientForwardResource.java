package de.tum.in.www1.artemis.web.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import de.tum.in.www1.artemis.versioning.IgnoreGlobalMapping;

@Controller
public class ClientForwardResource {

    /**
     * Forwards any unmapped paths (except those containing a period) to the client index.html
     * @return Forward Instruction for Browser
     */
    @IgnoreGlobalMapping
    @RequestMapping({ "/{path:[^\\.]*}", "/{path:^(?!websocket).*}/**/{path:[^\\.]*}" })
    public String forward() {
        return "forward:/";
    }
}
