import { User } from 'app/account/user/user.model';
import { CompetencyTaxonomy, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { Organization } from 'app/admin/organization-management/organization.model';
import { Course, CourseInformationSharingConfiguration, Language } from 'app/course/shared/entities/course.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

export interface CoursePrerequisiteDTO {
    id: number;
    title: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
    softDueDate?: string;
    masteryThreshold: number;
    optional: boolean;
    type: CourseCompetencyType;
}

export interface CourseForEnrollmentDTO {
    id: number;
    title: string;
    description?: string;
    semester?: string;
    enrollmentConfirmationMessage?: string;
    prerequisites?: CoursePrerequisiteDTO[];
}

export interface CourseMemberDTO {
    id: number;
    login?: string;
    name?: string;
    firstName?: string;
    lastName?: string;
    email?: string;
    visibleRegistrationNumber?: string;
    imageUrl?: string;
    activated: boolean;
    internal: boolean;
}

export interface CourseConfigurationResponseDTO {
    id: number;
    gradeRelevant: boolean;
    dataRetentionHold: boolean;
    autoOrchestratorEnabled: boolean;
    debounceWindowSecondsOverride?: number;
    maxDailyOrchestrationOverride?: number;
}

export interface LtiPlatformConfigurationResponseDTO {
    id: number;
    registrationId: string;
    clientId: string;
    authorizationUri: string;
    jwkSetUri: string;
    tokenUri: string;
    originalUrl?: string;
    customName?: string;
}

export interface OnlineCourseConfigurationResponseDTO {
    id?: number;
    userPrefix: string;
    requireExistingUser: boolean;
    ltiPlatformConfiguration?: LtiPlatformConfigurationResponseDTO;
}

export interface TutorialGroupsConfigurationResponseDTO {
    id: number;
    tutorialPeriodStartInclusive: string;
    tutorialPeriodEndInclusive: string;
    useTutorialGroupChannels: boolean;
    usePublicTutorialGroupChannels: boolean;
}

export interface CourseManagementDTO {
    id: number;
    title: string;
    shortName: string;
    description?: string;
    semester?: string;
    startDate?: string;
    endDate?: string;
    enrollmentStartDate?: string;
    enrollmentEndDate?: string;
    unenrollmentEndDate?: string;
    testCourse: boolean;
    language?: Language;
    defaultProgrammingLanguage?: ProgrammingLanguage;
    timeZone?: string;
    color?: string;
    courseIcon?: string;
    enrollmentEnabled?: boolean;
    unenrollmentEnabled: boolean;
    enrollmentConfirmationMessage?: string;
    onboardingDone: boolean;
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
    numberOfStudents?: number;
    numberOfTeachingAssistants?: number;
    numberOfEditors?: number;
    numberOfInstructors?: number;
    onlineCourseConfiguration?: OnlineCourseConfigurationResponseDTO;
    tutorialGroupsConfiguration?: TutorialGroupsConfigurationResponseDTO;
    courseConfiguration?: CourseConfigurationResponseDTO;
}

export interface CourseManagementOverviewDTO {
    id: number;
    title: string;
    shortName: string;
    description?: string;
    startDate?: string;
    endDate?: string;
    semester?: string;
    testCourse: boolean;
    color?: string;
    courseIcon?: string;
    timeZone?: string;
    courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
}

export interface CourseQuizExerciseReferenceDTO {
    id: number;
    title: string;
    shortName?: string;
}

export interface CourseForQuizSelectionDTO {
    id: number;
    title: string;
    exercises?: CourseQuizExerciseReferenceDTO[];
}

export interface OrganizationDTO {
    id: number;
    name: string;
    shortName: string;
    url?: string;
    description?: string;
    emailPattern: string;
    logoUrl?: string;
    numberOfUsers?: number;
    numberOfCourses?: number;
}

export interface CourseWithOrganizationsDTO {
    course: CourseManagementDTO;
    organizations?: OrganizationDTO[];
}

export interface CourseWithIdDTO {
    id: number;
}

export function courseFromManagementDTO(dto: CourseManagementDTO): Course {
    const course: Course = hydrate(new Course(), dto);
    course.startDate = convertDateStringFromServer(dto.startDate);
    course.endDate = convertDateStringFromServer(dto.endDate);
    course.enrollmentStartDate = convertDateStringFromServer(dto.enrollmentStartDate);
    course.enrollmentEndDate = convertDateStringFromServer(dto.enrollmentEndDate);
    course.unenrollmentEndDate = convertDateStringFromServer(dto.unenrollmentEndDate);
    return course;
}

export function courseFromEnrollmentDTO(dto: CourseForEnrollmentDTO): Course {
    const course: Course = hydrate(new Course(), dto);
    course.prerequisites = (dto.prerequisites ?? []).map((prerequisiteDTO) => {
        const prerequisite: Prerequisite = hydrate(new Prerequisite(), prerequisiteDTO);
        prerequisite.softDueDate = convertDateStringFromServer(prerequisiteDTO.softDueDate);
        return prerequisite;
    });
    return course;
}

export function courseFromQuizSelectionDTO(dto: CourseForQuizSelectionDTO): Course {
    const course: Course = hydrate(new Course(), dto);
    course.exercises = (dto.exercises ?? []).map((exerciseDTO) => hydrate(new QuizExercise(undefined, undefined), exerciseDTO));
    return course;
}

export function courseFromManagementOverviewDTO(dto: CourseManagementOverviewDTO): Course {
    const course: Course = hydrate(new Course(), dto);
    course.startDate = convertDateStringFromServer(dto.startDate);
    course.endDate = convertDateStringFromServer(dto.endDate);
    return course;
}

export function courseFromOrganizationsDTO(dto: CourseWithOrganizationsDTO): Course {
    const course = courseFromManagementDTO(dto.course);
    course.organizations = (dto.organizations ?? []).map((organizationDTO) => hydrate(new Organization(), organizationDTO));
    return course;
}

export function courseMemberFromDTO(dto: CourseMemberDTO): User {
    return hydrate(new User(), dto);
}

export function courseWithIdFromDTO(dto: CourseWithIdDTO): Course {
    return hydrate(new Course(), dto);
}
