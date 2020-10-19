package de.tum.in.www1.artemis.service.lecture_module;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture_module.HTMLModule;
import de.tum.in.www1.artemis.repository.lecture_module.HTMLModuleRepository;
import de.tum.in.www1.artemis.service.LectureService;

@Service
public class HTMLModuleService {

    private final LectureService lectureService;

    public HTMLModuleService(HTMLModuleRepository htmlModuleRepository, LectureService lectureService) {
        this.lectureService = lectureService;
    }

    public HTMLModule saveHtmlModule(HTMLModule htmlModule) {
        Optional<Lecture> lectureFromDBOptional = lectureService.findByIdWithStudentQuestionsAndLectureModules(htmlModule.getLecture().getId());
        if (lectureFromDBOptional.isEmpty()) {
            throw new IllegalArgumentException();
        }
        Lecture lectureFromDB = lectureFromDBOptional.get();
        lectureFromDB.addLectureModule(htmlModule);

        Lecture savedLecture = lectureService.save(lectureFromDB);

        HTMLModule persistedHTMLModule = (HTMLModule) savedLecture.getLectureModules().get(savedLecture.getLectureModules().size() - 1);
        return htmlModule;
    }
}
