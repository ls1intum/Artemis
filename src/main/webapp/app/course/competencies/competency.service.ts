import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Competency, CompetencyImportResponseDTO, CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyImportOptionsDTO } from 'app/entities/competency.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { map, tap } from 'rxjs/operators';
import { EntityTitleService } from 'app/shared/layouts/navbar/entity-title.service';
import { AccountService } from 'app/core/auth/account.service';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

type EntityResponseType = HttpResponse<Competency>;
type EntityArrayResponseType = HttpResponse<Competency[]>;

@Injectable({
    providedIn: 'root',
})
export class CompetencyService extends CourseCompetencyService {
    constructor(httpClient: HttpClient, entityTitleService: EntityTitleService, lectureUnitService: LectureUnitService, accountService: AccountService) {
        super(httpClient, entityTitleService, lectureUnitService, accountService);
    }

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<Competency[]>(`${this.resourceURL}/courses/${courseId}/competencies`, { observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => this.convertArrayResponseDatesFromServer(res)),
            tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
        );
    }

    findById(competencyId: number, courseId: number) {
        return this.httpClient.get<Competency>(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                this.convertCompetencyResponseFromServer(res);
                this.sendTitlesToEntityTitleService(res?.body);
                return res;
            }),
        );
    }

    create(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertCompetencyFromClient(competency);
        return this.httpClient.post<Competency>(`${this.resourceURL}/courses/${courseId}/competencies`, copy, { observe: 'response' });
    }

    createBulk(competencies: Competency[], courseId: number) {
        const copy = competencies.map((competency) => this.convertCompetencyFromClient(competency));
        return this.httpClient.post<Competency[]>(`${this.resourceURL}/courses/${courseId}/competencies/bulk`, copy, { observe: 'response' });
    }

    import(courseCompetency: CourseCompetency, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post<Competency>(`${this.resourceURL}/courses/${courseId}/competencies/import`, courseCompetency.id, { observe: 'response' });
    }

    importBulk(courseCompetencies: CourseCompetency[], courseId: number, importRelations: boolean) {
        const courseCompetencyIds = courseCompetencies.map((competency) => competency.id);
        const params = new HttpParams().set('importRelations', importRelations);
        return this.httpClient.post<Array<CompetencyWithTailRelationDTO>>(
            `${this.resourceURL}/courses/${courseId}/competencies/import/bulk`,
            {
                sourceCourseId: courseId,
                importRelations: importRelations,
                importExercises: false,
                importLectures: false,
                competencyIds: courseCompetencyIds,
            } as CourseCompetencyImportOptionsDTO,
            {
                params: params,
                observe: 'response',
            },
        );
    }

    importAll(courseId: number, sourceCourseId: number, importRelations: boolean) {
        return this.httpClient.post<Array<CompetencyWithTailRelationDTO>>(
            `${this.resourceURL}/courses/${courseId}/competencies/import-all/${sourceCourseId}`,
            {
                importExercises: false,
                importRelations: importRelations,
                sourceCourseId: sourceCourseId,
                importLectures: false,
            } as CourseCompetencyImportOptionsDTO,
            {
                observe: 'response',
            },
        );
    }

    importStandardizedCompetencies(competencyIdsToImport: number[], courseId: number) {
        return this.httpClient.post<Array<CompetencyImportResponseDTO>>(`${this.resourceURL}/courses/${courseId}/competencies/import-standardized`, competencyIdsToImport, {
            observe: 'response',
        });
    }

    update(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertCompetencyFromClient(competency);
        return this.httpClient.put(`${this.resourceURL}/courses/${courseId}/competencies`, copy, { observe: 'response' });
    }

    delete(competencyId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}`, { observe: 'response' });
    }
}
