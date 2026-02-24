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
import {
    CompetencyWithTailRelationResponseDTO,
    CourseCompetencyRequestDTO,
    CourseCompetencyResponseDTO,
    toCompetency,
    toCourseCompetencyRequestDTO,
} from 'app/atlas/shared/dto/course-competency-response.dto';
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
type EntityResponseDTOType = HttpResponse<CourseCompetencyResponseDTO>;
type EntityArrayResponseDTOType = HttpResponse<CourseCompetencyResponseDTO[]>;

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
        return this.httpClient.get<CourseCompetencyResponseDTO[]>(`${this.resourceURL}/courses/${courseId}/competencies`, { observe: 'response' }).pipe(
            map((res: EntityArrayResponseDTOType) => this.mapCompetencyArrayResponse(res)),
            tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
        );
    }

    findById(competencyId: number, courseId: number) {
        return this.httpClient.get<CourseCompetencyResponseDTO>(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseDTOType) => {
                const mapped = this.mapCompetencyResponse(res);
                this.sendTitlesToEntityTitleService(mapped?.body);
                return mapped;
            }),
        );
    }

    create(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const request: CourseCompetencyRequestDTO = toCourseCompetencyRequestDTO(competency);
        return this.httpClient
            .post<CourseCompetencyResponseDTO>(`${this.resourceURL}/courses/${courseId}/competencies`, request, { observe: 'response' })
            .pipe(map((res: EntityResponseDTOType) => this.mapCompetencyResponse(res)));
    }

    createBulk(competencies: Competency[], courseId: number) {
        const request = competencies.map((competency) => toCourseCompetencyRequestDTO(competency));
        return this.httpClient
            .post<CourseCompetencyResponseDTO[]>(`${this.resourceURL}/courses/${courseId}/competencies/bulk`, request, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseDTOType) => this.mapCompetencyArrayResponse(res)));
    }

    import(courseCompetency: CourseCompetency, courseId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<CourseCompetencyResponseDTO>(`${this.resourceURL}/courses/${courseId}/competencies/import`, courseCompetency.id, { observe: 'response' })
            .pipe(map((res: EntityResponseDTOType) => this.mapCompetencyResponse(res)));
    }

    importBulk(courseCompetencies: CourseCompetency[], courseId: number, importRelations: boolean) {
        const courseCompetencyIds = courseCompetencies.map((competency) => competency.id);
        const params = new HttpParams().set('importRelations', importRelations);
        return this.httpClient
            .post<Array<CompetencyWithTailRelationResponseDTO>>(
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
            )
            .pipe(map((res: HttpResponse<Array<CompetencyWithTailRelationResponseDTO>>) => this.mapCompetencyImportResponse(res)));
    }

    importAll(courseId: number, sourceCourseId: number, importRelations: boolean) {
        return this.httpClient
            .post<Array<CompetencyWithTailRelationResponseDTO>>(
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
            )
            .pipe(map((res: HttpResponse<Array<CompetencyWithTailRelationResponseDTO>>) => this.mapCompetencyImportResponse(res)));
    }

    importStandardizedCompetencies(competencyIdsToImport: number[], courseId: number) {
        return this.httpClient.post<Array<CompetencyImportResponseDTO>>(`${this.resourceURL}/courses/${courseId}/competencies/import-standardized`, competencyIdsToImport, {
            observe: 'response',
        });
    }

    update(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const request: CourseCompetencyRequestDTO = toCourseCompetencyRequestDTO(competency);
        return this.httpClient
            .put<CourseCompetencyResponseDTO>(`${this.resourceURL}/courses/${courseId}/competencies`, request, { observe: 'response' })
            .pipe(map((res: EntityResponseDTOType) => this.mapCompetencyResponse(res)));
    }

    delete(competencyId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}`, { observe: 'response' });
    }

    private mapCompetencyResponse(res: EntityResponseDTOType): EntityResponseType {
        const body = res.body ? toCompetency(res.body) : undefined;
        this.postProcessCompetency(body);
        return res.clone({ body });
    }

    private mapCompetencyArrayResponse(res: EntityArrayResponseDTOType): EntityArrayResponseType {
        const body = res.body ? res.body.map((dto) => toCompetency(dto)) : [];
        body.forEach((competency) => this.postProcessCompetency(competency));
        return res.clone({ body });
    }

    private mapCompetencyImportResponse(res: HttpResponse<Array<CompetencyWithTailRelationResponseDTO>>): HttpResponse<Array<CompetencyWithTailRelationDTO>> {
        const body =
            res.body?.map((dto) => {
                const mapped = new CompetencyWithTailRelationDTO();
                mapped.competency = dto.competency ? toCompetency(dto.competency) : undefined;
                this.postProcessCompetency(mapped.competency);
                mapped.tailRelations = dto.tailRelations;
                return mapped;
            }) ?? [];
        return res.clone({ body });
    }
}
