package de.tum.in.www1.artemis.service.lecture_module;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.lecture_module.HTMLModule;
import de.tum.in.www1.artemis.repository.lecture_module.HTMLModuleRepository;

@Service
public class HTMLModuleService {

    private final HTMLModuleRepository htmlModuleRepository;

    public HTMLModuleService(HTMLModuleRepository htmlModuleRepository) {
        this.htmlModuleRepository = htmlModuleRepository;
    }

    public HTMLModule saveHtmlModule(HTMLModule htmlModule) {
        return this.htmlModuleRepository.save(htmlModule);
    }
}
