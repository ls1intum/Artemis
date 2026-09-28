import { Competency } from 'app/atlas/shared/entities/competency.model';
import type { CompetencyTaxonomy, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { Course } from 'app/course/shared/entities/course.model';
import type { CourseInformationSharingConfiguration, Language } from 'app/course/shared/entities/course.model';
import { courseFromManagementDTO } from 'app/course/shared/entities/course-management-response.dto';
import type { CourseManagementDTO, CoursePrerequisiteDTO } from 'app/course/shared/entities/course-management-response.dto';
import type { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import type { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import type { DifficultyLevel, Exercise, ExerciseMode, ExerciseVariantGroupReference, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import type { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { createSubmission } from 'app/exercise/shared/entities/participation/student-participation.dto';
import type { TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import type { Submission, SubmissionExerciseType, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import type { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import type { QuizBatch, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import type { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import type { UMLDiagramType } from '@tumaet/apollon';

export interface CourseCompetencyDashboardDTO {
    id: number;
    title: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
    softDueDate?: string;
    masteryThreshold: number;
    optional: boolean;
    type: CourseCompetencyType;
}

/** The variant group as every exercise payload embeds it: identity, cap and the shared timeline, no member list. */
interface ExerciseVariantGroupReferenceDTO {
    id: number;
    title?: string;
    maxPoints?: number;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;
}

/** The fields all five course-management exercise payloads share. */
interface CourseManagementExerciseCommonDTO {
    id: number;
    title: string;
    shortName?: string;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    maxPoints?: number;
    bonusPoints?: number;
    difficulty?: DifficultyLevel;
    mode?: ExerciseMode;
    includedInOverallScore?: IncludedInOverallScore;
    exerciseVariantGroup?: ExerciseVariantGroupReferenceDTO;
}

/** The assessment configuration the four assessed exercise kinds carry and the quiz payload does not. */
interface AssessedCourseManagementExerciseDTO extends CourseManagementExerciseCommonDTO {
    channelName?: string;
    problemStatement?: string;
    gradingInstructions?: string;
    categories?: string[];
    teamMode?: boolean;
    assessmentType?: AssessmentType;
    exampleSolutionPublicationDate?: string;
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled?: boolean;
    allowComplaintsForAutomaticAssessments?: boolean;
}

interface ProgrammingCourseManagementExerciseDTO extends AssessedCourseManagementExerciseDTO {
    type: ExerciseType.PROGRAMMING;
    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    projectKey?: string;
    allowOnlineEditor?: boolean;
    allowOfflineIde?: boolean;
    allowOnlineIde?: boolean;
    staticCodeAnalysisEnabled?: boolean;
    maxStaticCodeAnalysisPenalty?: number;
    showTestNamesToStudents?: boolean;
    buildAndTestStudentSubmissionsAfterDueDate?: string;
}

interface TextCourseManagementExerciseDTO extends AssessedCourseManagementExerciseDTO {
    type: ExerciseType.TEXT;
    exampleSolution?: string;
    allowFeedbackRequests?: boolean;
}

interface ModelingCourseManagementExerciseDTO extends AssessedCourseManagementExerciseDTO {
    type: ExerciseType.MODELING;
    diagramType: UMLDiagramType;
    exampleSolutionModel?: string;
    exampleSolutionExplanation?: string;
}

interface FileUploadCourseManagementExerciseDTO extends AssessedCourseManagementExerciseDTO {
    type: ExerciseType.FILE_UPLOAD;
    exampleSolution?: string;
    filePattern?: string;
    allowFeedbackRequests?: boolean;
}

interface QuizBatchDTO {
    id?: number;
    startTime?: string;
    started?: boolean;
    ended?: boolean;
}

interface QuizCourseManagementExerciseDTO extends CourseManagementExerciseCommonDTO {
    type: ExerciseType.QUIZ;
    quizMode?: QuizMode;
    quizBatches?: QuizBatchDTO[];
    quizStarted?: boolean;
    quizEnded?: boolean;
    visibleToStudents?: boolean;
    duration?: number;
    randomizeQuestionOrder?: boolean;
    allowedNumberOfAttempts?: number;
    remainingNumberOfAttempts?: number;
}

/** The polymorphic exercise payload of the course-management content endpoints, discriminated by `type`. */
export type CourseManagementExerciseDTO =
    | ProgrammingCourseManagementExerciseDTO
    | TextCourseManagementExerciseDTO
    | ModelingCourseManagementExerciseDTO
    | FileUploadCourseManagementExerciseDTO
    | QuizCourseManagementExerciseDTO;

export interface LectureForCourseManagementDTO {
    id: number;
    title: string;
    description?: string;
    startDate?: string;
    endDate?: string;
    isTutorialLecture: boolean;
}

export interface CourseWithExercisesDTO extends CourseManagementDTO {
    exercises?: CourseManagementExerciseDTO[];
}

export interface CourseWithContentDTO extends CourseWithExercisesDTO {
    lectures?: LectureForCourseManagementDTO[];
    competencies?: CourseCompetencyDashboardDTO[];
    prerequisites?: CoursePrerequisiteDTO[];
}

export interface CourseAssessmentDashboardDTO extends CourseManagementDTO {
    exercises?: AssessmentDashboardExerciseDTO[];
}

/** The derived assessment statistics the tutor dashboard renders; it receives no exercise configuration beyond these. */
export interface AssessmentDashboardExerciseDTO {
    id: number;
    type: ExerciseType;
    title: string;
    dueDate?: string;
    assessmentDueDate?: string;
    includedInOverallScore?: IncludedInOverallScore;
    teamMode?: boolean;
    numberOfSubmissions?: DueDateStat;
    totalNumberOfAssessments?: DueDateStat;
    numberOfAssessmentsOfCorrectionRounds: DueDateStat[];
    numberOfComplaints?: number;
    numberOfOpenComplaints?: number;
    numberOfMoreFeedbackRequests?: number;
    numberOfOpenMoreFeedbackRequests?: number;
    averageRating?: number;
    numberOfRatings?: number;
    secondCorrectionEnabled: boolean;
    allowComplaintsForAutomaticAssessments: boolean;
    tutorParticipations: CourseTutorParticipationDTO[];
}

export interface CourseTutorParticipationDTO {
    id?: number;
    tutorId?: number;
    status: TutorParticipationStatus;
}

export interface CourseExerciseDueDateDTO {
    id: number;
    type: ExerciseType;
    title: string;
    dueDate: string;
    categories?: string[];
}

interface CourseDashboardResultDTO {
    id: number;
    completionDate?: string;
    score?: number;
    rated?: boolean;
    successful?: boolean;
    assessmentType?: AssessmentType;
    codeIssueCount?: number;
    testCaseCount?: number;
    passedTestCaseCount?: number;
}

interface CourseDashboardSubmissionDTO {
    id: number;
    submissionDate?: string;
    submitted?: boolean;
    type?: SubmissionType;
    submissionExerciseType: SubmissionExerciseType;
    buildFailed?: boolean;
    commitHash?: string;
    results?: CourseDashboardResultDTO[];
}

interface CourseDashboardParticipationDTO {
    id: number;
    type?: ParticipationType;
    initializationState?: InitializationState;
    initializationDate?: string;
    testRun?: boolean;
    individualDueDate?: string;
    repositoryUri?: string;
    submissions?: CourseDashboardSubmissionDTO[];
}

/** The student-visible exercise graph of the dashboard endpoints, with the requesting user's participations. */
export interface CourseDashboardExerciseResponseDTO {
    id: number;
    type: ExerciseType;
    title?: string;
    maxPoints?: number;
    bonusPoints?: number;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    assessmentType?: AssessmentType;
    difficulty?: DifficultyLevel;
    mode?: ExerciseMode;
    teamMode?: boolean;
    includedInOverallScore?: IncludedInOverallScore;
    categories?: string[];
    presentationScoreEnabled?: boolean;
    allowFeedbackRequests?: boolean;
    allowOnlineEditor?: boolean;
    allowOfflineIde?: boolean;
    allowOnlineIde?: boolean;
    staticCodeAnalysisEnabled?: boolean;
    maxStaticCodeAnalysisPenalty?: number;
    showTestNamesToStudents?: boolean;
    programmingLanguage?: ProgrammingLanguage;
    buildAndTestStudentSubmissionsAfterDueDate?: string;
    quizEnded?: boolean;
    quizBatches?: { started: boolean }[];
    studentAssignedTeamId?: number;
    studentAssignedTeamIdComputed?: boolean;
    exerciseVariantGroup?: ExerciseVariantGroupReferenceDTO;
    studentParticipations?: CourseDashboardParticipationDTO[];
    problemStatement?: string;
    gradingInstructions?: string;
    exampleSolution?: string;
    exampleSolutionModel?: string;
    exampleSolutionExplanation?: string;
    exampleSolutionPublicationDate?: string;
    quizQuestions?: QuizQuestion[];
}

export interface CourseDashboardDTO {
    id: number;
    title: string;
    shortName: string;
    description?: string;
    semester?: string;
    startDate?: string;
    endDate?: string;
    testCourse: boolean;
    language?: Language;
    defaultProgrammingLanguage?: ProgrammingLanguage;
    timeZone?: string;
    color?: string;
    courseIcon?: string;
    enrollmentEnabled?: boolean;
    unenrollmentEnabled: boolean;
    enrollmentStartDate?: string;
    enrollmentEndDate?: string;
    unenrollmentEndDate?: string;
    enrollmentConfirmationMessage?: string;
    onlineCourse: boolean;
    courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
    courseInformationSharingMessagingCodeOfConduct?: string;
    maxComplaints?: number;
    maxTeamComplaints?: number;
    maxComplaintTimeDays: number;
    maxRequestMoreFeedbackTimeDays: number;
    maxComplaintTextLimit: number;
    maxComplaintResponseTextLimit: number;
    presentationScore?: number;
    maxPoints?: number;
    accuracyOfScores?: number;
    complaintsEnabled: boolean;
    requestMoreFeedbackEnabled: boolean;
    athenaGradingFeedbackEnabled: boolean;
    athenaFormativeFeedbackEnabled: boolean;
    learningPathsEnabled: boolean;
    trainingEnabled: boolean;
    exercises?: CourseDashboardExerciseResponseDTO[];
    lectures?: LectureForCourseManagementDTO[];
    competencies?: CourseCompetencyDashboardDTO[];
    prerequisites?: CoursePrerequisiteDTO[];
    exams?: CourseDashboardExamDTO[];
}

export interface CourseDashboardExamDTO {
    id: number;
    title: string;
    testExam?: boolean;
    examWithAttendanceCheck?: boolean;
    visibleDate?: string;
    startDate?: string;
    endDate?: string;
    publishResultsDate?: string;
    examStudentReviewStart?: string;
    examStudentReviewEnd?: string;
    gracePeriod?: number;
    workingTime?: number;
    startText?: string;
    endText?: string;
    confirmationStartText?: string;
    confirmationEndText?: string;
    examMaxPoints?: number;
    randomizeExerciseOrder?: boolean;
    numberOfExercisesInExam?: number;
    numberOfCorrectionRoundsInExam?: number;
    channelName?: string;
    exampleSolutionPublicationDate?: string;
    examSummaryPublicationDate?: string;
}

export function exerciseFromCourseManagementDTO(dto: CourseManagementExerciseDTO): Exercise {
    switch (dto.type) {
        case ExerciseType.PROGRAMMING:
            return withParsedCategories(
                hydrate(new ProgrammingExercise(undefined, undefined), dto, sharedDateOverrides(dto), {
                    exampleSolutionPublicationDate: convertDateStringFromServer(dto.exampleSolutionPublicationDate),
                    buildAndTestStudentSubmissionsAfterDueDate: convertDateStringFromServer(dto.buildAndTestStudentSubmissionsAfterDueDate),
                }),
            );
        case ExerciseType.TEXT:
            return withParsedCategories(
                hydrate(new TextExercise(undefined, undefined), dto, sharedDateOverrides(dto), {
                    exampleSolutionPublicationDate: convertDateStringFromServer(dto.exampleSolutionPublicationDate),
                }),
            );
        case ExerciseType.MODELING:
            return withParsedCategories(
                hydrate(new ModelingExercise(dto.diagramType, undefined, undefined), dto, sharedDateOverrides(dto), {
                    exampleSolutionPublicationDate: convertDateStringFromServer(dto.exampleSolutionPublicationDate),
                }),
            );
        case ExerciseType.FILE_UPLOAD:
            return withParsedCategories(
                hydrate(new FileUploadExercise(undefined, undefined), dto, sharedDateOverrides(dto), {
                    exampleSolutionPublicationDate: convertDateStringFromServer(dto.exampleSolutionPublicationDate),
                }),
            );
        case ExerciseType.QUIZ:
            return hydrate(new QuizExercise(undefined, undefined), dto, sharedDateOverrides(dto), {
                quizBatches: (dto.quizBatches ?? []).map(quizBatchFromDTO),
            });
    }
}

export function exerciseFromCourseDashboardDTO(dto: CourseDashboardExerciseResponseDTO): Exercise {
    const exercise: Exercise = hydrate(newExerciseModel(dto.type), dto, sharedDateOverrides(dto), {
        exampleSolutionPublicationDate: convertDateStringFromServer(dto.exampleSolutionPublicationDate),
        buildAndTestStudentSubmissionsAfterDueDate: convertDateStringFromServer(dto.buildAndTestStudentSubmissionsAfterDueDate),
    });
    exercise.studentParticipations = (dto.studentParticipations ?? []).map(participationFromCourseDashboardDTO);
    return withParsedCategories(exercise);
}

export function exerciseFromCourseExerciseDueDateDTO(dto: CourseExerciseDueDateDTO): Exercise {
    return withParsedCategories(hydrate(newExerciseModel(dto.type), dto, { dueDate: convertDateStringFromServer(dto.dueDate) }));
}

function exerciseFromAssessmentDashboardDTO(dto: AssessmentDashboardExerciseDTO): Exercise {
    return hydrate(newExerciseModel(dto.type), dto, {
        dueDate: convertDateStringFromServer(dto.dueDate),
        assessmentDueDate: convertDateStringFromServer(dto.assessmentDueDate),
    });
}

export function courseFromWithExercisesDTO(dto: CourseWithExercisesDTO): Course {
    const course = courseFromManagementDTO(dto);
    course.exercises = (dto.exercises ?? []).map(exerciseFromCourseManagementDTO);
    return course;
}

export function courseFromWithContentDTO(dto: CourseWithContentDTO): Course {
    const course = courseFromWithExercisesDTO(dto);
    course.lectures = (dto.lectures ?? []).map(lectureFromCourseManagementDTO);
    course.competencies = (dto.competencies ?? []).map(competencyFromDTO);
    course.prerequisites = (dto.prerequisites ?? []).map(prerequisiteFromDTO);
    return course;
}

export function courseFromAssessmentDashboardDTO(dto: CourseAssessmentDashboardDTO): Course {
    const course = courseFromManagementDTO(dto);
    course.exercises = (dto.exercises ?? []).map(exerciseFromAssessmentDashboardDTO);
    return course;
}

export function courseFromDashboardDTO(dto: CourseDashboardDTO): Course {
    const { exercises, lectures, competencies, prerequisites, exams, ...courseProps } = dto;
    const course: Course = hydrate(new Course(), courseProps, {
        startDate: convertDateStringFromServer(dto.startDate),
        endDate: convertDateStringFromServer(dto.endDate),
        enrollmentStartDate: convertDateStringFromServer(dto.enrollmentStartDate),
        enrollmentEndDate: convertDateStringFromServer(dto.enrollmentEndDate),
        unenrollmentEndDate: convertDateStringFromServer(dto.unenrollmentEndDate),
    });
    course.exercises = (exercises ?? []).map(exerciseFromCourseDashboardDTO);
    course.lectures = (lectures ?? []).map(lectureFromCourseManagementDTO);
    course.competencies = (competencies ?? []).map(competencyFromDTO);
    course.prerequisites = (prerequisites ?? []).map(prerequisiteFromDTO);
    course.exams = (exams ?? []).map((examDTO) =>
        hydrate(new Exam(), examDTO, {
            startDate: convertDateStringFromServer(examDTO.startDate),
            endDate: convertDateStringFromServer(examDTO.endDate),
            visibleDate: convertDateStringFromServer(examDTO.visibleDate),
            publishResultsDate: convertDateStringFromServer(examDTO.publishResultsDate),
            examStudentReviewStart: convertDateStringFromServer(examDTO.examStudentReviewStart),
            examStudentReviewEnd: convertDateStringFromServer(examDTO.examStudentReviewEnd),
            exampleSolutionPublicationDate: convertDateStringFromServer(examDTO.exampleSolutionPublicationDate),
            examSummaryPublicationDate: convertDateStringFromServer(examDTO.examSummaryPublicationDate),
        }),
    );
    return course;
}

/**
 * The exercise model matching the payload's type discriminator, so `instanceof` checks and the typed readers of the
 * concrete models work on a converted list.
 */
function newExerciseModel(type: ExerciseType, diagramType?: UMLDiagramType): Exercise {
    switch (type) {
        case ExerciseType.PROGRAMMING:
            return new ProgrammingExercise(undefined, undefined);
        case ExerciseType.TEXT:
            return new TextExercise(undefined, undefined);
        case ExerciseType.MODELING:
            // The constructor takes the diagram type positionally, and the projections that have none pass undefined.
            return new ModelingExercise(diagramType as UMLDiagramType, undefined, undefined);
        case ExerciseType.FILE_UPLOAD:
            return new FileUploadExercise(undefined, undefined);
        case ExerciseType.QUIZ:
            return new QuizExercise(undefined, undefined);
    }
}

function sharedDateOverrides(dto: {
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exerciseVariantGroup?: ExerciseVariantGroupReferenceDTO;
}) {
    return {
        releaseDate: convertDateStringFromServer(dto.releaseDate),
        startDate: convertDateStringFromServer(dto.startDate),
        dueDate: convertDateStringFromServer(dto.dueDate),
        assessmentDueDate: convertDateStringFromServer(dto.assessmentDueDate),
        exerciseVariantGroup: variantGroupFromDTO(dto.exerciseVariantGroup),
    };
}

function withParsedCategories(exercise: Exercise): Exercise {
    ExerciseService.parseExerciseCategories(exercise);
    return exercise;
}

function variantGroupFromDTO(dto?: ExerciseVariantGroupReferenceDTO): ExerciseVariantGroupReference | undefined {
    if (!dto) {
        return undefined;
    }
    return {
        id: dto.id,
        title: dto.title,
        maxPoints: dto.maxPoints,
        releaseDate: convertDateStringFromServer(dto.releaseDate),
        startDate: convertDateStringFromServer(dto.startDate),
        dueDate: convertDateStringFromServer(dto.dueDate),
        assessmentDueDate: convertDateStringFromServer(dto.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateStringFromServer(dto.exampleSolutionPublicationDate),
    };
}

function quizBatchFromDTO(dto: QuizBatchDTO): QuizBatch {
    return {
        id: dto.id,
        startTime: convertDateStringFromServer(dto.startTime),
        started: dto.started,
        ended: dto.ended,
    };
}

function participationFromCourseDashboardDTO(dto: CourseDashboardParticipationDTO): StudentParticipation {
    const participation: StudentParticipation =
        dto.type === ParticipationType.PROGRAMMING ? new ProgrammingExerciseStudentParticipation() : new StudentParticipation(ParticipationType.STUDENT);
    hydrate(participation, dto, {
        initializationDate: convertDateStringFromServer(dto.initializationDate),
        individualDueDate: convertDateStringFromServer(dto.individualDueDate),
        submissions: undefined,
    });
    participation.submissions = (dto.submissions ?? []).map((submissionDTO) => submissionFromCourseDashboardDTO(submissionDTO, participation));
    return participation;
}

function submissionFromCourseDashboardDTO(dto: CourseDashboardSubmissionDTO, participation: StudentParticipation): Submission {
    const submission = createSubmission(dto.submissionExerciseType);
    hydrate(submission, dto, { submissionDate: convertDateStringFromServer(dto.submissionDate), results: undefined });
    submission.participation = participation;
    submission.results = (dto.results ?? []).map((resultDTO) => resultFromCourseDashboardDTO(resultDTO, submission));
    return submission;
}

function resultFromCourseDashboardDTO(dto: CourseDashboardResultDTO, submission: Submission): Result {
    const result = hydrate(new Result(), dto, { completionDate: convertDateStringFromServer(dto.completionDate) });
    result.submission = submission;
    return result;
}

function competencyFromDTO(dto: CourseCompetencyDashboardDTO): Competency {
    return hydrate(new Competency(), dto, { softDueDate: convertDateStringFromServer(dto.softDueDate) });
}

function lectureFromCourseManagementDTO(dto: LectureForCourseManagementDTO): Lecture {
    return hydrate(new Lecture(), dto, {
        startDate: convertDateStringFromServer(dto.startDate),
        endDate: convertDateStringFromServer(dto.endDate),
    });
}

function prerequisiteFromDTO(dto: CoursePrerequisiteDTO): Prerequisite {
    return hydrate(new Prerequisite(), dto, { softDueDate: convertDateStringFromServer(dto.softDueDate) });
}
