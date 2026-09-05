import type { User, UserPublicInfoDTO } from 'app/account/user/user.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import type { Course, Language } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';

export interface ParticipationSubmissionResultDTO {
    id: number;
    completionDate?: string;
    successful?: boolean;
    score?: number;
    rated: boolean;
    assessmentType?: AssessmentType;
    testCaseCount?: number;
    passedTestCaseCount?: number;
    codeIssueCount?: number;
}

export interface ParticipationSubmissionDTO {
    id: number;
    submitted?: boolean;
    submissionDate?: string;
    submissionExerciseType: SubmissionExerciseType;
    commitHash?: string;
    text?: string;
    language?: Language;
    model?: string;
    explanationText?: string;
    filePath?: string;
    results?: ParticipationSubmissionResultDTO[];
}

export interface ParticipationTeamDTO {
    id: number;
    name?: string;
    shortName?: string;
    image?: string;
    students?: UserPublicInfoDTO[];
}

export interface ParticipationCourseContextDTO {
    id: number;
    title?: string;
    shortName?: string;
    accuracyOfScores?: number;
}

export interface ParticipationExerciseContextDTO {
    id: number;
    title?: string;
    type: ExerciseType;
    exerciseType: ExerciseType;
    assessmentType?: AssessmentType;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    maxPoints?: number;
    course?: ParticipationCourseContextDTO;
    exerciseGroup?: {
        id: number;
        exam: {
            id: number;
        };
    };
}

export interface StudentParticipationDTO {
    id: number;
    initializationState?: InitializationState;
    initializationDate?: string;
    individualDueDate?: string;
    presentationScore?: number;
    testRun: boolean;
    type: ParticipationType.STUDENT | ParticipationType.PROGRAMMING;
    submissionCount?: number;
    participantName?: string;
    participantIdentifier?: string;
    student?: UserPublicInfoDTO;
    team?: ParticipationTeamDTO;
    exercise?: ParticipationExerciseContextDTO;
    submissions?: ParticipationSubmissionDTO[];
    repositoryUri?: string;
    buildPlanId?: string;
    branch?: string;
}

class ParticipationExerciseContext extends Exercise {
    constructor(type: ExerciseType) {
        super(type);
    }
}

/**
 * Converts a participation wire DTO into the existing component-facing model and reconnects its nested relationships.
 */
export function fromStudentParticipationDTO(dto: StudentParticipationDTO): StudentParticipation {
    const participation = dto.type === ParticipationType.PROGRAMMING ? new ProgrammingExerciseStudentParticipation() : new StudentParticipation(ParticipationType.STUDENT);
    participation.id = dto.id;
    participation.initializationState = dto.initializationState;
    participation.initializationDate = convertDateStringFromServer(dto.initializationDate);
    participation.individualDueDate = convertDateStringFromServer(dto.individualDueDate);
    participation.presentationScore = dto.presentationScore;
    participation.testRun = dto.testRun;
    participation.submissionCount = dto.submissionCount;
    participation.participantName = dto.participantName;
    participation.participantIdentifier = dto.participantIdentifier;
    participation.student = dto.student ? (deepClone(dto.student) as User) : undefined;
    participation.team = dto.team ? fromParticipationTeamDTO(dto.team) : undefined;
    participation.exercise = dto.exercise ? fromParticipationExerciseContextDTO(dto.exercise) : undefined;

    if (participation instanceof ProgrammingExerciseStudentParticipation) {
        participation.repositoryUri = dto.repositoryUri;
        participation.buildPlanId = dto.buildPlanId;
        participation.branch = dto.branch;
    }

    participation.submissions = dto.submissions?.map(fromParticipationSubmissionDTO);
    participation.submissions?.forEach((submission) => {
        submission.participation = participation;
        submission.results?.forEach((result) => (result.submission = submission));
    });
    return participation;
}

function fromParticipationTeamDTO(dto: ParticipationTeamDTO): Team {
    const team = new Team();
    team.id = dto.id;
    team.name = dto.name;
    team.shortName = dto.shortName;
    team.image = dto.image;
    team.students = dto.students?.map((student) => deepClone(student) as User);
    return team;
}

function fromParticipationExerciseContextDTO(dto: ParticipationExerciseContextDTO): Exercise {
    const exercise = new ParticipationExerciseContext(dto.exerciseType);
    exercise.id = dto.id;
    exercise.title = dto.title;
    exercise.type = dto.type;
    exercise.assessmentType = dto.assessmentType;
    exercise.releaseDate = convertDateStringFromServer(dto.releaseDate);
    exercise.startDate = convertDateStringFromServer(dto.startDate);
    exercise.dueDate = convertDateStringFromServer(dto.dueDate);
    exercise.assessmentDueDate = convertDateStringFromServer(dto.assessmentDueDate);
    exercise.maxPoints = dto.maxPoints;
    const course = dto.course ? deepClone(dto.course) : undefined;
    exercise.course = dto.exerciseGroup ? undefined : course;
    exercise.exerciseGroup = dto.exerciseGroup ? fromExerciseGroupDTO(dto.exerciseGroup, course) : undefined;
    return exercise;
}

function fromExerciseGroupDTO(dto: NonNullable<ParticipationExerciseContextDTO['exerciseGroup']>, course: Course | undefined): ExerciseGroup {
    const exerciseGroup = new ExerciseGroup();
    exerciseGroup.id = dto.id;
    exerciseGroup.exam = new Exam();
    exerciseGroup.exam.id = dto.exam.id;
    exerciseGroup.exam.course = course;
    return exerciseGroup;
}

function fromParticipationSubmissionDTO(dto: ParticipationSubmissionDTO): Submission {
    const submission = createSubmission(dto.submissionExerciseType);
    submission.id = dto.id;
    submission.submitted = dto.submitted;
    submission.submissionDate = convertDateStringFromServer(dto.submissionDate);
    submission.results = dto.results?.map(fromParticipationSubmissionResultDTO);
    if (submission instanceof ProgrammingSubmission) {
        submission.commitHash = dto.commitHash;
    } else if (submission instanceof TextSubmission) {
        submission.text = dto.text;
        submission.language = dto.language;
    } else if (submission instanceof ModelingSubmission) {
        submission.model = dto.model;
        submission.explanationText = dto.explanationText;
    } else if (submission instanceof FileUploadSubmission) {
        submission.filePath = dto.filePath;
    }
    return submission;
}

function createSubmission(type: SubmissionExerciseType): Submission {
    switch (type) {
        case SubmissionExerciseType.PROGRAMMING:
            return new ProgrammingSubmission();
        case SubmissionExerciseType.MODELING:
            return new ModelingSubmission();
        case SubmissionExerciseType.QUIZ:
            return new QuizSubmission();
        case SubmissionExerciseType.TEXT:
            return new TextSubmission();
        case SubmissionExerciseType.FILE_UPLOAD:
            return new FileUploadSubmission();
    }
}

function fromParticipationSubmissionResultDTO(dto: ParticipationSubmissionResultDTO): Result {
    const result = new Result();
    result.id = dto.id;
    result.completionDate = convertDateStringFromServer(dto.completionDate);
    result.successful = dto.successful;
    result.score = dto.score;
    result.rated = dto.rated;
    result.assessmentType = dto.assessmentType;
    result.testCaseCount = dto.testCaseCount;
    result.passedTestCaseCount = dto.passedTestCaseCount;
    result.codeIssueCount = dto.codeIssueCount;
    return result;
}
