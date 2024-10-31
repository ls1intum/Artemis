package de.tum.cit.aet.artemis.iris.web;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("iris")
@RestController
@RequestMapping("api/iris/lecutre-chat/")
public class IrisLectureChatSessionRessource {
}
