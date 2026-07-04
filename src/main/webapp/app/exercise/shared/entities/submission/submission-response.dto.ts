import { User, UserPublicInfoDTO } from 'app/account/user/user.model';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Feedback, FeedbackDTO, convertFeedbackFromServer } from 'app/assessment/shared/entities/feedback.model';
import { Language } from 'app/course/shared/entities/course.model';
import { StudentParticipationDTO, fromStudentParticipationDTO } from 'app/exercise/shared/entities/participation/student-participation.dto';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission, SubmissionExerciseType, SubmissionType, setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';

export interface SubmissionAssessmentNoteDTO {
    id: number;
    note?: string;
    creator?: UserPublicInfoDTO;
}

export interface SubmissionResultDTO {
    id: number;
    completionDate?: string;
    successful?: boolean;
    score?: number;
    rated: boolean;
    assessmentType?: AssessmentType;
    hasComplaint?: boolean;
    exampleResult?: boolean;
    testCaseCount?: number;
    passedTestCaseCount?: number;
    codeIssueCount?: number;
    assessor?: UserPublicInfoDTO;
    feedbacks?: FeedbackDTO[];
    assessmentNote?: SubmissionAssessmentNoteDTO;
}

export interface SubmissionResponseDTO {
    id: number;
    submitted: boolean;
    type?: SubmissionType;
    exampleSubmission?: boolean;
    submissionDate?: string;
    durationInMinutes?: number;
    submissionExerciseType: SubmissionExerciseType;
    participation?: StudentParticipationDTO;
    results?: SubmissionResultDTO[];
    commitHash?: string;
    buildFailed?: boolean;
    text?: string;
    language?: Language;
    model?: string;
    explanationText?: string;
    filePath?: string;
    quizBatch?: number;
    scoreInPoints?: number;
}

/**
 * Converts a submission wire DTO into the existing component-facing subtype and reconnects its relationships.
 */
export function fromSubmissionResponseDTO(dto: SubmissionResponseDTO): Submission {
    const submission = createSubmission(dto.submissionExerciseType);
    submission.id = dto.id;
    submission.submitted = dto.submitted;
    submission.type = dto.type;
    submission.exampleSubmission = dto.exampleSubmission;
    submission.submissionDate = convertDateStringFromServer(dto.submissionDate);
    submission.durationInMinutes = dto.durationInMinutes;
    submission.participation = dto.participation ? fromStudentParticipationDTO(dto.participation) : undefined;
    submission.results = dto.results?.map(fromSubmissionResultDTO);

    if (submission instanceof ProgrammingSubmission) {
        submission.commitHash = dto.commitHash;
        submission.buildFailed = dto.buildFailed;
    } else if (submission instanceof TextSubmission) {
        submission.text = dto.text;
        submission.language = dto.language;
    } else if (submission instanceof ModelingSubmission) {
        submission.model = dto.model;
        submission.explanationText = dto.explanationText;
    } else if (submission instanceof FileUploadSubmission) {
        submission.filePath = dto.filePath;
    } else if (submission instanceof QuizSubmission) {
        submission.quizBatch = dto.quizBatch;
        submission.scoreInPoints = dto.scoreInPoints;
    }

    submission.results?.forEach((result) => (result.submission = submission));
    setLatestSubmissionResult(submission, submission.results?.last());
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

function fromSubmissionResultDTO(dto: SubmissionResultDTO): Result {
    const result = new Result();
    result.id = dto.id;
    result.completionDate = convertDateStringFromServer(dto.completionDate);
    result.successful = dto.successful;
    result.score = dto.score;
    result.rated = dto.rated;
    result.assessmentType = dto.assessmentType;
    result.hasComplaint = dto.hasComplaint;
    result.exampleResult = dto.exampleResult;
    result.testCaseCount = dto.testCaseCount;
    result.passedTestCaseCount = dto.passedTestCaseCount;
    result.codeIssueCount = dto.codeIssueCount;
    result.assessor = dto.assessor ? Object.assign(new User(), dto.assessor) : undefined;
    result.feedbacks = dto.feedbacks?.map((feedback) => reconnectFeedback(convertFeedbackFromServer(feedback), result));
    result.assessmentNote = dto.assessmentNote ? fromSubmissionAssessmentNoteDTO(dto.assessmentNote) : undefined;
    return result;
}

function reconnectFeedback(feedback: Feedback, result: Result): Feedback {
    feedback.result = result;
    return feedback;
}

function fromSubmissionAssessmentNoteDTO(dto: SubmissionAssessmentNoteDTO): AssessmentNote {
    const assessmentNote = new AssessmentNote();
    assessmentNote.id = dto.id;
    assessmentNote.note = dto.note;
    assessmentNote.creator = dto.creator ? Object.assign(new User(), dto.creator) : undefined;
    return assessmentNote;
}
