package de.tum.in.www1.artemis.competency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
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
     * Creates competency and links it to the course. The title of the competency will hold the specified suffix.
     *
     * @param course course the competency will be linked to
     * @param suffix the suffix that will be included in the title
     * @return the persisted competency
     */
    private Competency createCompetency(Course course, String suffix) {
        Competency competency = new Competency();
        competency.setTitle("Example Competency" + suffix);
        competency.setDescription("Magna pars studiorum, prodita quaerimus.");
        competency.setCourse(course);
        return competencyRepo.save(competency);
    }

    /**
     * create and save competency with the passed arguments.
     *
     * @param course   the course we want to use the competency in
     * @param exercise the exercise of the competency
     * @return newly created competency
     */
    public Competency createCompetencyWithExercise(Course course, Exercise exercise) {
        Competency competency = new Competency();
        competency.setTitle("ExampleCompetency");
        competency.setCourse(course);
        competency.addExercise(exercise);
        return competencyRepo.save(competency);
    }

    /**
     * Creates competency and links it to the course.
     *
     * @param course course the competency will be linked to
     * @return the persisted competency
     */
    public Competency createCompetency(Course course) {
        return createCompetency(course, "");
    }

    /**
     * Creates multiple competencies and links them to the course.
     *
     * @param course               course the competencies will be linked to
     * @param numberOfCompetencies number of competencies to create
     * @return array of the persisted competencies
     */
    public Competency[] createCompetencies(Course course, int numberOfCompetencies) {
        Competency[] competencies = new Competency[numberOfCompetencies];
        for (int i = 0; i < competencies.length; i++) {
            competencies[i] = createCompetency(course, String.valueOf(i));
        }
        return competencies;
    }

    /**
     * Link lecture unit to competency.
     *
     * @param competency  the competency to link the learning unit to
     * @param lectureUnit the lecture unit that will be linked to the competency
     */
    public void linkLectureUnitToCompetency(Competency competency, LectureUnit lectureUnit) {
        lectureUnit.getCompetencies().add(competency);
        lectureUnitRepository.save(lectureUnit);
    }

    /**
     * Link exercise to competency.
     *
     * @param competency the competency to link the learning unit to
     * @param exercise   the exercise that will be linked to the competency
     */
    public void linkExerciseToCompetency(Competency competency, Exercise exercise) {
        exercise.getCompetencies().add(competency);
        exerciseRepository.save(exercise);
    }

    /**
     * Adds a relation between competencies.
     *
     * @param tail the competency that relates to another competency
     * @param type the type of the relation
     * @param head the competency that the tail competency relates to
     */
    public void addRelation(Competency tail, CompetencyRelation.RelationType type, Competency head) {
        CompetencyRelation relation = new CompetencyRelation();
        relation.setTailCompetency(tail);
        relation.setHeadCompetency(head);
        relation.setType(type);
        competencyRelationRepository.save(relation);
    }
}
