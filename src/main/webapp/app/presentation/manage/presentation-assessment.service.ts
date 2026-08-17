import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { User } from 'app/account/user/user.model';
import { convertDateFromClient, convertDateFromServer } from 'app/foundation/util/date.utils';
import { PresentationAssessment, PresentationAssessmentInstance } from 'app/presentation/shared/entities/presentation-assessment.model';

type EntityResponseType = HttpResponse<PresentationAssessment>;
type EntityArrayResponseType = HttpResponse<PresentationAssessment[]>;
type PresentationAssessmentRest = Omit<PresentationAssessment, 'presentationDate'> & { presentationDate?: string };
type PresentationAssessmentInstanceRest = Omit<PresentationAssessmentInstance, 'presentationDate'> & { presentationDate?: string };

@Injectable({ providedIn: 'root' })
export class PresentationAssessmentService {
    private readonly http = inject(HttpClient);

    findAllByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<PresentationAssessment[]>(`api/presentation/courses/${courseId}/presentation-assessments`, { observe: 'response' })
            .pipe(map((res) => this.convertDateArrayFromServer(res)));
    }

    create(courseId: number, presentationAssessment: PresentationAssessment): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(presentationAssessment);
        return this.http
            .post<PresentationAssessment>(`api/presentation/courses/${courseId}/presentation-assessments`, copy, { observe: 'response' })
            .pipe(map((res) => this.convertDateResponseFromServer(res)));
    }

    update(courseId: number, presentationAssessment: PresentationAssessment): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(presentationAssessment);
        return this.http
            .put<PresentationAssessment>(`api/presentation/courses/${courseId}/presentation-assessments/${presentationAssessment.id}`, copy, { observe: 'response' })
            .pipe(map((res) => this.convertDateResponseFromServer(res)));
    }

    delete(courseId: number, presentationAssessmentId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`api/presentation/courses/${courseId}/presentation-assessments/${presentationAssessmentId}`, { observe: 'response' });
    }

    createInstance(courseId: number, presentationAssessmentId: number, instance: PresentationAssessmentInstance): Observable<HttpResponse<PresentationAssessmentInstance>> {
        return this.http
            .post<PresentationAssessmentInstance>(
                `api/presentation/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/instances`,
                this.convertInstanceDateFromClient(instance),
                { observe: 'response' },
            )
            .pipe(map((res) => this.convertInstanceResponseFromServer(res)));
    }

    updateInstance(courseId: number, presentationAssessmentId: number, instance: PresentationAssessmentInstance): Observable<HttpResponse<PresentationAssessmentInstance>> {
        return this.http
            .put<PresentationAssessmentInstance>(
                `api/presentation/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/instances/${instance.id}`,
                this.convertInstanceDateFromClient(instance),
                { observe: 'response' },
            )
            .pipe(map((res) => this.convertInstanceResponseFromServer(res)));
    }

    deleteInstance(courseId: number, presentationAssessmentId: number, instanceId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`api/presentation/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/instances/${instanceId}`, {
            observe: 'response',
        });
    }

    findStudents(courseId: number, presentationAssessmentId: number): Observable<HttpResponse<User[]>> {
        return this.http.get<User[]>(`api/presentation/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/students`, { observe: 'response' });
    }

    findCourseStudents(courseId: number): Observable<HttpResponse<User[]>> {
        return this.http.get<User[]>(`api/course/courses/${courseId}/students`, { observe: 'response' });
    }

    addStudent(courseId: number, presentationAssessmentId: number, studentLogin: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(
            `api/presentation/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/students/${studentLogin}`,
            {},
            { observe: 'response' },
        );
    }

    removeStudent(courseId: number, presentationAssessmentId: number, studentLogin: string): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`api/presentation/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/students/${studentLogin}`, {
            observe: 'response',
        });
    }

    private convertDateFromClient(presentationAssessment: PresentationAssessment): PresentationAssessmentRest {
        const copy: PresentationAssessmentRest = {
            id: presentationAssessment.id,
            title: presentationAssessment.title,
            description: presentationAssessment.description,
            maxPoints: presentationAssessment.maxPoints,
            resultPoints: presentationAssessment.resultPoints,
            courseId: presentationAssessment.courseId,
            studentLogins: presentationAssessment.studentLogins,
            exerciseId: presentationAssessment.exerciseId,
        };
        copy.presentationDate = convertDateFromClient(presentationAssessment.presentationDate);
        return copy;
    }

    private convertDateResponseFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.presentationDate = convertDateFromServer(res.body.presentationDate);
            res.body.instances?.forEach((instance) => (instance.presentationDate = convertDateFromServer(instance.presentationDate)));
        }
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body?.forEach((presentationAssessment) => {
            presentationAssessment.presentationDate = convertDateFromServer(presentationAssessment.presentationDate);
            presentationAssessment.instances?.forEach((instance) => (instance.presentationDate = convertDateFromServer(instance.presentationDate)));
        });
        return res;
    }

    private convertInstanceDateFromClient(instance: PresentationAssessmentInstance): PresentationAssessmentInstanceRest {
        return { ...instance, presentationDate: convertDateFromClient(instance.presentationDate) };
    }

    private convertInstanceResponseFromServer(res: HttpResponse<PresentationAssessmentInstance>): HttpResponse<PresentationAssessmentInstance> {
        if (res.body) {
            res.body.presentationDate = convertDateFromServer(res.body.presentationDate);
        }
        return res;
    }
}
