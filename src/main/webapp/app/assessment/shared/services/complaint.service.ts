import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintResponseService } from 'app/assessment/manage/services/complaint-response.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { map } from 'rxjs/operators';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ComplaintRequestDTO } from 'app/assessment/shared/entities/complaint-request-dto.model';
import { ComplaintDTO } from 'app/assessment/shared/entities/complaint-dto.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { User } from 'app/core/user/user.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';

export type EntityResponseType = HttpResponse<Complaint>;
export type EntityResponseTypeArray = HttpResponse<Complaint[]>;
export type ComplaintDTOResponseType = HttpResponse<ComplaintDTO>;
export type ComplaintDTOArrayResponseType = HttpResponse<ComplaintDTO[]>;

export interface IComplaintService {
    isComplaintLockedForLoggedInUser: (complaint: Complaint, exercise: Exercise) => boolean | undefined;
    isComplaintLockedByLoggedInUser: (complaint: Complaint) => boolean | undefined;
    isComplaintLocked: (complaint: Complaint) => boolean | undefined;
    create: (complaintRequest: ComplaintRequestDTO) => Observable<EntityResponseType>;
    findBySubmissionId: (participationId: number) => Observable<EntityResponseType>;
    getComplaintsForTestRun: (exerciseId: number) => Observable<EntityResponseTypeArray>;
    findAllByTutorIdForCourseId: (tutorId: number, courseId: number, complaintType: ComplaintType) => Observable<EntityResponseTypeArray>;
    findAllByTutorIdForExerciseId: (tutorId: number, exerciseId: number, complaintType: ComplaintType) => Observable<EntityResponseTypeArray>;
    findAllByCourseId: (courseId: number, complaintType: ComplaintType) => Observable<EntityResponseTypeArray>;
    findAllByCourseIdAndExamId: (courseId: number, examId: number) => Observable<EntityResponseTypeArray>;
    findAllByExerciseId: (exerciseId: number, complaintType: ComplaintType) => Observable<EntityResponseTypeArray>;
}

@Injectable({ providedIn: 'root' })
export class ComplaintService implements IComplaintService {
    private http = inject(HttpClient);
    private complaintResponseService = inject(ComplaintResponseService);

    private resourceUrl = 'api/assessment/complaints';

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
     * Create a new complaint.
     * @param complaintRequest
     */
    create(complaintRequest: ComplaintRequestDTO): Observable<EntityResponseType> {
        return this.http
            .post<ComplaintDTO>(this.resourceUrl, complaintRequest, { observe: 'response' })
            .pipe(map((res: ComplaintDTOResponseType) => this.convertComplaintEntityResponseDatesFromServer(res)));
    }

    /**
     * Find complaint by Submission id.
     * @param submissionId
     */
    findBySubmissionId(submissionId: number): Observable<EntityResponseType> {
        return this.http
            .get<ComplaintDTO>(`${this.resourceUrl}?submissionId=${submissionId}`, { observe: 'response' })
            .pipe(map((res: ComplaintDTOResponseType) => this.convertComplaintEntityResponseDatesFromServer(res)));
    }

    /**
     * Find complaints for the instructor for a specified test run exercise (complaintType == 'COMPLAINT').
     * @param exerciseId
     */
    getComplaintsForTestRun(exerciseId: number): Observable<EntityResponseTypeArray> {
        return this.http
            .get<ComplaintDTO[]>(`${this.resourceUrl}?exerciseId=${exerciseId}`, { observe: 'response' })
            .pipe(map((res: ComplaintDTOArrayResponseType) => this.convertComplaintEntityResponseArrayDateFromServer(res)));
    }

    /**
     * Find all complaints by tutor id, course id, and complaintType.
     * @param tutorId
     * @param courseId
     * @param complaintType
     */
    findAllByTutorIdForCourseId(tutorId: number, courseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?courseId=${courseId}&complaintType=${complaintType}&tutorId=${tutorId}`;
        return this.requestComplaintsFromUrl(url);
    }

    /**
     * Find all complaints by course id and complaintType without student's information
     * @param courseId - the course id for which the complaints should be retrieved
     * @param complaintType - the type of complaint
     */
    findAllWithoutStudentInformationForCourseId(courseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?courseId=${courseId}&complaintType=${complaintType}&allComplaintsForTutor=true`;
        return this.requestComplaintsFromUrl(url);
    }

    /**
     * Find all complaints by tutor id, exercise id, and complaintType.
     * @param tutorId
     * @param exerciseId
     * @param complaintType
     */
    findAllByTutorIdForExerciseId(tutorId: number, exerciseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?exerciseId=${exerciseId}&complaintType=${complaintType}&tutorId=${tutorId}`;
        return this.requestComplaintsFromUrl(url);
    }

    /**
     * Find all complaints by course id and complaintType.
     * @param courseId
     * @param complaintType
     */
    findAllByCourseId(courseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?courseId=${courseId}&complaintType=${complaintType}`;
        return this.requestComplaintsFromUrl(url);
    }

    /**
     * Find all complaints by course id and exam id.
     * @param courseId
     * @param examId
     */
    findAllByCourseIdAndExamId(courseId: number, examId: number): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?courseId=${courseId}&examId=${examId}`;
        return this.requestComplaintsFromUrl(url);
    }

    /**
     * Find all complaints by exercise id and complaintType.
     * @param exerciseId
     * @param complaintType
     */
    findAllByExerciseId(exerciseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?exerciseId=${exerciseId}&complaintType=${complaintType}`;
        return this.requestComplaintsFromUrl(url);
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

    private requestComplaintsFromUrl(url: string): Observable<EntityResponseTypeArray> {
        return this.http
            .get<ComplaintDTO[]>(url, { observe: 'response' })
            .pipe(map((res: ComplaintDTOArrayResponseType) => this.convertComplaintEntityResponseArrayDateFromServer(res)));
    }

    private convertComplaintEntityResponseArrayDateFromServer(res: ComplaintDTOArrayResponseType): EntityResponseTypeArray {
        return res.clone({
            body: res.body ? res.body.map((dto) => this.convertComplaintFromServer(dto)) : undefined,
        });
    }

    private convertComplaintEntityResponseDatesFromServer(res: ComplaintDTOResponseType): HttpResponse<Complaint> {
        return res.clone({ body: res.body ? this.convertComplaintFromServer(res.body) : undefined });
    }

    private convertComplaintFromServer(dto: ComplaintDTO): Complaint {
        const complaint = new Complaint();
        complaint.id = dto.id;
        complaint.complaintText = dto.complaintText;
        complaint.complaintType = dto.complaintType;
        complaint.accepted = dto.complaintIsAccepted;
        complaint.submittedTime = dto.submittedTime ? dayjs(dto.submittedTime) : undefined;

        if (dto.complaintResponse) {
            complaint.complaintResponse = this.complaintResponseService.convertComplaintResponseFromServer(dto.complaintResponse);
        }

        if (dto.result) {
            const result = new Result();
            result.id = dto.result.id;
            result.completionDate = dto.result.completionDate ? dayjs(dto.result.completionDate) : undefined;
            result.score = dto.result.score;
            result.rated = dto.result.rated;
            result.assessmentType = dto.result.assessmentType;

            if (dto.result.assessorId !== undefined) {
                result.assessor = {
                    id: dto.result.assessorId,
                } as User;
            }

            if (dto.result.feedbacks) {
                result.feedbacks = dto.result.feedbacks.map((feedbackDTO) => ({
                    id: feedbackDTO.id,
                    text: feedbackDTO.text,
                    detailText: feedbackDTO.detailText,
                    hasLongFeedbackText: feedbackDTO.hasLongFeedbackText,
                    reference: feedbackDTO.reference,
                    credits: feedbackDTO.credits,
                    positive: feedbackDTO.positive,
                    type: feedbackDTO.type,
                    visibility: feedbackDTO.visibility,
                    testCase: feedbackDTO.testCaseName ? { testName: feedbackDTO.testCaseName } : undefined,
                })) as any;
            }

            if (dto.result.submission) {
                const submission = {
                    id: dto.result.submission.id,
                } as Submission;

                if (dto.result.submission.participation) {
                    submission.participation = {
                        id: dto.result.submission.participation.id,
                        exercise: dto.result.submission.participation.exercise
                            ? ({
                                  id: dto.result.submission.participation.exercise.id,
                                  type: dto.result.submission.participation.exercise.type,
                              } as Exercise)
                            : undefined,
                    } as StudentParticipation;
                }

                result.submission = submission;
            }
            complaint.result = result;
        }
        if (dto.participant) {
            if (dto.participant.isStudent === true) {
                complaint.student = {
                    id: dto.participant.id,
                } as User;
            } else if (dto.participant.isStudent === false) {
                complaint.team = {
                    id: dto.participant.id,
                } as Team;
            }
        }
        return complaint;
    }
}
