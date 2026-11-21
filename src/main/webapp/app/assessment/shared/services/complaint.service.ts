import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintResponseService } from 'app/assessment/manage/services/complaint-response.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { User } from 'app/account/user/user.model';
import { Feedback, convertFeedbackFromServer } from 'app/assessment/shared/entities/feedback.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { feedbackTypeByResponse, visibilityByResponse } from 'app/assessment/shared/util/feedback-response.util';
import { ComplaintApi } from 'app/openapi/api/complaint-api';
import type { Complaint as ComplaintDTO } from 'app/openapi/model/complaint';
import type { ComplaintBySubmissionQuery } from 'app/openapi/model/complaint-by-submission-query';
import type { ComplaintFeedback } from 'app/openapi/model/complaint-feedback';
import type { ComplaintsForTutorQuery } from 'app/openapi/model/complaints-for-tutor-query';
import type { ComplaintsQuery } from 'app/openapi/model/complaints-query';
import type { Exercise as ExerciseResponse } from 'app/openapi/model/exercise';
import type { GetComplaints200Response } from 'app/openapi/model/get-complaints200response';
import type { Participant } from 'app/openapi/model/participant';
import type { ResultSimple } from 'app/openapi/model/result-simple';

/** The queries of {@link ComplaintApi#getComplaints} that return a list of complaints. */
export type ComplaintListQuery = Exclude<ComplaintsQuery, ComplaintBySubmissionQuery>;

const complaintTypeByResponse: Record<NonNullable<ComplaintDTO['complaintType']>, ComplaintType> = {
    COMPLAINT: ComplaintType.COMPLAINT,
    MORE_FEEDBACK: ComplaintType.MORE_FEEDBACK,
};

const assessmentTypeByResponse: Record<NonNullable<ResultSimple['assessmentType']>, AssessmentType> = {
    AUTOMATIC: AssessmentType.AUTOMATIC,
    SEMI_AUTOMATIC: AssessmentType.SEMI_AUTOMATIC,
    MANUAL: AssessmentType.MANUAL,
    AUTOMATIC_ATHENA: AssessmentType.AUTOMATIC_ATHENA,
};

const exerciseTypeByResponse: Record<NonNullable<ExerciseResponse['type']>, ExerciseType> = {
    text: ExerciseType.TEXT,
    programming: ExerciseType.PROGRAMMING,
    modeling: ExerciseType.MODELING,
    'file-upload': ExerciseType.FILE_UPLOAD,
    quiz: ExerciseType.QUIZ,
};

/**
 * Reads complaints through the generated {@link ComplaintApi} and converts them to the client {@link Complaint}: dates become dayjs, the
 * enums become the client enums, and the participant becomes a student or a team. Also keeps the complaint lock rules.
 */
@Service()
export class ComplaintService {
    private complaintApi = inject(ComplaintApi);
    private complaintResponseService = inject(ComplaintResponseService);

    /**
     * Checks if a complaint is locked for the currently logged-in user
     *
     * A complaint is locked if the associated complaint response is locked
     *
     * @param complaint complaint to check the lock status for
     * @param exercise exercise used to find out if currently logged-in user is instructor
     */
    isComplaintLockedForLoggedInUser(complaint: Complaint, exercise: Exercise) {
        if (complaint.complaintResponse && complaint.accepted === undefined) {
            return this.complaintResponseService.isComplaintResponseLockedForLoggedInUser(complaint.complaintResponse, exercise);
        } else {
            return false;
        }
    }

    /**
     * Checks if the lock on a complaint is active and if the currently logged-in user is the creator of the lock
     * @param complaint complaint to check the lock status for
     */
    isComplaintLockedByLoggedInUser(complaint: Complaint) {
        if (complaint.complaintResponse && complaint.accepted === undefined) {
            return this.complaintResponseService.isComplaintResponseLockedByLoggedInUser(complaint.complaintResponse);
        } else {
            return false;
        }
    }

    /**
     * Checks if a complaint is locked
     * @param complaint complaint to check lock status for
     */
    isComplaintLocked(complaint: Complaint) {
        if (complaint.complaintResponse && complaint.accepted === undefined) {
            return complaint.complaintResponse.isCurrentlyLocked;
        } else {
            return false;
        }
    }

    /**
     * Finds the complaint about a result of the submission, for the owner of the participation. The server answers with an empty body when
     * there is none.
     * @param submissionId the submission whose complaint is requested
     */
    findBySubmissionId(submissionId: number): Observable<ComplaintDTO | undefined> {
        return this.complaintApi.getComplaints({ submissionId }).pipe(map((body) => (!body || Array.isArray(body) ? undefined : body)));
    }

    /**
     * Finds the complaint about a result of the submission for its assessors rather than the participation owner. The server answers with
     * an empty body when there is none.
     * @param submissionId the submission whose complaint is requested
     */
    findBySubmissionIdForAssessment(submissionId: number): Observable<ComplaintDTO | undefined> {
        return this.complaintApi.getComplaintBySubmissionIdForAssessment(submissionId).pipe(map((body) => body ?? undefined));
    }

    /**
     * Finds the complaints of a course, exercise, exam or test run with student information, for instructors.
     * @param query the scope of the list, which selects the server handler
     */
    findAll(query: ComplaintListQuery): Observable<Complaint[]> {
        return this.complaintApi.getComplaints(query).pipe(map((body) => this.convertComplaintListFromServer(body)));
    }

    /**
     * Finds the complaints of a course or exercise without student information, for tutors.
     * @param query the scope of the list, and whether it holds the complaints about every assessor or only the current user's
     */
    findAllForTutor(query: ComplaintsForTutorQuery): Observable<Complaint[]> {
        return this.complaintApi.getComplaintsForTutor(query).pipe(map((body) => this.convertComplaintListFromServer(body)));
    }

    /**
     * Returns the time needed to evaluate the complaint. If it hasn't been evaluated yet, the difference between the submission time and now is used.
     * @param complaint for which the response time should be calculated
     * @return returns the passed time in seconds
     */
    getResponseTimeInSeconds(complaint: Complaint): number {
        if (complaint.accepted !== undefined) {
            return complaint.complaintResponse?.submittedTime?.diff(complaint.submittedTime, 'seconds') || NaN;
        } else {
            return dayjs().diff(complaint.submittedTime, 'seconds');
        }
    }

    /**
     * Determines if the complaint should be highlighted. This is the case if the complaint hasn't been reviewed and was submitted more than one week ago.
     * @param complaint for which it should be determined if highlighting is needed
     * @return returns true iff the complaint should be highlighted
     */
    shouldHighlightComplaint(complaint: Complaint): boolean {
        if (complaint.accepted !== undefined) {
            return false;
        }

        const complaintSubmittedTime = complaint.submittedTime;
        if (complaintSubmittedTime) {
            return dayjs().diff(complaintSubmittedTime, 'days') > 7;
        }

        return false;
    }

    /**
     * Calculates the date until which a complaint can be filed at least
     * @param exercise for which the student can complain
     * @param complaintTimeFrame number of days the student has to file the complaint
     * @param result of the student in the exercise that might receive complain
     * @param studentParticipation the participation which might contain an individual due date
     * @return the date until which the student can complain
     */
    static getIndividualComplaintDueDate(
        exercise: Exercise,
        complaintTimeFrame: number,
        result: Result | undefined,
        studentParticipation?: StudentParticipation,
    ): dayjs.Dayjs | undefined {
        // No complaints if there is no result or the exercise does not support complaints
        if (
            !result?.completionDate ||
            (exercise.assessmentType === AssessmentType.AUTOMATIC && !exercise.allowComplaintsForAutomaticAssessments) ||
            (!exercise.allowComplaintsForAutomaticAssessments && !result.rated) ||
            exercise.type === ExerciseType.QUIZ
        ) {
            return undefined;
        }

        const relevantDueDate = studentParticipation?.individualDueDate ?? exercise.dueDate;
        const possibleComplaintStartDates = [dayjs(result.completionDate)];
        if (relevantDueDate) {
            possibleComplaintStartDates.push(dayjs(relevantDueDate));
        }
        if (exercise.assessmentDueDate) {
            possibleComplaintStartDates.push(dayjs(exercise.assessmentDueDate));
        }
        const complaintStartDate = dayjs.max(possibleComplaintStartDates);

        if (!complaintStartDate || dayjs().isBefore(complaintStartDate)) {
            return undefined;
        }
        return complaintStartDate.add(complaintTimeFrame, 'days');
    }

    /**
     * Converts a complaint DTO into a {@link Complaint}, attaching the already-loaded result the complaint belongs to.
     * Used when the surrounding component already holds the full result (e.g. assessment editors). The result may be
     * undefined for views that do not consume {@link Complaint#result}.
     */
    public convertComplaintFromServer(dto: ComplaintDTO, result: Result | undefined): Complaint {
        const complaint = this.convertComplaintBaseFromServer(dto);
        complaint.result = result;
        return complaint;
    }

    /**
     * Converts a complaint DTO into a {@link Complaint}, reconstructing the reduced result from the DTO.
     * Used for complaint lists where only the slim {@link ResultSimple} is available.
     */
    public convertComplaintFromServerInList(dto: ComplaintDTO): Complaint {
        const complaint = this.convertComplaintBaseFromServer(dto);
        if (dto.result) {
            complaint.result = this.convertResultSimpleFromServer(dto.result);
        }
        return complaint;
    }

    private convertComplaintListFromServer(body: GetComplaints200Response | null): Complaint[] {
        return Array.isArray(body) ? body.map((dto) => this.convertComplaintFromServerInList(dto)) : [];
    }

    /**
     * Converts the fields shared by both complaint conversions (without the result, which differs between the two).
     */
    private convertComplaintBaseFromServer(dto: ComplaintDTO): Complaint {
        const complaint = new Complaint();
        complaint.id = dto.id;
        complaint.complaintText = dto.complaintText;
        complaint.complaintType = dto.complaintType && complaintTypeByResponse[dto.complaintType];
        complaint.accepted = dto.complaintIsAccepted;
        complaint.submittedTime = dto.submittedTime ? dayjs(dto.submittedTime) : undefined;
        complaint.assessorKey = dto.assessorKey;
        complaint.assessorLabel = dto.assessorLabel;

        if (dto.complaintResponse) {
            complaint.complaintResponse = this.complaintResponseService.convertComplaintResponseFromServer(dto.complaintResponse);
        }
        this.assignParticipant(complaint, dto.participant);
        return complaint;
    }

    /**
     * Assigns the participant of the complaint as either a student or a team, depending on the DTO flag.
     */
    private assignParticipant(complaint: Complaint, participant?: Participant): void {
        if (participant?.isStudent === true) {
            complaint.student = hydrate(new User(), {
                id: participant.id,
                name: participant.name,
                login: participant.login,
            });
        } else if (participant?.isStudent === false) {
            complaint.team = hydrate(new Team(), {
                id: participant.id,
                name: participant.name,
                shortName: participant.login,
            });
        }
    }

    /**
     * Reconstructs a {@link Result} from the slim {@link ResultSimple} contained in a complaint list response.
     */
    private convertResultSimpleFromServer(resultDto: ResultSimple): Result {
        const result = new Result();
        result.id = resultDto.id;
        result.completionDate = resultDto.completionDate ? dayjs(resultDto.completionDate) : undefined;
        result.score = resultDto.score;
        result.rated = resultDto.rated;
        result.successful = resultDto.successful;
        result.assessmentType = resultDto.assessmentType && assessmentTypeByResponse[resultDto.assessmentType];

        if (resultDto.assessor) {
            result.assessor = hydrate(new User(), {
                id: resultDto.assessor.id,
                login: resultDto.assessor.login,
                name: resultDto.assessor.name,
            });
        }

        if (resultDto.feedbacks) {
            result.feedbacks = resultDto.feedbacks.map((feedback) => this.convertFeedbackFromServer(feedback));
        }

        if (resultDto.submission) {
            const submission: Submission = { id: resultDto.submission.id };

            if (resultDto.submission.participation) {
                const participation = hydrate(new StudentParticipation(), {
                    id: resultDto.submission.participation.id,
                });

                const exerciseDto = resultDto.submission.participation.exercise;
                if (exerciseDto || resultDto.exerciseTitle) {
                    const exercise: Partial<Exercise> = {
                        id: exerciseDto?.id,
                        type: exerciseDto?.type && exerciseTypeByResponse[exerciseDto.type],
                        title: resultDto.exerciseTitle,
                    };
                    participation.exercise = exercise as Exercise;
                }
                submission.participation = participation;
            }
            result.submission = submission;
        }
        return result;
    }

    /**
     * Converts a feedback of a complaint's result like every other feedback from the server, including the test case it names.
     */
    private convertFeedbackFromServer(feedback: ComplaintFeedback): Feedback {
        return convertFeedbackFromServer({
            id: feedback.id,
            text: feedback.text,
            detailText: feedback.detailText,
            hasLongFeedbackText: feedback.hasLongFeedbackText,
            reference: feedback.reference,
            credits: feedback.credits,
            positive: feedback.positive,
            type: feedback.type && feedbackTypeByResponse[feedback.type],
            visibility: feedback.visibility && visibilityByResponse[feedback.visibility],
            testCaseName: feedback.testCaseName,
            gradingInstruction: feedback.gradingInstruction,
        });
    }

    /**
     * Returns feedbacks without circular references.
     */
    public getFeedbacksForUpdateAfterComplaint(assessments: Feedback[]): Feedback[] {
        return assessments.map((feedback) => hydrate(new Feedback(), feedback, { result: undefined }));
    }

    /**
     * Returns a complaint response payload without circular references.
     */
    public getComplaintResponseForUpdateAfterComplaint(complaintResponse: ComplaintResponse): ComplaintResponse {
        const sanitizedComplaintResponse = hydrate(new ComplaintResponse(), complaintResponse);

        if (complaintResponse.complaint) {
            sanitizedComplaintResponse.complaint = hydrate(new Complaint(), {
                id: complaintResponse.complaint.id,
                accepted: complaintResponse.complaint.accepted,
                complaintType: complaintResponse.complaint.complaintType,
            });
        }

        return sanitizedComplaintResponse;
    }
}
