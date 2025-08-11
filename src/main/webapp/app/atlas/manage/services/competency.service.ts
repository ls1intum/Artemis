import { Injectable } from '@angular/core';
import { HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
    Competency,
    CompetencyImportResponseDTO,
    CompetencyWithTailRelationDTO,
    CourseCompetency,
    CourseCompetencyImportOptionsDTO,
} from 'app/atlas/shared/entities/competency.model';
import { map, tap } from 'rxjs/operators';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';

export interface SuggestedCompetency extends Competency {
    isSuggested?: boolean;
}

export interface CompetencySuggestionRequest {
    description: string;
}

export interface CompetencySuggestionResponse {
    competencies: SuggestedCompetency[];
}

type EntityResponseType = HttpResponse<Competency>;
type EntityArrayResponseType = HttpResponse<Competency[]>;

@Injectable({
    providedIn: 'root',
})
export class CompetencyService extends CourseCompetencyService {
    /**
     * Get competency suggestions from AtlasML API
     * @param description The description to base suggestions on
     * @returns Observable of suggested competencies
     */
    getSuggestedCompetencies(description: string): Observable<HttpResponse<CompetencySuggestionResponse>> {
        const request: CompetencySuggestionRequest = { description };
        return this.httpClient.post<CompetencySuggestionResponse>(`${this.resourceURL}/competencies/suggest`, request, { observe: 'response' }).pipe(
            map((response: HttpResponse<CompetencySuggestionResponse>) => {
                if (response.body?.competencies) {
                    // Mark all competencies as suggested
                    response.body.competencies.forEach((competency) => {
                        competency.isSuggested = true;
                    });
                }
                return response;
            }),
        );
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
            `${this.resourceURL}/courses/${courseId}/competencies/import-all`,
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
