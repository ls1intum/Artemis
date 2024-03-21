package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.security.annotations.ManualConfig;

@Profile(PROFILE_CORE)
@Controller
public class ClientForwardResource {

    /**
     * Forwards any unmapped paths (except those containing a period) to the client index.html
     * This is important so that reloads of the client app do not lead to NOT FOUND
     *
     * @return Forward Instruction for Browser
     */
    @RequestMapping({ "{path:[^\\.]*}", "{path:^(?!websocket).*}/**/{path:[^\\.]*}" })
    @EnforceNothing
    @ManualConfig
    public String forward() {
        return "forward:/";
    }
}
