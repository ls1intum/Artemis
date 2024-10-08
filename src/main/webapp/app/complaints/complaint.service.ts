import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { map } from 'rxjs/operators';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { convertDateFromServer } from 'app/utils/date.utils';
import { Result } from 'app/entities/result.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ComplaintRequestDTO } from 'app/entities/complaint-request-dto.model';

export type EntityResponseType = HttpResponse<Complaint>;
export type EntityResponseTypeArray = HttpResponse<Complaint[]>;

export interface IComplaintService {
    isComplaintLockedForLoggedInUser: (complaint: Complaint, exercise: Exercise) => boolean | undefined;
    isComplaintLockedByLoggedInUser: (complaint: Complaint) => boolean | undefined;
    isComplaintLocked: (complaint: Complaint) => boolean | undefined;
    create: (complaint: Complaint, examId: number) => Observable<EntityResponseType>;
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

    private resourceUrl = 'api/complaints';

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
            .post<Complaint>(this.resourceUrl, complaintRequest, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertComplaintEntityResponseDatesFromServer(res)));
    }

    /**
     * Find complaint by Submission id.
     * @param submissionId
     */
    findBySubmissionId(submissionId: number): Observable<EntityResponseType> {
        return this.http
            .get<Complaint>(`${this.resourceUrl}?submissionId=${submissionId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertComplaintEntityResponseDatesFromServer(res)));
    }

    /**
     * Find complaints for instructor for specified test run exercise (complaintType == 'COMPLAINT').
     * @param exerciseId
     */
    getComplaintsForTestRun(exerciseId: number): Observable<EntityResponseTypeArray> {
        return this.http
            .get<Complaint[]>(`${this.resourceUrl}?exerciseId=${exerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseTypeArray) => this.convertComplaintEntityResponseArrayDateFromServer(res)));
    }

    /**
     * Find all complaints by tutor id, course id and complaintType.
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
     * Find all complaints by tutor id, exercise id and complaintType.
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
        return this.http.get<Complaint[]>(url, { observe: 'response' }).pipe(map((res: EntityResponseTypeArray) => this.convertComplaintEntityResponseArrayDateFromServer(res)));
    }

    private convertComplaintEntityResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.submittedTime = res.body.submittedTime ? dayjs(res.body.submittedTime) : undefined;
            if (res.body?.complaintResponse) {
                this.complaintResponseService.convertComplaintResponseDatesFromServer(res.body.complaintResponse);
            }
        }
        return res;
    }

    private convertComplaintEntityResponseArrayDateFromServer(res: EntityResponseTypeArray): EntityResponseTypeArray {
        if (res.body) {
            res.body.forEach((complaint) => {
                complaint.submittedTime = convertDateFromServer(complaint.submittedTime);
                if (complaint.complaintResponse) {
                    this.complaintResponseService.convertComplaintResponseDatesFromServer(complaint.complaintResponse);
                }
            });
        }
        return res;
    }
}
