package de.tum.cit.aet.artemis.atlas;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.util.StudentScoreUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyProgressUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.PrerequisiteUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.StandardizedCompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.learningpath.util.LearningPathUtilService;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyJolRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.KnowledgeAreaRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;
import de.tum.cit.aet.artemis.atlas.repository.ScienceSettingRepository;
import de.tum.cit.aet.artemis.atlas.repository.SourceRepository;
import de.tum.cit.aet.artemis.atlas.repository.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyProgressTestRepository;
import de.tum.cit.aet.artemis.atlas.test_repository.LearningPathTestRepository;
import de.tum.cit.aet.artemis.atlas.test_repository.ScienceEventTestRepository;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.ExerciseUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

public abstract class AbstractAtlasIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // Repositories
    @Autowired
    protected CompetencyRepository competencyRepository;

    @Autowired
    protected CourseCompetencyRepository courseCompetencyRepository;

    @Autowired
    protected CompetencyRelationRepository competencyRelationRepository;

    @Autowired
    protected CompetencyProgressTestRepository competencyProgressRepository;

    @Autowired
    protected KnowledgeAreaRepository knowledgeAreaRepository;

    @Autowired
    protected StandardizedCompetencyRepository standardizedCompetencyRepository;

    @Autowired
    protected SourceRepository sourceRepository;

    @Autowired
    protected LearningPathTestRepository learningPathRepository;

    @Autowired
    protected ScienceSettingRepository scienceSettingRepository;

    @Autowired
    protected ScienceEventTestRepository scienceEventRepository;

    @Autowired
    protected PrerequisiteRepository prerequisiteRepository;

    @Autowired
    protected CompetencyJolRepository competencyJolRepository;

    // External Repositories
    @Autowired
    protected LectureRepository lectureRepository;

    @Autowired
    protected LectureUnitRepository lectureUnitRepository;

    @Autowired
    protected GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    protected TextUnitRepository textUnitRepository;

    @Autowired
    protected AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    protected ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    protected SubmissionTestRepository submissionRepository;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    protected ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    // Services

    @Autowired
    protected CompetencyProgressService competencyProgressService;

    @Autowired
    protected FeatureToggleService featureToggleService;

    // External Services
    @Autowired
    protected LectureUnitService lectureUnitService;

    @Autowired
    protected ParticipationService participationService;

    // Util Services
    @Autowired
    protected CompetencyProgressUtilService competencyProgressUtilService;

    @Autowired
    protected CompetencyUtilService competencyUtilService;

    @Autowired
    protected PrerequisiteUtilService prerequisiteUtilService;

    @Autowired
    protected StandardizedCompetencyUtilService standardizedCompetencyUtilService;

    @Autowired
    protected LearningPathUtilService learningPathUtilService;

    // External Util Services
    @Autowired
    protected PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    protected TextExerciseUtilService textExerciseUtilService;

    @Autowired
    protected LectureUtilService lectureUtilService;

    @Autowired
    protected StudentScoreUtilService studentScoreUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    protected TeamUtilService teamUtilService;
}
