package de.tum.in.www1.artemis.competency;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.RelationType;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.CompetencyRelationRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;

/**
 * Service responsible for initializing the database with specific testdata related to competencies for use in integration tests.
 */
@Service
public class CompetencyUtilService {

    @Autowired
    private CompetencyRepository competencyRepo;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private CompetencyRelationRepository competencyRelationRepository;

    /**
     * Creates and saves a Competency for the given Course.
     *
     * @param course The Course the Competency belongs to
     * @param suffix The suffix that will be added to the title of the Competency
     * @return The created Competency
     */
    private Competency createCompetency(Course course, String suffix) {
        Competency competency = new Competency();
        competency.setTitle("Example Competency" + suffix);
        competency.setDescription("Magna pars studiorum, prodita quaerimus.");
        competency.setCourse(course);
        return competencyRepo.save(competency);
    }

    /**
     * Creates and saves a Competency for the given Course and Exercise.
     *
     * @param course   The Course the Competency belongs to
     * @param exercise The Exercise the Competency belongs to
     * @return The created Competency
     */
    public Competency createCompetencyWithExercise(Course course, Exercise exercise) {
        Competency competency = new Competency();
        competency.setTitle("ExampleCompetency");
        competency.setCourse(course);
        competency.addExercise(exercise);
        return competencyRepo.save(competency);
    }

    /**
     * Creates and saves a Competency for the given Course.
     *
     * @param course The Course the Competency belongs to
     * @return The created Competency
     */
    public Competency createCompetency(Course course) {
        return createCompetency(course, "");
    }

    /**
     * Creates and saves a Competency for the given Course.
     *
     * @param course The Course the Competency belongs to
     * @param time   The soft due date of the competency
     * @return The created Competency
     */
    public Competency createCompetencyWithSoftDueDate(Course course, ZonedDateTime time) {
        final var competency = createCompetency(course);
        competency.setSoftDueDate(time);
        return competencyRepo.save(competency);
    }

    /**
     * Creates and saves a Competency for each given soft due date and links them to the Course.
     *
     * @param course       The Course the Competencies belong to
     * @param softDueDates The soft due dates of the Competencies
     * @return An array of the created Competencies
     */
    public Competency[] createCompetencies(Course course, ZonedDateTime... softDueDates) {
        Competency[] competencies = new Competency[softDueDates.length];
        for (int i = 0; i < competencies.length; i++) {
            competencies[i] = createCompetencyWithSoftDueDate(course, softDueDates[i]);
        }
        return competencies;
    }

    /**
     * Creates and saves the given number of Competencies for the given Course.
     *
     * @param course               The Course the Competencies belong to
     * @param numberOfCompetencies The number of Competencies to create
     * @return An array of the created Competencies
     */
    public Competency[] createCompetencies(Course course, int numberOfCompetencies) {
        Competency[] competencies = new Competency[numberOfCompetencies];
        for (int i = 0; i < competencies.length; i++) {
            competencies[i] = createCompetency(course, String.valueOf(i));
        }
        return competencies;
    }

    /**
     * Updates and saves the LectureUnit's Competencies by adding the given Competency.
     *
     * @param competency  The Competency to add to the LectureUnit
     * @param lectureUnit The LectureUnit to update
     */
    public void linkLectureUnitToCompetency(Competency competency, LectureUnit lectureUnit) {
        lectureUnit.getCompetencies().add(competency);
        lectureUnitRepository.save(lectureUnit);
    }

    /**
     * Updates and saves the Exercise's Competencies by adding the given Competency.
     *
     * @param competency The Competency to add to the Exercise
     * @param exercise   The Exercise to update
     * @return The updated Exercise
     */
    public Exercise linkExerciseToCompetency(Competency competency, Exercise exercise) {
        exercise.getCompetencies().add(competency);
        return exerciseRepository.save(exercise);
    }

    /**
     * Creates and saves a CompetencyRelation for the given Competencies.
     *
     * @param tail The Competency that relates to the head Competency
     * @param type The type of the relation
     * @param head The Competency that the tail Competency relates to
     */
    public void addRelation(Competency tail, RelationType type, Competency head) {
        CompetencyRelation relation = new CompetencyRelation();
        relation.setTailCompetency(tail);
        relation.setHeadCompetency(head);
        relation.setType(type);
        competencyRelationRepository.save(relation);
    }

    /**
     * Updates and saves the Competency's mastery threshold.
     *
     * @param competency       The Competency to update
     * @param masteryThreshold The new mastery threshold
     * @return The updated Competency
     */
    public Competency updateMasteryThreshold(@NotNull Competency competency, int masteryThreshold) {
        competency.setMasteryThreshold(masteryThreshold);
        return competencyRepo.save(competency);
    }
}
