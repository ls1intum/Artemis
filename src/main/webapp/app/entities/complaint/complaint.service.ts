import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import * as moment from 'moment';

import { Complaint } from 'app/entities/complaint/complaint.model';

export type EntityResponseType = HttpResponse<Complaint>;
export type EntityResponseTypeArray = HttpResponse<Complaint[]>;

export interface IComplaintService {
    create: (complaint: Complaint) => Observable<EntityResponseType>;
    find: (id: number) => Observable<EntityResponseType>;
    findByResultId: (resultId: number) => Observable<EntityResponseType>;
    getNumberOfAllowedComplaintsInCourse: (courseId: number) => Observable<number>;
}
@Injectable({ providedIn: 'root' })
export class ComplaintService implements IComplaintService {
    private resourceUrl = SERVER_API_URL + 'api/complaints';

    constructor(private http: HttpClient) {}

    create(complaint: Complaint): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(complaint);
        return this.http.post<Complaint>(this.resourceUrl, copy, { observe: 'response' }).map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Complaint>(`${this.resourceUrl}/${id}`, { observe: 'response' }).map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findByResultId(resultId: number): Observable<EntityResponseType> {
        return this.http.get<Complaint>(`${this.resourceUrl}/result/${resultId}`, { observe: 'response' }).map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    getForTutor(exerciseId: number): Observable<EntityResponseTypeArray> {
        return this.http
            .get<Complaint[]>(`${this.resourceUrl}/for-tutor-dashboard/${exerciseId}`, { observe: 'response' })
            .map((res: EntityResponseTypeArray) => this.convertDateFromServerArray(res));
    }

    getNumberOfAllowedComplaintsInCourse(courseId: number): Observable<number> {
        return this.http.get<number>(SERVER_API_URL + `api/${courseId}/allowed-complaints`);
    }

    updateComplaint(complaint: Complaint): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(complaint);
        return this.http.put<Complaint>(`${this.resourceUrl}/${complaint.id}`, copy, { observe: 'response' }).map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findAllByTutorIdForCourseId(tutorId: number, courseId: number): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?courseId=${courseId}&tutorId=${tutorId}`;

        return this.requestComplaintsFromUrl(url);
    }

    findAllByTutorIdForExerciseId(tutorId: number, exerciseId: number): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?exerciseId=${exerciseId}&tutorId=${tutorId}`;

        return this.requestComplaintsFromUrl(url);
    }

    findAllByCourseId(courseId: number): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?courseId=${courseId}`;

        return this.requestComplaintsFromUrl(url);
    }

    findAllByExerciseId(exerciseId: number): Observable<EntityResponseTypeArray> {
        const url = `${this.resourceUrl}?exerciseId=${exerciseId}`;

        return this.requestComplaintsFromUrl(url);
    }

    private requestComplaintsFromUrl(url: string): Observable<EntityResponseTypeArray> {
        return this.http.get<Complaint[]>(url, { observe: 'response' }).map((res: EntityResponseTypeArray) => this.convertDateFromServerArray(res));
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
