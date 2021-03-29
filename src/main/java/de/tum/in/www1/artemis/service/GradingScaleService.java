package de.tum.in.www1.artemis.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.GradeStepRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class GradingScaleService {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(GradingScaleService.class);

    private final GradingScaleRepository gradingScaleRepository;

    private final GradeStepRepository gradeStepRepository;

    private final GradeStepService gradeStepService;

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    public GradingScaleService(GradingScaleRepository gradingScaleRepository, GradeStepRepository gradeStepRepository, GradeStepService gradeStepService,
            CourseRepository courseRepository, ExamRepository examRepository) {
        this.gradeStepRepository = gradeStepRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.gradeStepService = gradeStepService;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
    }

    public GradeStep matchGradeToGradeStep(int percentage, Long gradingScaleId) {
        List<GradeStep> gradeSteps = gradeStepService.findAllGradeStepsForGradingScaleById(gradingScaleId);
        Optional<GradeStep> matchingGradeStep = gradeSteps.stream().filter(gradeStep -> gradeStep.matchingGradePercentage(percentage)).findFirst();
        if (matchingGradeStep.isPresent()) {
            return matchingGradeStep.get();
        }
        else {
            throw new EntityNotFoundException("No grade step in selected grading scale matches given percentage");
        }
    }

    public GradingScale updateGradeStepsForGradingScale(Set<GradeStep> newGradeSteps, Long gradingScaleId) {
        if (newGradeSteps.stream().allMatch(GradeStep::isValid)) {
            throw new BadRequestAlertException("Not all grade steps are follow the correct format.", "gradeStep", "invalidFormat");
        }
        if (!gradeStepSetMapsToValidGradingScale(newGradeSteps)) {
            throw new BadRequestAlertException("Grade step set can't match to a valid grading scale.", "gradeStep", "invalidFormat");
        }
        gradeStepRepository.deleteAllGradeStepsForGradingScaleById(gradingScaleId);
        for (GradeStep gradeStep : newGradeSteps) {
            gradeStepService.saveGradeStepForGradingScaleById(gradeStep, gradingScaleId);
        }
        return gradingScaleRepository.findById(gradingScaleId).orElseThrow();
    }

    public GradingScale saveGradingScaleForCourse(GradingScale gradingScale, Long courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow();
        gradingScale.setCourse(course);
        return gradingScaleRepository.saveAndFlush(gradingScale);
    }

    public GradingScale saveGradingScaleForExam(GradingScale gradingScale, Long examId) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        gradingScale.setExam(exam);
        return gradingScaleRepository.saveAndFlush(gradingScale);
    }

    public void deleteGradingScaleById(Long id) {
        gradeStepRepository.deleteById(id);
    }

    private boolean gradeStepSetMapsToValidGradingScale(Set<GradeStep> gradeSteps) {
        if (gradeSteps.stream().map(GradeStep::getGradeName).distinct().count() != gradeSteps.size()) {
            return false;
        }
        List<GradeStep> sortedGradeSteps = gradeSteps.stream().sorted(Comparator.comparingDouble(GradeStep::getLowerBoundPercentage)).collect(Collectors.toList());
        return IntStream.range(0, sortedGradeSteps.size() - 2).allMatch(i -> GradeStep.checkValidAdjacency(sortedGradeSteps.get(i), sortedGradeSteps.get(i + 1)));
    }

}
