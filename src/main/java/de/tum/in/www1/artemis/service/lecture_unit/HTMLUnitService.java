package de.tum.in.www1.artemis.service.lecture_unit;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture_unit.HTMLUnit;
import de.tum.in.www1.artemis.repository.lecture_unit.HTMLUnitRepository;
import de.tum.in.www1.artemis.service.LectureService;

@Service
public class HTMLUnitService {

    private final LectureService lectureService;

    public HTMLUnitService(HTMLUnitRepository htmlUnitRepository, LectureService lectureService) {
        this.lectureService = lectureService;
    }

    public HTMLUnit saveHtmlUnit(HTMLUnit htmlUnit) {
        Optional<Lecture> lectureFromDBOptional = lectureService.findByIdWithStudentQuestionsAndLectureModules(htmlUnit.getLecture().getId());
        if (lectureFromDBOptional.isEmpty()) {
            throw new IllegalArgumentException();
        }
        Lecture lectureFromDB = lectureFromDBOptional.get();
        lectureFromDB.addLectureUnit(htmlUnit);

        Lecture savedLecture = lectureService.save(lectureFromDB);

        HTMLUnit persistedHTMLUnit = (HTMLUnit) savedLecture.getLectureUnits().get(savedLecture.getLectureUnits().size() - 1);
        return htmlUnit;
    }
}
