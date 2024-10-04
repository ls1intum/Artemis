package de.tum.cit.aet.artemis.atlas.competency.util;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyJolRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

/**
 * Service responsible for initializing the database with specific test data related to competencies for use in integration tests.
 */
@Service
public class CompetencyUtilService {

    @Autowired
    private CompetencyRepository competencyRepo;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private CompetencyRelationRepository competencyRelationRepository;

    @Autowired
    private CompetencyJolRepository competencyJOLRepository;

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
        competency.setTaxonomy(CompetencyTaxonomy.UNDERSTAND);
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
    public LectureUnit linkLectureUnitToCompetency(Competency competency, LectureUnit lectureUnit) {
        lectureUnit.getCompetencies().add(competency);
        return lectureUnitRepository.save(lectureUnit);
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

    /**
     * Creates and saves a CompetencyJOL for the given Competency and User.
     *
     * @param competency    The Competency the CompetencyJOL belongs to
     * @param user          The User the CompetencyJOL belongs to
     * @param value         The value of the CompetencyJOL
     * @param judgementTime The time of the judgement
     * @param progress      The progress of the CompetencyJOL
     * @param confidence    The confidence of the CompetencyJOL
     * @return The persisted CompetencyJOL
     */
    public CompetencyJol createJol(Competency competency, User user, short value, ZonedDateTime judgementTime, double progress, double confidence) {
        CompetencyJol jol = new CompetencyJol();
        jol.setCompetency(competency);
        jol.setUser(user);
        jol.setValue(value);
        jol.setJudgementTime(judgementTime);
        jol.setCompetencyProgress(progress);
        jol.setCompetencyConfidence(confidence);
        return competencyJOLRepository.save(jol);
    }
}
