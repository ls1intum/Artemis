package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.Entity;
import jakarta.validation.Valid;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;

/**
 * Enforces that every request body bound to an Artemis DTO is actually validated (Layer 2b / Safeguard S1).
 * <p>
 * Spring runs Bean Validation on a controller body parameter only when the parameter carries
 * {@link Valid} (or {@link Validated}). Without it, any {@code @Size}/{@code @Min}/{@code @Max} on the DTO is
 * silently dead, so oversized or malformed input is bound unchecked (findings F-005/F-007). This test fails
 * when a {@code @RequestBody}/{@code @RequestPart} parameter whose (possibly wrapped) type is an Artemis DTO
 * is missing {@code @Valid}/{@code @Validated}.
 * <p>
 * Accepting entity types as request bodies is a separate concern, enforced by the per-module entity-usage
 * tests; entities are therefore skipped here to avoid double-reporting.
 * <p>
 * The {@link #KNOWN_UNVALIDATED} set is a self-shrinking ratchet of the request bodies that are currently
 * unvalidated: the test is green on day one and fails for any NEW unvalidated request DTO. It also fails for
 * a stale entry that no longer matches a real violation, so the list can only shrink as {@code @Valid} is
 * added. Each entry is keyed by the fully-qualified identity
 * {@code <ControllerFqn>#<method>(<rawParamTypeNames>)#p<index>:<dtoFqn>}, where the parameter-type list uses
 * fully-qualified type names so overloads cannot collide.
 */
class RequestBodyValidationArchitectureTest extends AbstractArchitectureTest {

    /**
     * Request-body DTO parameters that currently lack {@code @Valid}/{@code @Validated}, grandfathered so the
     * guard is green on introduction. This list must only shrink: add {@code @Valid} and remove the entry.
     */
    private static final Set<String> KNOWN_UNVALIDATED = Set.of(
            "de.tum.cit.aet.artemis.account.web.AccountResource#changePassword(de.tum.cit.aet.artemis.core.dto.PasswordChangeDTO)#p0:de.tum.cit.aet.artemis.core.dto.PasswordChangeDTO",
            "de.tum.cit.aet.artemis.account.web.AccountResource#revokeCredentials(de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO)#p0:de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO",
            "de.tum.cit.aet.artemis.account.web.PasskeyResource#updatePasskeyLabel(java.lang.String,de.tum.cit.aet.artemis.account.dto.PasskeyDTO)#p1:de.tum.cit.aet.artemis.account.dto.PasskeyDTO",
            "de.tum.cit.aet.artemis.account.web.UserResource#setSelectedLLMUsageToTimestampAndEnum(de.tum.cit.aet.artemis.core.dto.SelectedLLMUsageDTO)#p0:de.tum.cit.aet.artemis.core.dto.SelectedLLMUsageDTO",
            "de.tum.cit.aet.artemis.account.web.admin.AdminUserResource#importUsers(java.util.List)#p0:de.tum.cit.aet.artemis.core.dto.StudentDTO",
            "de.tum.cit.aet.artemis.admin.web.AdminImprintResource#updateImprint(de.tum.cit.aet.artemis.admin.dto.ImprintDTO)#p0:de.tum.cit.aet.artemis.admin.dto.ImprintDTO",
            "de.tum.cit.aet.artemis.admin.web.AdminLogResource#changeLevel(de.tum.cit.aet.artemis.core.dto.vm.LoggerVM)#p0:de.tum.cit.aet.artemis.core.dto.vm.LoggerVM",
            "de.tum.cit.aet.artemis.admin.web.AdminPrivacyStatementResource#updatePrivacyStatement(de.tum.cit.aet.artemis.admin.dto.PrivacyStatementDTO)#p0:de.tum.cit.aet.artemis.admin.dto.PrivacyStatementDTO",
            "de.tum.cit.aet.artemis.assessment.web.BonusResource#createBonusForExam(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.assessment.dto.BonusRequestDTO)#p2:de.tum.cit.aet.artemis.assessment.dto.BonusRequestDTO",
            "de.tum.cit.aet.artemis.assessment.web.BonusResource#updateBonus(java.lang.Long,java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.assessment.dto.BonusRequestDTO)#p3:de.tum.cit.aet.artemis.assessment.dto.BonusRequestDTO",
            "de.tum.cit.aet.artemis.assessment.web.ComplaintResource#createComplaint(de.tum.cit.aet.artemis.assessment.dto.ComplaintRequestDTO,java.security.Principal)#p0:de.tum.cit.aet.artemis.assessment.dto.ComplaintRequestDTO",
            "de.tum.cit.aet.artemis.assessment.web.ComplaintResponseResource#refreshLockOrResolveComplaint(de.tum.cit.aet.artemis.assessment.dto.ComplaintResponseUpdateDTO,long)#p0:de.tum.cit.aet.artemis.assessment.dto.ComplaintResponseUpdateDTO",
            "de.tum.cit.aet.artemis.assessment.web.ExampleSubmissionResource#createExampleSubmission(java.lang.Long,de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionRequestDTO)#p1:de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionRequestDTO",
            "de.tum.cit.aet.artemis.assessment.web.ExampleSubmissionResource#updateExampleSubmission(java.lang.Long,de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionRequestDTO)#p1:de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionRequestDTO",
            "de.tum.cit.aet.artemis.assessment.web.ResultResource#createResultForExternalSubmission(java.lang.Long,java.lang.String,de.tum.cit.aet.artemis.assessment.dto.ExternalSubmissionResultDTO)#p2:de.tum.cit.aet.artemis.assessment.dto.ExternalSubmissionResultDTO",
            "de.tum.cit.aet.artemis.assessment.web.TutorParticipationResource#assessExampleSubmissionForTutorParticipation(java.lang.Long,de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionRequestDTO)#p1:de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionRequestDTO",
            "de.tum.cit.aet.artemis.atlas.web.CompetencyResource#importAllCompetenciesFromCourse(long,de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO)#p1:de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO",
            "de.tum.cit.aet.artemis.atlas.web.CompetencyResource#importCompetencies(long,de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO)#p1:de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO",
            "de.tum.cit.aet.artemis.atlas.web.CompetencyResource#importCompetency(long,de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO)#p1:de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO",
            "de.tum.cit.aet.artemis.atlas.web.CompetencyResource#suggestCompetencies(de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO)#p0:de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO",
            "de.tum.cit.aet.artemis.atlas.web.CourseCompetencyResource#createCompetencyRelation(long,de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO)#p1:de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO",
            "de.tum.cit.aet.artemis.atlas.web.CourseCompetencyResource#importAllCompetenciesFromCourse(long,de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO)#p1:de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO",
            "de.tum.cit.aet.artemis.atlas.web.CourseLearnerProfileResource#updateCourseLearnerProfile(long,de.tum.cit.aet.artemis.atlas.dto.CourseLearnerProfileDTO)#p1:de.tum.cit.aet.artemis.atlas.dto.CourseLearnerProfileDTO",
            "de.tum.cit.aet.artemis.atlas.web.PrerequisiteResource#importAllPrerequisitesFromCourse(long,de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO)#p1:de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO",
            "de.tum.cit.aet.artemis.atlas.web.PrerequisiteResource#importPrerequisite(long,de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO)#p1:de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO",
            "de.tum.cit.aet.artemis.atlas.web.PrerequisiteResource#importPrerequisites(long,de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO)#p1:de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO",
            "de.tum.cit.aet.artemis.atlas.web.ScienceResource#science(de.tum.cit.aet.artemis.atlas.dto.ScienceEventDTO)#p0:de.tum.cit.aet.artemis.atlas.dto.ScienceEventDTO",
            "de.tum.cit.aet.artemis.atlas.web.ScienceSettingsResource#saveScienceSettingsForCurrentUser(de.tum.cit.aet.artemis.atlas.dto.ScienceSettingDTO[])#p0:de.tum.cit.aet.artemis.atlas.dto.ScienceSettingDTO",
            "de.tum.cit.aet.artemis.communication.web.ConversationMessageResource#updateMessage(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.communication.dto.UpdatePostingDTO)#p2:de.tum.cit.aet.artemis.communication.dto.UpdatePostingDTO",
            "de.tum.cit.aet.artemis.communication.web.ForwardedMessageResource#createForwardedMessage(de.tum.cit.aet.artemis.communication.dto.ForwardedMessageDTO)#p0:de.tum.cit.aet.artemis.communication.dto.ForwardedMessageDTO",
            "de.tum.cit.aet.artemis.communication.web.conversation.ChannelResource#createChannel(java.lang.Long,de.tum.cit.aet.artemis.communication.dto.ChannelDTO)#p1:de.tum.cit.aet.artemis.communication.dto.ChannelDTO",
            "de.tum.cit.aet.artemis.communication.web.conversation.ChannelResource#createFeedbackChannel(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.communication.dto.FeedbackChannelRequestDTO)#p2:de.tum.cit.aet.artemis.communication.dto.FeedbackChannelRequestDTO",
            "de.tum.cit.aet.artemis.communication.web.conversation.ChannelResource#updateChannel(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.communication.dto.ChannelDTO)#p2:de.tum.cit.aet.artemis.communication.dto.ChannelDTO",
            "de.tum.cit.aet.artemis.communication.web.conversation.GroupChatResource#startGroupChat(java.lang.Long,de.tum.cit.aet.artemis.communication.dto.GroupChatCreationDTO)#p1:de.tum.cit.aet.artemis.communication.dto.GroupChatCreationDTO",
            "de.tum.cit.aet.artemis.communication.web.conversation.GroupChatResource#updateGroupChat(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.communication.dto.GroupChatDTO)#p2:de.tum.cit.aet.artemis.communication.dto.GroupChatDTO",
            "de.tum.cit.aet.artemis.communication.web.conversation.OneToOneChatResource#startOneToOneChat(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.communication.dto.OneToOneChatCreationDTO)#p2:de.tum.cit.aet.artemis.communication.dto.OneToOneChatCreationDTO",
            "de.tum.cit.aet.artemis.core.web.open.PublicAccountResource#finishPasswordReset(de.tum.cit.aet.artemis.core.dto.vm.KeyAndPasswordVM)#p0:de.tum.cit.aet.artemis.core.dto.vm.KeyAndPasswordVM",
            "de.tum.cit.aet.artemis.core.web.open.PublicUserJwtResource#exchangeCodeToJwtToken(de.tum.cit.aet.artemis.account.dto.OIDCCodeExchangeDTO)#p0:de.tum.cit.aet.artemis.account.dto.OIDCCodeExchangeDTO",
            "de.tum.cit.aet.artemis.course.web.CourseAccessResource#addUsersToCourseRole(java.lang.Long,java.lang.String,java.util.List)#p2:de.tum.cit.aet.artemis.core.dto.StudentDTO",
            "de.tum.cit.aet.artemis.course.web.CourseAthenaConfigResource#updateAthenaConfig(long,de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigUpdateDTO)#p1:de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigUpdateDTO",
            "de.tum.cit.aet.artemis.course.web.CourseMaterialImportResource#importCourseMaterial(long,de.tum.cit.aet.artemis.course.dto.CourseMaterialImportOptionsDTO)#p1:de.tum.cit.aet.artemis.course.dto.CourseMaterialImportOptionsDTO",
            "de.tum.cit.aet.artemis.exam.web.ExamResource#addStudentsToExam(java.lang.Long,java.lang.Long,java.util.List)#p2:de.tum.cit.aet.artemis.exam.dto.ExamUserDTO",
            "de.tum.cit.aet.artemis.exam.web.ExamResource#createExam(java.lang.Long,de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO)#p1:de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO",
            "de.tum.cit.aet.artemis.exam.web.ExamResource#updateExam(java.lang.Long,de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO)#p1:de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO",
            "de.tum.cit.aet.artemis.exam.web.ExamUserResource#updateExamUser(de.tum.cit.aet.artemis.exam.dto.ExamUserDTO,org.springframework.web.multipart.MultipartFile,java.lang.Long,java.lang.Long)#p0:de.tum.cit.aet.artemis.exam.dto.ExamUserDTO",
            "de.tum.cit.aet.artemis.exam.web.ExerciseGroupResource#createExerciseGroup(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.exam.dto.ExerciseGroupCreateDTO)#p2:de.tum.cit.aet.artemis.exam.dto.ExerciseGroupCreateDTO",
            "de.tum.cit.aet.artemis.exam.web.ExerciseGroupResource#updateExerciseGroup(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.exam.dto.ExerciseGroupUpdateDTO)#p2:de.tum.cit.aet.artemis.exam.dto.ExerciseGroupUpdateDTO",
            "de.tum.cit.aet.artemis.exam.web.StudentExamResource#createTestRun(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.exam.dto.CreateTestRunDTO)#p2:de.tum.cit.aet.artemis.exam.dto.CreateTestRunDTO",
            "de.tum.cit.aet.artemis.exam.web.StudentExamResource#submitStudentExam(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.exam.dto.submit.SubmitStudentExamDTO)#p2:de.tum.cit.aet.artemis.exam.dto.submit.SubmitStudentExamDTO",
            "de.tum.cit.aet.artemis.exercise.web.ExerciseVariantGroupResource#setExerciseVariantGroup(de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupAssignmentDTO,java.lang.Long,java.lang.Long)#p0:de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupAssignmentDTO",
            "de.tum.cit.aet.artemis.exercise.web.TeamResource#createTeam(de.tum.cit.aet.artemis.exercise.dto.TeamInputDTO,long)#p0:de.tum.cit.aet.artemis.exercise.dto.TeamInputDTO",
            "de.tum.cit.aet.artemis.exercise.web.TeamResource#importTeamsFromList(long,java.util.List,de.tum.cit.aet.artemis.exercise.dto.TeamImportStrategyType)#p1:de.tum.cit.aet.artemis.exercise.dto.TeamImportDTO",
            "de.tum.cit.aet.artemis.exercise.web.TeamResource#updateTeam(de.tum.cit.aet.artemis.exercise.dto.TeamInputDTO,long,long)#p0:de.tum.cit.aet.artemis.exercise.dto.TeamInputDTO",
            "de.tum.cit.aet.artemis.fileupload.web.FileUploadExerciseResource#createFileUploadExercise(de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseInputDTO)#p0:de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseInputDTO",
            "de.tum.cit.aet.artemis.fileupload.web.FileUploadExerciseResource#exportSubmissions(long,de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO)#p1:de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO",
            "de.tum.cit.aet.artemis.fileupload.web.FileUploadExerciseResource#importFileUploadExercise(long,de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseInputDTO)#p1:de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseInputDTO",
            "de.tum.cit.aet.artemis.fileupload.web.FileUploadExerciseResource#reEvaluateAndUpdateFileUploadExercise(long,de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO,java.lang.Boolean)#p1:de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO",
            "de.tum.cit.aet.artemis.fileupload.web.FileUploadExerciseResource#updateFileUploadExercise(de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO,java.lang.String,java.lang.Long)#p0:de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO",
            "de.tum.cit.aet.artemis.fileupload.web.FileUploadSubmissionResource#createFileUploadSubmission(long,de.tum.cit.aet.artemis.fileupload.dto.FileUploadSubmissionInputDTO,org.springframework.web.multipart.MultipartFile)#p1:de.tum.cit.aet.artemis.fileupload.dto.FileUploadSubmissionInputDTO",
            "de.tum.cit.aet.artemis.hyperion.web.HyperionProblemStatementResource#rewriteProblemStatement(long,de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteRequestDTO)#p1:de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteRequestDTO",
            "de.tum.cit.aet.artemis.iris.web.IrisMessageResource#saveMcqResponse(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.iris.dto.IrisMcqResponseDTO)#p2:de.tum.cit.aet.artemis.iris.dto.IrisMcqResponseDTO",
            "de.tum.cit.aet.artemis.iris.web.IrisStruggleInterventionResource#cancelStruggleJob(long,de.tum.cit.aet.artemis.iris.dto.CancelStruggleJobRequestDTO)#p1:de.tum.cit.aet.artemis.iris.dto.CancelStruggleJobRequestDTO",
            "de.tum.cit.aet.artemis.iris.web.IrisStruggleInterventionResource#revealAmbient(long,java.lang.String,de.tum.cit.aet.artemis.iris.dto.RevealAmbientRequestDTO)#p2:de.tum.cit.aet.artemis.iris.dto.RevealAmbientRequestDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalIngestionWorkerResource#claimJobs(de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimRequestDTO,jakarta.servlet.http.HttpServletRequest)#p0:de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimRequestDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalIngestionWorkerResource#heartbeat(de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerHeartbeatRequestDTO,jakarta.servlet.http.HttpServletRequest)#p0:de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerHeartbeatRequestDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource#executeChatCommand(java.lang.String,de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisCommandDTO,jakarta.servlet.http.HttpServletRequest)#p1:de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisCommandDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource#setAutonomousTutorJobStatus(java.lang.String,de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO,jakarta.servlet.http.HttpServletRequest)#p1:de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource#setCompetencyExtractionJobStatus(java.lang.String,de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO,jakarta.servlet.http.HttpServletRequest)#p1:de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource#setStatusOfChatJob(java.lang.String,de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO,jakarta.servlet.http.HttpServletRequest)#p1:de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource#setStatusOfFaqIngestionJob(java.lang.String,de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO,jakarta.servlet.http.HttpServletRequest)#p1:de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource#setStatusOfGlobalSearchAnswerJob(java.lang.String,de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO,jakarta.servlet.http.HttpServletRequest)#p1:de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource#setStatusOfIngestionJob(java.lang.String,de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO,jakarta.servlet.http.HttpServletRequest)#p1:de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource#setStruggleInterventionJobStatus(java.lang.String,de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO,jakarta.servlet.http.HttpServletRequest)#p1:de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO",
            "de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource#setTutorSuggestionJobStatus(java.lang.String,de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO,jakarta.servlet.http.HttpServletRequest)#p1:de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO",
            "de.tum.cit.aet.artemis.lecture.web.AttachmentVideoUnitResource#createAttachmentVideoUnit(java.lang.Long,de.tum.cit.aet.artemis.lecture.dto.AttachmentVideoUnitDTO,de.tum.cit.aet.artemis.lecture.dto.AttachmentDTO,org.springframework.web.multipart.MultipartFile,boolean)#p2:de.tum.cit.aet.artemis.lecture.dto.AttachmentDTO",
            "de.tum.cit.aet.artemis.lecture.web.AttachmentVideoUnitResource#createAttachmentVideoUnits(java.lang.Long,de.tum.cit.aet.artemis.lecture.dto.LectureUnitSplitInformationDTO,java.lang.String)#p1:de.tum.cit.aet.artemis.lecture.dto.LectureUnitSplitInformationDTO",
            "de.tum.cit.aet.artemis.lecture.web.AttachmentVideoUnitResource#updateAttachmentVideoUnit(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.lecture.dto.AttachmentVideoUnitDTO,de.tum.cit.aet.artemis.lecture.dto.AttachmentDTO,org.springframework.web.multipart.MultipartFile,org.springframework.web.multipart.MultipartFile,java.util.List,java.util.List,boolean,java.lang.String)#p3:de.tum.cit.aet.artemis.lecture.dto.AttachmentDTO",
            "de.tum.cit.aet.artemis.lecture.web.AttachmentVideoUnitResource#updateAttachmentVideoUnit(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.lecture.dto.AttachmentVideoUnitDTO,de.tum.cit.aet.artemis.lecture.dto.AttachmentDTO,org.springframework.web.multipart.MultipartFile,org.springframework.web.multipart.MultipartFile,java.util.List,java.util.List,boolean,java.lang.String)#p6:de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO",
            "de.tum.cit.aet.artemis.lecture.web.AttachmentVideoUnitResource#updateAttachmentVideoUnit(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.lecture.dto.AttachmentVideoUnitDTO,de.tum.cit.aet.artemis.lecture.dto.AttachmentDTO,org.springframework.web.multipart.MultipartFile,org.springframework.web.multipart.MultipartFile,java.util.List,java.util.List,boolean,java.lang.String)#p7:de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO",
            "de.tum.cit.aet.artemis.lecture.web.ExerciseUnitResource#createExerciseUnit(java.lang.Long,de.tum.cit.aet.artemis.lecture.dto.ExerciseUnitDTO)#p1:de.tum.cit.aet.artemis.lecture.dto.ExerciseUnitDTO",
            "de.tum.cit.aet.artemis.lecture.web.LectureResource#createLecture(de.tum.cit.aet.artemis.lecture.web.LectureResource$SimpleLectureDTO)#p0:de.tum.cit.aet.artemis.lecture.web.LectureResource$SimpleLectureDTO",
            "de.tum.cit.aet.artemis.lecture.web.LectureResource#createLectureSeries(long,java.util.List)#p1:de.tum.cit.aet.artemis.lecture.dto.LectureSeriesCreateLectureDTO",
            "de.tum.cit.aet.artemis.lecture.web.LectureResource#updateLecture(de.tum.cit.aet.artemis.lecture.web.LectureResource$SimpleLectureDTO)#p0:de.tum.cit.aet.artemis.lecture.web.LectureResource$SimpleLectureDTO",
            "de.tum.cit.aet.artemis.lecture.web.TextUnitResource#createTextUnit(java.lang.Long,de.tum.cit.aet.artemis.lecture.dto.TextUnitDTO)#p1:de.tum.cit.aet.artemis.lecture.dto.TextUnitDTO",
            "de.tum.cit.aet.artemis.lecture.web.TextUnitResource#updateTextUnit(java.lang.Long,de.tum.cit.aet.artemis.lecture.dto.TextUnitDTO)#p1:de.tum.cit.aet.artemis.lecture.dto.TextUnitDTO",
            "de.tum.cit.aet.artemis.localci.web.AutomaticAfterDueDateResource#previewAutomaticAfterDueDate(de.tum.cit.aet.artemis.programming.dto.AutomaticAfterDueDatePreviewRequestDTO)#p0:de.tum.cit.aet.artemis.programming.dto.AutomaticAfterDueDatePreviewRequestDTO",
            "de.tum.cit.aet.artemis.localci.web.BuildPlanResource#setBuildPlan(java.lang.Long,de.tum.cit.aet.artemis.programming.dto.BuildPlanRequestDTO)#p1:de.tum.cit.aet.artemis.programming.dto.BuildPlanRequestDTO",
            "de.tum.cit.aet.artemis.modeling.web.ApollonConversionResource#convertApollonModel(de.tum.cit.aet.artemis.modeling.dto.ApollonModelDTO)#p0:de.tum.cit.aet.artemis.modeling.dto.ApollonModelDTO",
            "de.tum.cit.aet.artemis.modeling.web.ApollonDiagramResource#updateApollonDiagram(de.tum.cit.aet.artemis.modeling.dto.ApollonDiagramUpdateDTO,java.lang.Long)#p0:de.tum.cit.aet.artemis.modeling.dto.ApollonDiagramUpdateDTO",
            "de.tum.cit.aet.artemis.modeling.web.ModelingAssessmentResource#saveModelingAssessment(long,long,boolean,de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentDTO)#p3:de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentDTO",
            "de.tum.cit.aet.artemis.modeling.web.ModelingAssessmentResource#saveModelingExampleAssessment(long,java.util.List)#p1:de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO",
            "de.tum.cit.aet.artemis.modeling.web.ModelingAssessmentResource#updateModelingAssessmentAfterComplaint(java.lang.Long,de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentUpdateDTO)#p1:de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentUpdateDTO",
            "de.tum.cit.aet.artemis.modeling.web.ModelingExerciseResource#createModelingExercise(de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO)#p0:de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO",
            "de.tum.cit.aet.artemis.modeling.web.ModelingExerciseResource#exportSubmissions(long,de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO)#p1:de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO",
            "de.tum.cit.aet.artemis.modeling.web.ModelingExerciseResource#importExercise(long,de.tum.cit.aet.artemis.modeling.dto.ImportModelingExerciseDTO)#p1:de.tum.cit.aet.artemis.modeling.dto.ImportModelingExerciseDTO",
            "de.tum.cit.aet.artemis.modeling.web.ModelingExerciseResource#reEvaluateAndUpdateModelingExercise(long,de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO,java.lang.Boolean)#p1:de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO",
            "de.tum.cit.aet.artemis.modeling.web.ModelingExerciseResource#updateModelingExercise(de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO,java.lang.String)#p0:de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO",
            "de.tum.cit.aet.artemis.notification.web.GlobalNotificationSettingResource#updateSetting(de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType,de.tum.cit.aet.artemis.notification.dto.UpdateGlobalNotificationSettingDTO)#p1:de.tum.cit.aet.artemis.notification.dto.UpdateGlobalNotificationSettingDTO",
            "de.tum.cit.aet.artemis.notification.web.PushNotificationResource#unregister(java.lang.String,de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType,de.tum.cit.aet.artemis.notification.dto.PushNotificationUnregisterRequestDTO)#p2:de.tum.cit.aet.artemis.notification.dto.PushNotificationUnregisterRequestDTO",
            "de.tum.cit.aet.artemis.notification.web.UserCourseNotificationSettingResource#setSettingSpecification(java.lang.Long,de.tum.cit.aet.artemis.notification.dto.CourseNotificationSettingSpecificationRequestDTO)#p1:de.tum.cit.aet.artemis.notification.dto.CourseNotificationSettingSpecificationRequestDTO",
            "de.tum.cit.aet.artemis.notification.web.UserCourseNotificationStatusResource#markDisplayedNotificationsAsSeen(java.lang.Long,de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSeenRequestDTO)#p1:de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSeenRequestDTO",
            "de.tum.cit.aet.artemis.notification.web.UserCourseNotificationStatusResource#updateNotificationStatus(java.lang.Long,de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationStatusUpdateRequestDTO)#p1:de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationStatusUpdateRequestDTO",
            "de.tum.cit.aet.artemis.notification.web.admin.AdminSystemNotificationResource#createSystemNotification(de.tum.cit.aet.artemis.notification.dto.SystemNotificationUpdateDTO,boolean)#p0:de.tum.cit.aet.artemis.notification.dto.SystemNotificationUpdateDTO",
            "de.tum.cit.aet.artemis.notification.web.admin.AdminSystemNotificationResource#updateSystemNotification(de.tum.cit.aet.artemis.notification.dto.SystemNotificationUpdateDTO)#p0:de.tum.cit.aet.artemis.notification.dto.SystemNotificationUpdateDTO",
            "de.tum.cit.aet.artemis.plagiarism.web.PlagiarismAnswerPostResource#updateAnswerPost(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismAnswerPostUpdateRequestDTO)#p2:de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismAnswerPostUpdateRequestDTO",
            "de.tum.cit.aet.artemis.plagiarism.web.PlagiarismPostResource#updatePost(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismPostUpdateRequestDTO)#p2:de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismPostUpdateRequestDTO",
            "de.tum.cit.aet.artemis.plagiarism.web.PlagiarismResource#updatePlagiarismComparisonStatus(long,long,de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismComparisonStatusDTO)#p2:de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismComparisonStatusDTO",
            "de.tum.cit.aet.artemis.programming.web.ExerciseSharingResource#getExerciseDetails(de.tum.cit.aet.artemis.core.dto.SharingInfoDTO)#p0:de.tum.cit.aet.artemis.core.dto.SharingInfoDTO",
            "de.tum.cit.aet.artemis.programming.web.ExerciseSharingResource#setUpFromSharingImport(de.tum.cit.aet.artemis.programming.service.sharing.SharingSetupInfoDTO)#p0:de.tum.cit.aet.artemis.programming.service.sharing.SharingSetupInfoDTO",
            "de.tum.cit.aet.artemis.programming.web.IdeSettingsResource#setIde(de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage,de.tum.cit.aet.artemis.programming.dto.IdeDTO)#p1:de.tum.cit.aet.artemis.programming.dto.IdeDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingAssessmentResource#saveProgrammingAssessment(java.lang.Long,boolean,de.tum.cit.aet.artemis.programming.dto.ProgrammingManualResultRequestDTO)#p2:de.tum.cit.aet.artemis.programming.dto.ProgrammingManualResultRequestDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingAssessmentResource#updateProgrammingManualResultAfterComplaint(de.tum.cit.aet.artemis.programming.dto.ProgrammingAssessmentUpdateDTO,long)#p0:de.tum.cit.aet.artemis.programming.dto.ProgrammingAssessmentUpdateDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseCreationResource#createProgrammingExercise(de.tum.cit.aet.artemis.programming.dto.CreateProgrammingExerciseDTO,boolean,java.util.Set)#p0:de.tum.cit.aet.artemis.programming.dto.CreateProgrammingExerciseDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseDeletionResource#reset(java.lang.Long,de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResetOptionsDTO)#p1:de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResetOptionsDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseDeletionResource#resetParticipations(java.lang.Long,de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResetOptionsDTO)#p1:de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResetOptionsDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseExportImportResource#exportSelectedSubmissionsForInstructor(long,java.lang.String,de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO)#p2:de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseExportImportResource#exportSubmissionsByParticipationIds(long,java.lang.String,de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO)#p2:de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseExportImportResource#exportSubmissionsByStudentLogins(long,java.lang.String,de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO)#p2:de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseExportImportResource#importProgrammingExercise(long,de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO,boolean,boolean)#p1:de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseExportImportResource#importProgrammingExerciseFromFile(long,de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO,org.springframework.web.multipart.MultipartFile,java.util.Set)#p1:de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExercisePartialUpdateResource#updateProgrammingExerciseTimeline(de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO,java.lang.String)#p0:de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseTestCaseResource#updateTestCases(java.lang.Long,java.util.Set)#p1:de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseUpdateResource#reEvaluateAndUpdateProgrammingExercise(long,de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO,java.lang.Boolean,java.util.Set)#p1:de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO",
            "de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseUpdateResource#updateProgrammingExercise(de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO,java.lang.String,java.util.Set)#p0:de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO",
            "de.tum.cit.aet.artemis.programming.web.StaticCodeAnalysisResource#updateStaticCodeAnalysisCategories(java.lang.Long,java.util.List)#p1:de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisCategoryUpdateDTO",
            "de.tum.cit.aet.artemis.programming.web.SubmissionPolicyResource#addSubmissionPolicyToProgrammingExercise(java.lang.Long,de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyDTO)#p1:de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyDTO",
            "de.tum.cit.aet.artemis.programming.web.SubmissionPolicyResource#updateSubmissionPolicy(java.lang.Long,de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyDTO)#p1:de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyDTO",
            "de.tum.cit.aet.artemis.programming.web.repository.AuxiliaryRepositoryResource#renameFile(java.lang.Long,de.tum.cit.aet.artemis.programming.dto.FileMove)#p1:de.tum.cit.aet.artemis.programming.dto.FileMove",
            "de.tum.cit.aet.artemis.programming.web.repository.AuxiliaryRepositoryResource#updateAuxiliaryFiles(java.lang.Long,java.util.List,java.lang.Boolean,java.security.Principal)#p1:de.tum.cit.aet.artemis.programming.web.repository.FileSubmission",
            "de.tum.cit.aet.artemis.programming.web.repository.RepositoryProgrammingExerciseParticipationResource#renameFile(java.lang.Long,de.tum.cit.aet.artemis.programming.dto.FileMove)#p1:de.tum.cit.aet.artemis.programming.dto.FileMove",
            "de.tum.cit.aet.artemis.programming.web.repository.RepositoryProgrammingExerciseParticipationResource#updateParticipationFiles(java.lang.Long,java.util.List,boolean)#p1:de.tum.cit.aet.artemis.programming.web.repository.FileSubmission",
            "de.tum.cit.aet.artemis.programming.web.repository.TestRepositoryResource#renameFile(java.lang.Long,de.tum.cit.aet.artemis.programming.dto.FileMove)#p1:de.tum.cit.aet.artemis.programming.dto.FileMove",
            "de.tum.cit.aet.artemis.programming.web.repository.TestRepositoryResource#updateTestFiles(java.lang.Long,java.util.List,java.lang.Boolean,java.security.Principal)#p1:de.tum.cit.aet.artemis.programming.web.repository.FileSubmission",
            "de.tum.cit.aet.artemis.programming.web.sshuserkeys.SshPublicKeysResource#addSshPublicKey(de.tum.cit.aet.artemis.programming.dto.UserSshPublicKeyDTO)#p0:de.tum.cit.aet.artemis.programming.dto.UserSshPublicKeyDTO",
            "de.tum.cit.aet.artemis.quiz.web.QuizExerciseBatchResource#joinBatch(java.lang.Long,de.tum.cit.aet.artemis.quiz.dto.QuizBatchJoinDTO)#p1:de.tum.cit.aet.artemis.quiz.dto.QuizBatchJoinDTO",
            "de.tum.cit.aet.artemis.text.web.TextAssessmentEventResource#addAssessmentEvent(de.tum.cit.aet.artemis.text.dto.TextAssessmentEventInputDTO)#p0:de.tum.cit.aet.artemis.text.dto.TextAssessmentEventInputDTO",
            "de.tum.cit.aet.artemis.text.web.TextAssessmentResource#saveTextAssessment(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO)#p2:de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO",
            "de.tum.cit.aet.artemis.text.web.TextAssessmentResource#saveTextExampleAssessment(long,long,de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO)#p2:de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO",
            "de.tum.cit.aet.artemis.text.web.TextAssessmentResource#submitTextAssessment(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO)#p2:de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO",
            "de.tum.cit.aet.artemis.text.web.TextAssessmentResource#updateTextAssessmentAfterComplaint(java.lang.Long,java.lang.Long,de.tum.cit.aet.artemis.text.dto.TextAssessmentUpdateDTO)#p2:de.tum.cit.aet.artemis.text.dto.TextAssessmentUpdateDTO",
            "de.tum.cit.aet.artemis.text.web.TextExerciseCreationUpdateResource#createTextExercise(de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO)#p0:de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO",
            "de.tum.cit.aet.artemis.text.web.TextExerciseCreationUpdateResource#reEvaluateAndUpdateTextExercise(long,de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO,java.lang.Boolean)#p1:de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO",
            "de.tum.cit.aet.artemis.text.web.TextExerciseCreationUpdateResource#updateTextExercise(de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO,java.lang.String)#p0:de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO",
            "de.tum.cit.aet.artemis.text.web.TextExerciseExportImportResource#exportSubmissions(long,de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO)#p1:de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO",
            "de.tum.cit.aet.artemis.text.web.TextExerciseExportImportResource#importExercise(long,de.tum.cit.aet.artemis.text.dto.ImportTextExerciseDTO)#p1:de.tum.cit.aet.artemis.text.dto.ImportTextExerciseDTO",
            "de.tum.cit.aet.artemis.tutorialgroup.web.TutorialGroupResource#importRegistrations(long,long,java.util.List)#p2:de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupStudentImportDataDTO");

    @Test
    void everyRequestBodyDtoParameterIsValidated() {
        List<String> violations = new ArrayList<>();

        var condition = new ArchCondition<JavaClass>("validate every Artemis DTO bound via @RequestBody/@RequestPart") {

            @Override
            public void check(JavaClass controllerClass, ConditionEvents events) {
                Class<?> reflectedController = controllerClass.reflect();
                for (JavaMethod archMethod : controllerClass.getMethods()) {
                    if (!archMethod.getOwner().equals(controllerClass)) {
                        continue;
                    }
                    Method reflectedMethod = findMatchingDeclaredMethod(reflectedController, archMethod);
                    if (reflectedMethod == null || !isRestEndpointMethod(reflectedMethod)) {
                        continue;
                    }
                    Parameter[] parameters = reflectedMethod.getParameters();
                    for (int i = 0; i < parameters.length; i++) {
                        Parameter parameter = parameters[i];
                        // Meta-annotation aware, so a (future) composed annotation that is itself @RequestBody/@RequestPart is still detected.
                        if (!AnnotatedElementUtils.hasAnnotation(parameter, RequestBody.class) && !AnnotatedElementUtils.hasAnnotation(parameter, RequestPart.class)) {
                            continue;
                        }
                        Optional<Class<?>> dto = validatableDtoType(parameter.getParameterizedType());
                        if (dto.isEmpty()) {
                            continue;
                        }
                        // Deliberately a parameter-declaration check: Spring triggers top-level body validation only on @Valid/@Validated
                        // placed on the parameter itself, not on a pure type-use @Valid, so this mirrors Spring's actual behaviour.
                        if (parameter.isAnnotationPresent(Valid.class) || parameter.isAnnotationPresent(Validated.class)) {
                            continue;
                        }
                        violations.add(violationKey(reflectedController, reflectedMethod, i, dto.get()));
                    }
                }
            }
        };

        classes().that().areAnnotatedWith(RestController.class).should(condition).allowEmptyShould(true).check(productionClasses);

        List<String> unlisted = new ArrayList<>(new TreeSet<>(violations));
        unlisted.removeAll(KNOWN_UNVALIDATED);
        List<String> stale = new ArrayList<>(new TreeSet<>(KNOWN_UNVALIDATED));
        stale.removeAll(violations);

        assertThat(unlisted)
                .as("New @RequestBody/@RequestPart DTO parameters without @Valid/@Validated. Add @Valid so field constraints "
                        + "(@Size/@Min/@Max) are enforced, or, if the body is intentionally unvalidated, add the key to " + "KNOWN_UNVALIDATED.\n%s", String.join("\n", unlisted))
                .isEmpty();
        assertThat(stale).as("KNOWN_UNVALIDATED entries that are no longer unvalidated violations; remove them so the ratchet only " + "shrinks.\n%s", String.join("\n", stale))
                .isEmpty();
    }

    /**
     * Resolves the Artemis DTO a request body is (possibly) wrapped around and that therefore needs validation,
     * or empty when the parameter carries no validatable Artemis DTO. Unwraps {@link Optional}, arrays,
     * {@link Collection} element types and {@link Map} value types. A type is a validatable Artemis DTO when it
     * lives under {@value #ARTEMIS_PACKAGE}, is a concrete class or record (not an interface, abstract
     * class, enum or annotation) and is not a JPA {@link Entity} (entities are the entity-usage tests' concern).
     * Everything else -- String, primitives/wrappers, MultipartFile, Pageable, JsonNode, framework envelopes --
     * lives outside the Artemis package and is skipped by the package check.
     */
    static Optional<Class<?>> validatableDtoType(Type type) {
        Class<?> raw = unwrapToClass(type);
        if (raw == null) {
            return Optional.empty();
        }
        boolean concrete = !raw.isInterface() && !Modifier.isAbstract(raw.getModifiers()) && !raw.isEnum() && !raw.isAnnotation() && !raw.isPrimitive() && !raw.isArray();
        if (!concrete) {
            return Optional.empty();
        }
        String packageName = raw.getPackageName();
        if (!packageName.equals(ARTEMIS_PACKAGE) && !packageName.startsWith(ARTEMIS_PACKAGE + ".")) {
            return Optional.empty();
        }
        if (raw.isAnnotationPresent(Entity.class)) {
            return Optional.empty();
        }
        return Optional.of(raw);
    }

    /**
     * Unwraps container types to the element/value class that actually travels in the body: Optional&lt;T&gt;,
     * arrays, Collection&lt;T&gt; and Map&lt;K, V&gt; (the value V) are followed to their element type; wildcards
     * to their upper bound. Returns null when no single concrete class can be determined.
     */
    private static Class<?> unwrapToClass(Type type) {
        switch (type) {
            case null -> {
                return null;
            }
            case Class<?> cls -> {
                return cls.isArray() ? unwrapToClass(cls.getComponentType()) : cls;
            }
            case ParameterizedType parameterizedType -> {
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class<?> rawClass) {
                    Type[] arguments = parameterizedType.getActualTypeArguments();
                    if (Optional.class.isAssignableFrom(rawClass) || Collection.class.isAssignableFrom(rawClass)) {
                        return arguments.length == 1 ? unwrapToClass(arguments[0]) : null;
                    }
                    if (Map.class.isAssignableFrom(rawClass)) {
                        return arguments.length == 2 ? unwrapToClass(arguments[1]) : null;
                    }
                    return rawClass;
                }
                return null;
            }
            case GenericArrayType genericArrayType -> {
                return unwrapToClass(genericArrayType.getGenericComponentType());
            }
            case WildcardType wildcardType -> {
                for (Type upper : wildcardType.getUpperBounds()) {
                    Class<?> found = unwrapToClass(upper);
                    if (found != null && found != Object.class) {
                        return found;
                    }
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static String violationKey(Class<?> controller, Method method, int parameterIndex, Class<?> dto) {
        List<String> rawParameterTypes = new ArrayList<>();
        for (Class<?> parameterType : method.getParameterTypes()) {
            rawParameterTypes.add(parameterType.getTypeName());
        }
        return "%s#%s(%s)#p%d:%s".formatted(controller.getName(), method.getName(), String.join(",", rawParameterTypes), parameterIndex, dto.getName());
    }

    /**
     * Whether the given controller method is an actual HTTP endpoint, i.e. it is (meta-)annotated with
     * {@link RequestMapping} (covers {@code @GetMapping}/{@code @PostMapping}/... and custom mappings).
     */
    private static boolean isRestEndpointMethod(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    /**
     * Matches ArchUnit's {@link JavaMethod} to the declared reflective {@link Method} by name and raw parameter types.
     */
    private static Method findMatchingDeclaredMethod(Class<?> owner, JavaMethod archMethod) {
        String name = archMethod.getName();
        Class<?>[] rawParameterTypes = archMethod.getRawParameterTypes().stream().map(JavaClass::reflect).toArray(Class<?>[]::new);
        try {
            return owner.getDeclaredMethod(name, rawParameterTypes);
        }
        catch (NoSuchMethodException exception) {
            for (Method method : owner.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == rawParameterTypes.length) {
                    if (List.of(method.getParameterTypes()).equals(List.of(rawParameterTypes))) {
                        return method;
                    }
                }
            }
            return null;
        }
    }
}
