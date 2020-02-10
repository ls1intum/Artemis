import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import * as moment from 'moment';

import { Complaint, ComplaintType } from 'app/entities/complaint/complaint.model';

export type EntityResponseType = HttpResponse<Complaint>;
export type EntityResponseTypeArray = HttpResponse<Complaint[]>;

export interface IComplaintService {
    create: (complaint: Complaint) => Observable<EntityResponseType>;
    findByResultId: (resultId: number) => Observable<EntityResponseType>;
    getNumberOfAllowedComplaintsInCourse: (courseId: number) => Observable<number>;
}

@Injectable({ providedIn: 'root' })
export class ComplaintService implements IComplaintService {
    private apiUrl = SERVER_API_URL + 'api';
    private resourceUrl = this.apiUrl + '/complaints';

    constructor(private http: HttpClient) {}

    /**
     * Create a new complaint.
     * @param complaint
     */
    create(complaint: Complaint): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(complaint);
        return this.http
            .post<Complaint>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    /**
     * Find complaint by Result id.
     * @param resultId
     */
    findByResultId(resultId: number): Observable<EntityResponseType> {
        return this.http
            .get<Complaint>(`${this.resourceUrl}/result/${resultId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    /**
     * Find complaints for tutor for specified exercise (complaintType == 'COMPLAINT').
     * @param exerciseId
     */
    getComplaintsForTutor(exerciseId: number): Observable<EntityResponseTypeArray> {
        return this.http
            .get<Complaint[]>(`${this.apiUrl}/exercises/${exerciseId}/complaints-for-tutor-dashboard`, { observe: 'response' })
            .map((res: EntityResponseTypeArray) => this.convertDateFromServerArray(res));
    }

    /**
     * Find more feedback requests for tutor in this exercise.
     * @param exerciseId
     */
    getMoreFeedbackRequestsForTutor(exerciseId: number): Observable<EntityResponseTypeArray> {
        return this.http
            .get<Complaint[]>(`${this.apiUrl}/exercises/${exerciseId}/more-feedback-for-tutor-dashboard`, { observe: 'response' })
            .map((res: EntityResponseTypeArray) => this.convertDateFromServerArray(res));
    }

    /**
     * Get number of allowed complaints in this course.
     * @param courseId
     */
    getNumberOfAllowedComplaintsInCourse(courseId: number): Observable<number> {
        return this.http.get<number>(`${this.apiUrl}/${courseId}/allowed-complaints`);
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
     * Find all complaints by exercise id and complaintType.
     * @param exerciseId
     * @param complaintType
     */
    findAllByExerciseId(exerciseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        const url = `${this.apiUrl}/exercises/${exerciseId}/complaints?complaintType=${complaintType}`;
        return this.requestComplaintsFromUrl(url);
    }

    private requestComplaintsFromUrl(url: string): Observable<EntityResponseTypeArray> {
        return this.http
            .get<Complaint[]>(url, { observe: 'response' })
            .map((res: EntityResponseTypeArray) => this.convertDateFromServerArray(res));
    }

    private convertDateFromClient(complaint: Complaint): Complaint {
        return Object.assign({}, complaint, {
            submittedTime: complaint.submittedTime != null && moment(complaint.submittedTime).isValid ? complaint.submittedTime.toJSON() : null,
        });
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.submittedTime = res.body.submittedTime != null ? moment(res.body.submittedTime) : null;
        }
        return res;
    }

    private convertDateFromServerArray(res: EntityResponseTypeArray): EntityResponseTypeArray {
        if (res.body) {
            res.body.forEach(complaint => {
                complaint.submittedTime = complaint.submittedTime != null ? moment(complaint.submittedTime) : null;
            });
        }

        return res;
    }
}
