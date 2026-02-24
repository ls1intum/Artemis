import { HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { CompetencyImportResponseDTO, CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyImportOptionsDTO } from 'app/atlas/shared/entities/competency.model';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import {
    CompetencyWithTailRelationResponseDTO,
    CourseCompetencyRequestDTO,
    CourseCompetencyResponseDTO,
    toCourseCompetencyRequestDTO,
    toPrerequisite,
} from 'app/atlas/shared/dto/course-competency-response.dto';

type EntityResponseType = HttpResponse<Prerequisite>;
type EntityArrayResponseType = HttpResponse<Prerequisite[]>;
type EntityResponseDTOType = HttpResponse<CourseCompetencyResponseDTO>;
type EntityArrayResponseDTOType = HttpResponse<CourseCompetencyResponseDTO[]>;

@Injectable({
    providedIn: 'root',
})
export class PrerequisiteService extends CourseCompetencyService {
    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<CourseCompetencyResponseDTO[]>(`${this.resourceURL}/courses/${courseId}/prerequisites`, { observe: 'response' }).pipe(
            map((res: EntityArrayResponseDTOType) => this.mapPrerequisiteArrayResponse(res)),
            tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
        );
    }

    findById(prerequisiteId: number, courseId: number) {
        return this.httpClient.get<CourseCompetencyResponseDTO>(`${this.resourceURL}/courses/${courseId}/prerequisites/${prerequisiteId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseDTOType) => {
                const mapped = this.mapPrerequisiteResponse(res);
                this.sendTitlesToEntityTitleService(mapped?.body);
                return mapped;
            }),
        );
    }

    create(prerequisite: Prerequisite, courseId: number): Observable<EntityResponseType> {
        const request: CourseCompetencyRequestDTO = toCourseCompetencyRequestDTO(prerequisite);
        return this.httpClient
            .post<CourseCompetencyResponseDTO>(`${this.resourceURL}/courses/${courseId}/prerequisites`, request, { observe: 'response' })
            .pipe(map((res: EntityResponseDTOType) => this.mapPrerequisiteResponse(res)));
    }

    createBulk(prerequisites: Prerequisite[], courseId: number) {
        const request = prerequisites.map((prerequisite) => toCourseCompetencyRequestDTO(prerequisite));
        return this.httpClient
            .post<CourseCompetencyResponseDTO[]>(`${this.resourceURL}/courses/${courseId}/prerequisites/bulk`, request, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseDTOType) => this.mapPrerequisiteArrayResponse(res)));
    }

    import(courseCompetency: CourseCompetency, courseId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<CourseCompetencyResponseDTO>(`${this.resourceURL}/courses/${courseId}/prerequisites/import`, courseCompetency.id, { observe: 'response' })
            .pipe(map((res: EntityResponseDTOType) => this.mapPrerequisiteResponse(res)));
    }

    importBulk(courseCompetencies: CourseCompetency[], courseId: number, importRelations: boolean) {
        const courseCompetencyIds = courseCompetencies.map((competency) => competency.id);
        return this.httpClient
            .post<Array<CompetencyWithTailRelationResponseDTO>>(
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
            )
            .pipe(map((res: HttpResponse<Array<CompetencyWithTailRelationResponseDTO>>) => this.mapPrerequisiteImportResponse(res)));
    }

    importAll(courseId: number, sourceCourseId: number, importRelations: boolean) {
        return this.httpClient
            .post<Array<CompetencyWithTailRelationResponseDTO>>(
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
            )
            .pipe(map((res: HttpResponse<Array<CompetencyWithTailRelationResponseDTO>>) => this.mapPrerequisiteImportResponse(res)));
    }

    importStandardizedCompetencies(competencyIdsToImport: number[], courseId: number) {
        return this.httpClient.post<Array<CompetencyImportResponseDTO>>(`${this.resourceURL}/courses/${courseId}/prerequisites/import-standardized`, competencyIdsToImport, {
            observe: 'response',
        });
    }

    update(prerequisite: Prerequisite, courseId: number): Observable<EntityResponseType> {
        const request: CourseCompetencyRequestDTO = toCourseCompetencyRequestDTO(prerequisite);
        return this.httpClient
            .put<CourseCompetencyResponseDTO>(`${this.resourceURL}/courses/${courseId}/prerequisites`, request, { observe: 'response' })
            .pipe(map((res: EntityResponseDTOType) => this.mapPrerequisiteResponse(res)));
    }

    delete(prerequisiteId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/prerequisites/${prerequisiteId}`, { observe: 'response' });
    }

    private mapPrerequisiteResponse(res: EntityResponseDTOType): EntityResponseType {
        const body = res.body ? toPrerequisite(res.body) : undefined;
        this.postProcessCompetency(body);
        return res.clone({ body });
    }

    private mapPrerequisiteArrayResponse(res: EntityArrayResponseDTOType): EntityArrayResponseType {
        const body = res.body ? res.body.map((dto) => toPrerequisite(dto)) : [];
        body.forEach((prerequisite) => {
            this.postProcessCompetency(prerequisite);
        });
        return res.clone({ body });
    }

    private mapPrerequisiteImportResponse(res: HttpResponse<Array<CompetencyWithTailRelationResponseDTO>>): HttpResponse<Array<CompetencyWithTailRelationDTO>> {
        const body =
            res.body?.map((dto) => {
                const mapped = new CompetencyWithTailRelationDTO();
                mapped.competency = dto.competency ? toPrerequisite(dto.competency) : undefined;
                this.postProcessCompetency(mapped.competency);
                mapped.tailRelations = dto.tailRelations;
                return mapped;
            }) ?? [];
        return res.clone({ body });
    }
}
