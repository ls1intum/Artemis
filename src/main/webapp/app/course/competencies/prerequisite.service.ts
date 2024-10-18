import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { CompetencyImportResponseDTO, CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyImportOptionsDTO } from 'app/entities/competency.model';
import { EntityTitleService } from 'app/shared/layouts/navbar/entity-title.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AccountService } from 'app/core/auth/account.service';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

type EntityResponseType = HttpResponse<Prerequisite>;
type EntityArrayResponseType = HttpResponse<Prerequisite[]>;

@Injectable({
    providedIn: 'root',
})
export class PrerequisiteService extends CourseCompetencyService {
    constructor(httpClient: HttpClient, entityTitleService: EntityTitleService, lectureUnitService: LectureUnitService, accountService: AccountService) {
        super(httpClient, entityTitleService, lectureUnitService, accountService);
    }

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<Prerequisite[]>(`${this.resourceURL}/courses/${courseId}/prerequisites`, { observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => this.convertArrayResponseDatesFromServer(res)),
            tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
        );
    }

    findById(prerequisiteId: number, courseId: number) {
        return this.httpClient.get<Prerequisite>(`${this.resourceURL}/courses/${courseId}/prerequisites/${prerequisiteId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                this.convertCompetencyResponseFromServer(res);
                this.sendTitlesToEntityTitleService(res?.body);
                return res;
            }),
        );
    }

    create(prerequisite: Prerequisite, courseId: number): Observable<EntityResponseType> {
        prerequisite.lectureUnitLinks?.forEach((link) => {
            link.lectureUnit = undefined;
            if (link.competency?.lectureUnitLinks) {
                link.competency.lectureUnitLinks = undefined;
            }
        });
        const copy = this.convertCompetencyFromClient(prerequisite);
        return this.httpClient.post<Prerequisite>(`${this.resourceURL}/courses/${courseId}/prerequisites`, copy, { observe: 'response' });
    }

    createBulk(prerequisites: Prerequisite[], courseId: number) {
        const copy = prerequisites.map((prerequisite) => this.convertCompetencyFromClient(prerequisite));
        return this.httpClient.post<Prerequisite[]>(`${this.resourceURL}/courses/${courseId}/prerequisites/bulk`, copy, { observe: 'response' });
    }

    import(courseCompetency: CourseCompetency, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post<Prerequisite>(`${this.resourceURL}/courses/${courseId}/prerequisites/import`, courseCompetency.id, { observe: 'response' });
    }

    importBulk(courseCompetencies: CourseCompetency[], courseId: number, importRelations: boolean) {
        const courseCompetencyIds = courseCompetencies.map((competency) => competency.id);
        return this.httpClient.post<Array<CompetencyWithTailRelationDTO>>(
            `${this.resourceURL}/courses/${courseId}/prerequisites/import/bulk`,
            {
                importExercises: false,
                importRelations: importRelations,
                sourceCourseId: courseId,
                importLectures: false,
                competencyIds: courseCompetencyIds,
            } as CourseCompetencyImportOptionsDTO,
            {
                observe: 'response',
            },
        );
    }

    importAll(courseId: number, sourceCourseId: number, importRelations: boolean) {
        return this.httpClient.post<Array<CompetencyWithTailRelationDTO>>(
            `${this.resourceURL}/courses/${courseId}/prerequisites/import-all`,
            {
                importLectures: false,
                importRelations: importRelations,
                importExercises: false,
                sourceCourseId: sourceCourseId,
            } as CourseCompetencyImportOptionsDTO,
            {
                observe: 'response',
            },
        );
    }

    importStandardizedCompetencies(competencyIdsToImport: number[], courseId: number) {
        return this.httpClient.post<Array<CompetencyImportResponseDTO>>(`${this.resourceURL}/courses/${courseId}/prerequisites/import-standardized`, competencyIdsToImport, {
            observe: 'response',
        });
    }

    update(prerequisite: Prerequisite, courseId: number): Observable<EntityResponseType> {
        prerequisite.lectureUnitLinks?.forEach((link) => {
            link.lectureUnit = undefined;
            if (link.competency?.lectureUnitLinks) {
                link.competency.lectureUnitLinks = undefined;
            }
        });
        const copy = this.convertCompetencyFromClient(prerequisite);
        return this.httpClient.put(`${this.resourceURL}/courses/${courseId}/prerequisites`, copy, { observe: 'response' });
    }

    delete(prerequisiteId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/prerequisites/${prerequisiteId}`, { observe: 'response' });
    }
}
