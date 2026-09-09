import { Competency, CompetencyTaxonomy, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { Course, Language } from 'app/course/shared/entities/course.model';
import { CourseManagementDTO, CoursePrerequisiteDTO, courseFromManagementDTO } from 'app/course/shared/entities/course-management-response.dto';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { UMLDiagramType } from '@tumaet/apollon';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
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
    id: number;
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
    exercises?: CourseManagementExerciseDTO[];
    lectures?: LectureForCourseManagementDTO[];
    competencies?: CourseCompetencyDashboardDTO[];
    prerequisites?: CoursePrerequisiteDTO[];
}

export function exerciseFromCourseManagementDTO(dto: CourseManagementExerciseDTO): Exercise {
    const exercise = createExercise(dto.type);
    return hydrate(exercise, dto);
}

export function courseFromWithExercisesDTO(dto: CourseWithExercisesDTO): Course {
    const course = courseFromManagementDTO(dto);
    course.exercises = (dto.exercises ?? []).map(exerciseFromCourseManagementDTO);
    return course;
}

export function courseFromWithContentDTO(dto: CourseWithContentDTO): Course {
    const course = courseFromWithExercisesDTO(dto);
    course.lectures = (dto.lectures ?? []).map((lectureDTO) => hydrate(new Lecture(), lectureDTO));
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
    const course = hydrate(new Course(), dto);
    course.exercises = (dto.exercises ?? []).map(exerciseFromCourseManagementDTO);
    course.lectures = (dto.lectures ?? []).map((lectureDTO) => hydrate(new Lecture(), lectureDTO));
    course.competencies = (dto.competencies ?? []).map(competencyFromDTO);
    course.prerequisites = (dto.prerequisites ?? []).map(prerequisiteFromDTO);
    return course;
}

function competencyFromDTO(dto: CourseCompetencyDashboardDTO): Competency {
    return hydrate(new Competency(), dto, { softDueDate: convertDateStringFromServer(dto.softDueDate) });
}

function prerequisiteFromDTO(dto: CoursePrerequisiteDTO): Prerequisite {
    return hydrate(new Prerequisite(), dto, { softDueDate: convertDateStringFromServer(dto.softDueDate) });
}

function createExercise(type: ExerciseType): Exercise {
    switch (type) {
        case ExerciseType.PROGRAMMING:
            return new ProgrammingExercise(undefined, undefined);
        case ExerciseType.MODELING:
            return new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        case ExerciseType.QUIZ:
            return new QuizExercise(undefined, undefined);
        case ExerciseType.TEXT:
            return new TextExercise(undefined, undefined);
        case ExerciseType.FILE_UPLOAD:
            return new FileUploadExercise(undefined, undefined);
    }
}
