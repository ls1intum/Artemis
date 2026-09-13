import type { Competency, CompetencyTaxonomy, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import type { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { Course } from 'app/course/shared/entities/course.model';
import type { CourseInformationSharingConfiguration, Language } from 'app/course/shared/entities/course.model';
import { courseFromManagementDTO } from 'app/course/shared/entities/course-management-response.dto';
import type { CourseManagementDTO, CoursePrerequisiteDTO } from 'app/course/shared/entities/course-management-response.dto';
import type { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import type { Exam } from 'app/exam/shared/entities/exam.model';
import type { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import type { TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import type { Lecture } from 'app/lecture/shared/entities/lecture.model';
import type { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';

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

export interface CourseManagementExerciseDTO {
    id: number;
    type: ExerciseType;
    title: string;
    shortName?: string;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;
    maxPoints?: number;
    bonusPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;
    categories?: string[];
    teamMode?: boolean;
}

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

export interface AssessmentDashboardExerciseDTO extends CourseManagementExerciseDTO {
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
    exercises?: CourseManagementExerciseDTO[];
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
    return hydrate({} as Exercise, dto);
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
    course.exercises = (dto.exercises ?? []).map(exerciseFromCourseManagementDTO);
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
    course.exercises = (exercises ?? []).map(exerciseFromCourseManagementDTO);
    course.lectures = (lectures ?? []).map(lectureFromCourseManagementDTO);
    course.competencies = (competencies ?? []).map(competencyFromDTO);
    course.prerequisites = (prerequisites ?? []).map(prerequisiteFromDTO);
    course.exams = (exams ?? []).map((examDTO) =>
        hydrate({} as Exam, examDTO, {
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

function competencyFromDTO(dto: CourseCompetencyDashboardDTO): Competency {
    return hydrate({} as Competency, dto, { softDueDate: convertDateStringFromServer(dto.softDueDate) });
}

function lectureFromCourseManagementDTO(dto: LectureForCourseManagementDTO): Lecture {
    return hydrate({} as Lecture, dto, {
        startDate: convertDateStringFromServer(dto.startDate),
        endDate: convertDateStringFromServer(dto.endDate),
    });
}

function prerequisiteFromDTO(dto: CoursePrerequisiteDTO): Prerequisite {
    return hydrate({} as Prerequisite, dto, { softDueDate: convertDateStringFromServer(dto.softDueDate) });
}
