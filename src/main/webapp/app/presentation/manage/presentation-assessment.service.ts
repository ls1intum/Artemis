import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { User } from 'app/account/user/user.model';
import { convertDateFromClient, convertDateFromServer } from 'app/foundation/util/date.utils';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';

type EntityResponseType = HttpResponse<PresentationAssessment>;
type EntityArrayResponseType = HttpResponse<PresentationAssessment[]>;
type PresentationAssessmentRest = Omit<PresentationAssessment, 'presentationDate'> & { presentationDate?: string };

@Injectable({ providedIn: 'root' })
export class PresentationAssessmentService {
    private readonly http = inject(HttpClient);

    findAllByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<PresentationAssessment[]>(`api/courses/${courseId}/presentation-assessments`, { observe: 'response' })
            .pipe(map((res) => this.convertDateArrayFromServer(res)));
    }

    create(courseId: number, presentationAssessment: PresentationAssessment): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(presentationAssessment);
        return this.http
            .post<PresentationAssessment>(`api/courses/${courseId}/presentation-assessments`, copy, { observe: 'response' })
            .pipe(map((res) => this.convertDateResponseFromServer(res)));
    }

    update(courseId: number, presentationAssessment: PresentationAssessment): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(presentationAssessment);
        return this.http
            .put<PresentationAssessment>(`api/courses/${courseId}/presentation-assessments/${presentationAssessment.id}`, copy, { observe: 'response' })
            .pipe(map((res) => this.convertDateResponseFromServer(res)));
    }

    delete(courseId: number, presentationAssessmentId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`api/courses/${courseId}/presentation-assessments/${presentationAssessmentId}`, { observe: 'response' });
    }

    findStudents(courseId: number, presentationAssessmentId: number): Observable<HttpResponse<User[]>> {
        return this.http.get<User[]>(`api/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/students`, { observe: 'response' });
    }

    addStudent(courseId: number, presentationAssessmentId: number, studentLogin: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`api/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/students/${studentLogin}`, {}, { observe: 'response' });
    }

    removeStudent(courseId: number, presentationAssessmentId: number, studentLogin: string): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`api/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/students/${studentLogin}`, { observe: 'response' });
    }

    private convertDateFromClient(presentationAssessment: PresentationAssessment): PresentationAssessmentRest {
        const copy: PresentationAssessmentRest = {
            id: presentationAssessment.id,
            title: presentationAssessment.title,
            description: presentationAssessment.description,
            maxPoints: presentationAssessment.maxPoints,
            resultPoints: presentationAssessment.resultPoints,
            courseId: presentationAssessment.courseId,
        };
        copy.presentationDate = convertDateFromClient(presentationAssessment.presentationDate);
        return copy;
    }

    private convertDateResponseFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.presentationDate = convertDateFromServer(res.body.presentationDate);
        }
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body?.forEach((presentationAssessment) => {
            presentationAssessment.presentationDate = convertDateFromServer(presentationAssessment.presentationDate);
        });
        return res;
    }
}
