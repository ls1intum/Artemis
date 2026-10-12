import { HttpClient, HttpResponse } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { convertDateFromClient, convertDateFromServer } from 'app/foundation/util/date.utils';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import {
    PresentationAssessment,
    PresentationAssessmentInstance,
    PresentationAssessmentInstanceRequest,
    PresentationAssessmentInstancesCreate,
    PresentationAssessmentStatistics,
    PresentationAssessmentStudentRow,
    PresentationAssessmentStudentRowsRequest,
} from 'app/presentation/shared/entities/presentation-assessment.model';
import { createRequestOption } from 'app/foundation/util/request.util';
type EntityResponseType = HttpResponse<PresentationAssessment>;
type EntityArrayResponseType = HttpResponse<PresentationAssessment[]>;
type PresentationAssessmentInstanceRequestRest = Omit<PresentationAssessmentInstanceRequest, 'presentationDate'> & { presentationDate?: string };

@Service()
export class PresentationAssessmentService {
    private readonly http = inject(HttpClient);

    findAllByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.http.get<PresentationAssessment[]>(`api/assessment/courses/${courseId}/presentation-assessments`, { observe: 'response' });
    }

    findStudentRows(courseId: number, request: PresentationAssessmentStudentRowsRequest): Observable<HttpResponse<PresentationAssessmentStudentRow[]>> {
        const params = createRequestOption(Object.fromEntries(Object.entries(request).filter(([, value]) => value !== undefined)));

        return this.http
            .get<PresentationAssessmentStudentRow[]>(`api/assessment/courses/${courseId}/presentation-assessments/student-rows`, {
                params,
                observe: 'response',
            })
            .pipe(
                map((res) => {
                    res.body?.forEach((row) => {
                        row.instance.presentationDate = convertDateFromServer(row.instance.presentationDate);
                    });
                    return res;
                }),
            );
    }

    getStatistics(courseId: number): Observable<HttpResponse<PresentationAssessmentStatistics>> {
        return this.http.get<PresentationAssessmentStatistics>(`api/assessment/courses/${courseId}/presentation-assessments/statistics`, { observe: 'response' });
    }

    create(courseId: number, presentationAssessment: PresentationAssessment): Observable<EntityResponseType> {
        return this.http.post<PresentationAssessment>(`api/assessment/courses/${courseId}/presentation-assessments`, presentationAssessment, { observe: 'response' });
    }

    update(courseId: number, presentationAssessment: PresentationAssessment): Observable<EntityResponseType> {
        return this.http.put<PresentationAssessment>(`api/assessment/courses/${courseId}/presentation-assessments/${presentationAssessment.id}`, presentationAssessment, {
            observe: 'response',
        });
    }

    delete(courseId: number, presentationAssessmentId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`api/assessment/courses/${courseId}/presentation-assessments/${presentationAssessmentId}`, { observe: 'response' });
    }

    updateInstance(courseId: number, presentationAssessmentId: number, instance: PresentationAssessmentInstanceRequest): Observable<HttpResponse<PresentationAssessmentInstance>> {
        return this.http
            .put<PresentationAssessmentInstance>(
                `api/assessment/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/instances/${instance.id}`,
                this.convertInstanceDateFromClient(instance),
                { observe: 'response' },
            )
            .pipe(map((res) => this.convertInstanceResponseFromServer(res)));
    }

    saveInstances(courseId: number, presentationAssessmentId: number, request: PresentationAssessmentInstancesCreate): Observable<HttpResponse<PresentationAssessmentInstance[]>> {
        return this.http
            .post<PresentationAssessmentInstance[]>(
                `api/assessment/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/instances`,
                cloneWith(request, { presentationDate: convertDateFromClient(request.presentationDate) }),
                { observe: 'response' },
            )
            .pipe(map((res) => this.convertInstanceArrayResponseFromServer(res)));
    }

    deleteInstance(courseId: number, presentationAssessmentId: number, instanceId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`api/assessment/courses/${courseId}/presentation-assessments/${presentationAssessmentId}/instances/${instanceId}`, {
            observe: 'response',
        });
    }

    private convertInstanceDateFromClient(instance: PresentationAssessmentInstanceRequest): PresentationAssessmentInstanceRequestRest {
        return cloneWith(instance, { presentationDate: convertDateFromClient(instance.presentationDate) });
    }

    private convertInstanceResponseFromServer(res: HttpResponse<PresentationAssessmentInstance>): HttpResponse<PresentationAssessmentInstance> {
        if (res.body) {
            res.body.presentationDate = convertDateFromServer(res.body.presentationDate);
        }
        return res;
    }

    private convertInstanceArrayResponseFromServer(res: HttpResponse<PresentationAssessmentInstance[]>): HttpResponse<PresentationAssessmentInstance[]> {
        res.body?.forEach((instance) => (instance.presentationDate = convertDateFromServer(instance.presentationDate)));
        return res;
    }
}
