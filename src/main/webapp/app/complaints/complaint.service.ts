import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';

import * as moment from 'moment';

import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { Exercise } from 'app/entities/exercise.model';
import { map } from 'rxjs/operators';

export type EntityResponseType = HttpResponse<Complaint>;
export type EntityResponseTypeArray = HttpResponse<Complaint[]>;

export interface IComplaintService {
    create: (complaint: Complaint, examId: number) => Observable<EntityResponseType>;
    findByResultId: (resultId: number) => Observable<EntityResponseType>;
    getNumberOfAllowedComplaintsInCourse: (courseId: number) => Observable<number>;
}

@Injectable({ providedIn: 'root' })
export class ComplaintService implements IComplaintService {
    private apiUrl = SERVER_API_URL + 'api';
    private resourceUrl = this.apiUrl + '/complaints';

    constructor(private http: HttpClient, private complaintResponseService: ComplaintResponseService) {}

    /**
     * Checks if a complaint is locked for the currently logged in user
     *
     * A complaint is locked if the associated complaint response is locked
     *
     * @param complaint complaint to check the lock status for
     * @param exercise exercise used to find out if currently logged in user is instructor
     */
    isComplaintLockedForLoggedInUser(complaint: Complaint, exercise: Exercise) {
        if (complaint.complaintResponse && complaint.accepted === undefined) {
            return this.complaintResponseService.isComplaintResponseLockedForLoggedInUser(complaint.complaintResponse, exercise);
        } else {
            return false;
        }
    }

    /**
     * Checks if the lock on a complaint is active and if the currently logged in user is the creator of the lock
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
     * @param complaint
     * @param examId the Id of the exam
     */
    create(complaint: Complaint, examId?: number): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(complaint);
        if (examId) {
            return this.http
                .post<Complaint>(`${this.resourceUrl}/exam/${examId}`, copy, { observe: 'response' })
                .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
        }
        return this.http.post<Complaint>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Find complaint by Result id.
     * @param resultId
     */
    findByResultId(resultId: number): Observable<EntityResponseType> {
        return this.http.get<Complaint>(`${this.resourceUrl}/results/${resultId}`, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Find complaints for instructor for specified test run exercise (complaintType == 'COMPLAINT').
     * @param exerciseId
     */
    getComplaintsForTestRun(exerciseId: number): Observable<EntityResponseTypeArray> {
        return this.http
            .get<Complaint[]>(`${this.apiUrl}/exercises/${exerciseId}/complaints-for-test-run-dashboard`, { observe: 'response' })
            .pipe(map((res: EntityResponseTypeArray) => this.convertDateFromServerArray(res)));
    }

    /**
     * Find more feedback requests for tutor in this exercise.
     * @param exerciseId
     */
    getMoreFeedbackRequestsForTutor(exerciseId: number): Observable<EntityResponseTypeArray> {
        return this.http
            .get<Complaint[]>(`${this.apiUrl}/exercises/${exerciseId}/more-feedback-for-assessment-dashboard`, { observe: 'response' })
            .pipe(map((res: EntityResponseTypeArray) => this.convertDateFromServerArray(res)));
    }

    /**
     * Get number of allowed complaints in this course.
     * @param courseId
     * @param teamMode If true, the number of allowed complaints for the user's team is returned
     */
    getNumberOfAllowedComplaintsInCourse(courseId: number, teamMode = false): Observable<number> {
        return this.http.get<number>(`${this.apiUrl}/courses/${courseId}/allowed-complaints?teamMode=${teamMode}`);
    }

    /**
     * Find all complaints by tutor id, course id and complaintType.
     * @param tutorId
     * @param courseId
     * @param complaintType
     */
    findAllByTutorIdForCourseId(tutorId: number, courseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.apiUrl}/courses/${courseId}/complaints?complaintType=${complaintType}&tutorId=${tutorId}`;
        return this.requestComplaintsFromUrl(url);
    }

    /**
     * Find all complaints by tutor id, exercise id and complaintType.
     * @param tutorId
     * @param exerciseId
     * @param complaintType
     */
    findAllByTutorIdForExerciseId(tutorId: number, exerciseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.apiUrl}/exercises/${exerciseId}/complaints?complaintType=${complaintType}&tutorId=${tutorId}`;
        return this.requestComplaintsFromUrl(url);
    }

    /**
     * Find all complaints by course id and complaintType.
     * @param courseId
     * @param complaintType
     */
    findAllByCourseId(courseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.apiUrl}/courses/${courseId}/complaints?complaintType=${complaintType}`;
        return this.requestComplaintsFromUrl(url);
    }

    /**
     * Find all complaints by course id and exam id.
     * @param courseId
     * @param examId
     */
    findAllByCourseIdAndExamId(courseId: number, examId: number): Observable<EntityResponseTypeArray> {
        const url = `${this.apiUrl}/courses/${courseId}/exams/${examId}/complaints`;
        return this.requestComplaintsFromUrl(url);
    }

    /**
     * Find all complaints by exercise id and complaintType.
     * @param exerciseId
     * @param complaintType
     */
    findAllByExerciseId(exerciseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.apiUrl}/exercises/${exerciseId}/complaints?complaintType=${complaintType}`;
        return this.requestComplaintsFromUrl(url);
    }

    private requestComplaintsFromUrl(url: string): Observable<EntityResponseTypeArray> {
        return this.http.get<Complaint[]>(url, { observe: 'response' }).pipe(map((res: EntityResponseTypeArray) => this.convertDateFromServerArray(res)));
    }

    private convertDateFromClient(complaint: Complaint): Complaint {
        return Object.assign({}, complaint, {
            submittedTime: complaint.submittedTime && moment(complaint.submittedTime).isValid ? complaint.submittedTime.toJSON() : undefined,
        });
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.submittedTime = res.body.submittedTime ? moment(res.body.submittedTime) : undefined;
            if (res.body?.complaintResponse) {
                this.complaintResponseService.convertDatesToMoment(res.body.complaintResponse);
            }
        }
        return res;
    }

    private convertDateFromServerArray(res: EntityResponseTypeArray): EntityResponseTypeArray {
        if (res.body) {
            res.body.forEach((complaint) => {
                complaint.submittedTime = complaint.submittedTime ? moment(complaint.submittedTime) : undefined;
                if (complaint.complaintResponse) {
                    this.complaintResponseService.convertDatesToMoment(complaint.complaintResponse);
                }
            });
        }
        return res;
    }
}
