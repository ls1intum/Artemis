import { Submission, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission-exercise-type.model';
import { addPublicFilePrefix } from 'app/app.constants';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { InitializationState, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterionDTO } from 'app/exercise/shared/exercise-update-shared-dto.model';

export interface FileUploadSubmissionInputDTO {
    id?: number;
    submitted: boolean;
    exerciseId?: number;
}

export interface FileUploadCourseContextDTO {
    id?: number;
    title?: string;
    shortName?: string;
    teachingAssistantGroupName?: string;
    editorGroupName?: string;
    instructorGroupName?: string;
    accuracyOfScores?: number;
    maxComplaints?: number;
    maxTeamComplaints?: number;
    maxComplaintTimeDays?: number;
    maxRequestMoreFeedbackTimeDays?: number;
    maxComplaintTextLimit?: number;
    maxComplaintResponseTextLimit?: number;
    complaintsEnabled?: boolean;
    requestMoreFeedbackEnabled?: boolean;
}

export interface FileUploadExamContextDTO {
    id?: number;
    course?: FileUploadCourseContextDTO;
}

export interface FileUploadExerciseGroupContextDTO {
    id?: number;
    exam?: FileUploadExamContextDTO;
}

export interface FileUploadExerciseContextDTO {
    id?: number;
    title?: string;
    problemStatement?: string;
    gradingInstructions?: string;
    exampleSolution?: string;
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
    maxPoints?: number;
    bonusPoints?: number;
    assessmentType?: AssessmentType;
    allowComplaintsForAutomaticAssessments?: boolean;
    allowFeedbackRequests?: boolean;
    type?: ExerciseType;
    filePattern?: string;
    teamMode?: boolean;
    isAtLeastTutor?: boolean;
    isAtLeastEditor?: boolean;
    isAtLeastInstructor?: boolean;
    course?: FileUploadCourseContextDTO;
    exerciseGroup?: FileUploadExerciseGroupContextDTO;
    gradingCriteria?: GradingCriterionDTO[];
}

export interface FileUploadParticipationDTO {
    id?: number;
    initializationState?: InitializationState;
    initializationDate?: dayjs.Dayjs;
    individualDueDate?: dayjs.Dayjs;
    presentationScore?: number;
    submissionCount?: number;
    type?: ParticipationType;
    testRun?: boolean;
    participantName?: string;
    participantIdentifier?: string;
    isOwner?: boolean;
    exercise?: FileUploadExerciseContextDTO;
}

export interface FileUploadSubmissionDTO {
    id?: number;
    submitted?: boolean;
    submissionDate?: dayjs.Dayjs;
    type?: SubmissionType;
    exampleSubmission?: boolean;
    submissionExerciseType?: SubmissionExerciseType;
    durationInMinutes?: number;
    filePath?: string;
    participation?: FileUploadParticipationDTO;
    results?: Result[];
}

export class FileUploadParticipation extends StudentParticipation {
    public isOwner?: boolean;
}

export class FileUploadSubmission extends Submission {
    public filePath?: string;
    public filePathUrl?: string;

    constructor() {
        super(SubmissionExerciseType.FILE_UPLOAD);
        this.filePathUrl = addPublicFilePrefix(this.filePath);
    }
}
