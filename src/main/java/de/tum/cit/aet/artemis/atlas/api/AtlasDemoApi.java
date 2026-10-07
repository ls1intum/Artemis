package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.factories.CompetencyFactory;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyAtlasMLNotificationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyRelationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyValidationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CourseCompetencyService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;

/**
 * Creates the competencies of the demo course seeded by the {@code demo} profile, together with their relations, the learning paths of the course and the progress of the demo
 * students.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Conditional(AtlasEnabled.class)
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class AtlasDemoApi extends AbstractAtlasApi {

    /**
     * Titles and descriptions of the prerequisite and the competencies of the demo course. A title is the idempotency key of its prerequisite or competency within the demo
     * course, so none of the titles may change.
     */
    private static final String JAVA_TITLE = "Program in Java";

    private static final String JAVA_DESCRIPTION = """
            Write, compile and debug small Java programs with classes, interfaces, collections and loops. The course builds on this from its first lecture on.""";

    private static final String ARCHITECTURE_TITLE = "Evaluate architectural styles";

    private static final String ARCHITECTURE_DESCRIPTION = """
            Compare layered, client-server and microservice architectures by the quality attributes they favour, and judge which style fits a given system.""";

    private static final String ALGORITHMS_TITLE = "Analyze algorithm complexity";

    private static final String ALGORITHMS_DESCRIPTION = """
            Determine the time complexity of an algorithm in Big O notation, tell its best, average and worst case apart, and choose a sorting or searching algorithm that \
            suits the input.""";

    private static final String MODELING_TITLE = "Model a domain with class diagrams";

    private static final String MODELING_DESCRIPTION = """
            Turn requirements into a UML class diagram with classes, attributes, associations, multiplicities, compositions and generalizations.""";

    private static final String COMMUNICATION_TITLE = "Communicate design decisions";

    private static final String COMMUNICATION_DESCRIPTION = """
            Explain a design decision and its trade-offs in writing and with diagrams, so that your team can follow and challenge it.""";

    /**
     * The link weights the client offers when an instructor links a learning object to a competency.
     */
    private static final double HIGH_WEIGHT = 1;

    private static final double MEDIUM_WEIGHT = 0.5;

    private static final double LOW_WEIGHT = 0.25;

    private static final Logger log = LoggerFactory.getLogger(AtlasDemoApi.class);

    private final CourseRepository courseRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final CompetencyLectureUnitLinkRepository competencyLectureUnitLinkRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyValidationService competencyValidationService;

    private final CourseCompetencyService courseCompetencyService;

    private final CompetencyAtlasMLNotificationService atlasMLNotificationService;

    private final CompetencyRelationService competencyRelationService;

    private final LearningPathService learningPathService;

    private final CompetencyProgressService competencyProgressService;

    public AtlasDemoApi(CourseRepository courseRepository, CompetencyExerciseLinkRepository competencyExerciseLinkRepository,
            CompetencyLectureUnitLinkRepository competencyLectureUnitLinkRepository, CompetencyRelationRepository competencyRelationRepository,
            CompetencyValidationService competencyValidationService, CourseCompetencyService courseCompetencyService,
            CompetencyAtlasMLNotificationService atlasMLNotificationService, CompetencyRelationService competencyRelationService, LearningPathService learningPathService,
            CompetencyProgressService competencyProgressService) {
        this.courseRepository = courseRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.competencyLectureUnitLinkRepository = competencyLectureUnitLinkRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyValidationService = competencyValidationService;
        this.courseCompetencyService = courseCompetencyService;
        this.atlasMLNotificationService = atlasMLNotificationService;
        this.competencyRelationService = competencyRelationService;
        this.learningPathService = learningPathService;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * Creates the competencies of the demo course in the given course, as far as they do not exist yet: a prerequisite about programming in Java, a competency for each topic of
     * the course and one about communicating design decisions, which spans the topics. Each competency is linked to the learning objects of its topic and related to the
     * competencies it builds on, and the learning paths of the course are enabled.
     * <p>
     * The prerequisite and the competencies are identified by their title within the course, a relation by the competencies it relates and a link by its competency and
     * learning object. Each of them is created like the production creation paths create it, see the methods below, and only if it is missing, so that deleted demo content
     * comes back on the next startup without touching anything else. Only the given learning objects are linked, so seeding never links one that a user added.
     * <p>
     * The results and lecture unit completions of the demo students exist before the links, and they only update the progress in the competencies they are already linked to.
     * The exercise and lecture unit editors therefore update the progress of everyone who participated when they link a competency, see
     * {@link CompetencyProgressService#updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds}. Seeding updates the progress of the demo students as well, but
     * synchronously, so that it is complete once seeding is done, and on every startup, so that it also counts the links and completions that seeding restored.
     *
     * @param course        the demo course.
     * @param architecture  the exercises and lecture units about software architecture.
     * @param algorithms    the exercises and lecture units about algorithms and their complexity.
     * @param modeling      the exercises and lecture units about object-oriented modeling.
     * @param communication the exercises about communicating design decisions.
     * @param students      the demo students, whose progress is updated.
     */
    public void createDemo(Course course, Collection<? extends LearningObject> architecture, Collection<? extends LearningObject> algorithms,
            Collection<? extends LearningObject> modeling, Collection<? extends LearningObject> communication, List<User> students) {
        // Loaded with its competencies and prerequisites like the creation requests load it, which also tells which of them exist already.
        Course courseWithCompetencies = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(course.getId());
        Set<Competency> competencies = courseWithCompetencies.getCompetencies();
        Prerequisite java = findOrCreate(courseWithCompetencies, courseWithCompetencies.getPrerequisites(),
                new Prerequisite(JAVA_TITLE, JAVA_DESCRIPTION, null, 50, CompetencyTaxonomy.APPLY, false));
        Competency architectureCompetency = findOrCreate(courseWithCompetencies, competencies,
                CompetencyFactory.generateCompetency(ARCHITECTURE_TITLE, ARCHITECTURE_DESCRIPTION, CompetencyTaxonomy.EVALUATE, 70, courseWithCompetencies));
        Competency algorithmsCompetency = findOrCreate(courseWithCompetencies, competencies,
                CompetencyFactory.generateCompetency(ALGORITHMS_TITLE, ALGORITHMS_DESCRIPTION, CompetencyTaxonomy.ANALYZE, 70, courseWithCompetencies));
        Competency modelingCompetency = findOrCreate(courseWithCompetencies, competencies,
                CompetencyFactory.generateCompetency(MODELING_TITLE, MODELING_DESCRIPTION, CompetencyTaxonomy.CREATE, 60, courseWithCompetencies));
        Competency communicationCompetency = findOrCreate(courseWithCompetencies, competencies,
                CompetencyFactory.generateCompetency(COMMUNICATION_TITLE, COMMUNICATION_DESCRIPTION, CompetencyTaxonomy.UNDERSTAND, 50, courseWithCompetencies));

        link(architectureCompetency, architecture.stream(), AtlasDemoApi::topicWeight);
        link(algorithmsCompetency, algorithms.stream(), AtlasDemoApi::topicWeight);
        link(modelingCompetency, modeling.stream(), AtlasDemoApi::topicWeight);
        // Besides the exercises that are about communicating design decisions, the essays and the class diagrams of the topics put design decisions into words and diagrams.
        link(communicationCompetency, communication.stream(), AtlasDemoApi::topicWeight);
        link(communicationCompetency, Stream.concat(architecture.stream(), modeling.stream()).filter(Exercise.class::isInstance), learningObject -> MEDIUM_WEIGHT);

        enableLearningPaths(course.getId());

        // Like CourseCompetencyResource#getCompetencyStudentProgress when a student refreshes their progress. The prerequisite is left out: nothing is linked to it.
        for (Competency competency : List.of(architectureCompetency, algorithmsCompetency, modelingCompetency, communicationCompetency)) {
            students.forEach(student -> competencyProgressService.updateCompetencyProgress(competency.getId(), student));
        }

        // Last, because restoring a relation fails if a relation an instructor added in the meantime would close a cycle with it.
        Set<CompetencyRelation> relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
        relate(courseWithCompetencies, relations, algorithmsCompetency, RelationType.ASSUMES, java);
        relate(courseWithCompetencies, relations, modelingCompetency, RelationType.ASSUMES, java);
        relate(courseWithCompetencies, relations, communicationCompetency, RelationType.EXTENDS, architectureCompetency);
        relate(courseWithCompetencies, relations, communicationCompetency, RelationType.ASSUMES, modelingCompetency);
    }

    /**
     * Returns the competency of the given kind with the title of the given one, or creates the given one like {@code CompetencyResource#createCompetency} and
     * {@code PrerequisiteResource#createPrerequisite} do: validated and stored for the course, and for a competency, AtlasML learns about it.
     *
     * @param course     the course with its competencies and prerequisites.
     * @param existing   the competencies of the given kind that the course already has.
     * @param competency the competency to create if the course has none with its title.
     * @return the existing or the created competency.
     */
    private <C extends CourseCompetency> C findOrCreate(Course course, Set<C> existing, C competency) {
        return existing.stream().filter(existingCompetency -> competency.getTitle().equals(existingCompetency.getTitle())).findFirst().map(existingCompetency -> {
            log.debug("Demo {} '{}' already exists, skipping creation", competency.getType(), competency.getTitle());
            return existingCompetency;
        }).orElseGet(() -> {
            competencyValidationService.checkForCreation(competency);
            C createdCompetency = courseCompetencyService.createCourseCompetency(competency, course);
            if (createdCompetency instanceof Competency created) {
                atlasMLNotificationService.notifyAtlasML(List.of(created), OperationTypeDTO.UPDATE, "demo competency creation");
            }
            log.info("Created demo {} '{}' with id {}", createdCompetency.getType(), createdCompetency.getTitle(), createdCompetency.getId());
            return createdCompetency;
        });
    }

    /**
     * Links the competency to each of the given learning objects that it is not linked to yet, the way the exercise and lecture unit editors store the links, see
     * {@code CompetencyExerciseLinkService#saveAll} and {@code LectureUnitService#linkLectureUnitsToCompetency}. Each link is checked on its own, so that a link that was
     * removed is restored without touching the others.
     *
     * @param competency      the competency to link.
     * @param learningObjects the learning objects to link it to.
     * @param weight          the weight of the link to a learning object.
     */
    private void link(CourseCompetency competency, Stream<? extends LearningObject> learningObjects, ToDoubleFunction<LearningObject> weight) {
        learningObjects.forEach(learningObject -> {
            switch (learningObject) {
                // An exercise unit is linked through its exercise, see CourseCompetency#prePersistOrUpdate.
                case ExerciseUnit ignored -> {
                }
                case Exercise exercise when competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(exercise.getId(), competency.getId()).isEmpty() -> {
                    competencyExerciseLinkRepository.save(new CompetencyExerciseLink(competency, exercise, weight.applyAsDouble(exercise)));
                    log.info("Linked demo competency '{}' to exercise '{}'", competency.getTitle(), exercise.getTitle());
                }
                case LectureUnit lectureUnit when competencyLectureUnitLinkRepository.findByLectureUnitIdAndCompetencyId(lectureUnit.getId(), competency.getId()).isEmpty() -> {
                    competencyLectureUnitLinkRepository.save(new CompetencyLectureUnitLink(competency, lectureUnit, weight.applyAsDouble(lectureUnit)));
                    log.info("Linked demo competency '{}' to lecture unit '{}'", competency.getTitle(), lectureUnit.getName());
                }
                default -> log.debug("Demo competency '{}' is already linked to {} {}, skipping", competency.getTitle(), learningObject.getClass().getSimpleName(),
                        learningObject.getId());
            }
        });
    }

    /**
     * The weight of the link between a competency and a learning object of its topic: the exercises show best whether a student masters the competency, the further reading
     * least.
     */
    private static double topicWeight(LearningObject learningObject) {
        return switch (learningObject) {
            case Exercise ignored -> HIGH_WEIGHT;
            case OnlineUnit ignored -> LOW_WEIGHT;
            default -> MEDIUM_WEIGHT;
        };
    }

    /**
     * Unless the two competencies are related already, relates them like {@code CourseCompetencyResource#createCompetencyRelation}, which rejects a relation that would close a
     * cycle. A relation whose type a user changed is therefore left as it is.
     */
    private void relate(Course course, Set<CompetencyRelation> existingRelations, CourseCompetency tail, RelationType type, CourseCompetency head) {
        if (existingRelations.stream()
                .anyMatch(relation -> relation.getTailCompetency().getId().equals(tail.getId()) && relation.getHeadCompetency().getId().equals(head.getId()))) {
            log.debug("Demo competency relation '{}' {} '{}' already exists, skipping creation", tail.getTitle(), type, head.getTitle());
            return;
        }
        CompetencyRelation relation = competencyRelationService.createCompetencyRelation(tail, head, type, course);
        log.info("Created demo competency relation '{}' {} '{}' with id {}", tail.getTitle(), type, head.getTitle(), relation.getId());
    }

    /**
     * Enables the learning paths of the course like {@code LearningPathResource#enableLearningPathsForCourse}, which also generates the learning path of every student of the
     * course, unless they are enabled already.
     */
    private void enableLearningPaths(long courseId) {
        // Loaded anew like the request does: enabling the learning paths saves the course together with its competencies, so it has to know the ones created above.
        Course course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);
        if (course.getLearningPathsEnabled()) {
            log.debug("Learning paths of the demo course are already enabled, skipping");
            return;
        }
        learningPathService.enableLearningPathsForCourse(course);
        log.info("Enabled the learning paths of demo course '{}'", course.getShortName());
    }
}
