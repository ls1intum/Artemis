package de.tum.in.www1.artemis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.repository.LectureRepository;

@Service
@Transactional
public class LectureService {

    private LectureRepository lectureRepository;

    public LectureService(LectureRepository lectureRepository) {
        this.lectureRepository = lectureRepository;
    }

    public List<Lecture> findAllByCourseId(Long courseId) {
        return lectureRepository.findAllByCourseId(courseId);
    }

}
