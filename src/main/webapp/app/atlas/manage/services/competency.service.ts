import { HttpResponse } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable, defer } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { toCompetency, toCompetencyWithTailRelation, toCourseCompetencyRequestDTO } from 'app/atlas/shared/dto/course-competency-response.dto';
import { Competency, CompetencyWithTailRelationDTO, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { CompetencyApi } from 'app/openapi/api/competency-api';
import { CompetencyImportOptions } from 'app/openapi/model/competency-import-options';
import { CompetencyWithTailRelation } from 'app/openapi/model/competency-with-tail-relation';
import { CourseCompetencyResponse } from 'app/openapi/model/course-competency-response';

@Service()
export class CompetencyService extends CourseCompetencyService {
    private readonly competencyApi = inject(CompetencyApi);

    override getAllForCourse(courseId: number): Observable<HttpResponse<Competency[]>> {
        return this.competencyApi.getCompetenciesWithProgress(courseId).pipe(
            // Preserve the response shape shared with CourseCompetencyService for body-only operations.
            map((body) => new HttpResponse({ body: body?.map((dto) => this.hydrate(dto)) ?? [] })),
            tap((response) => response.body?.forEach((value) => this.sendTitlesToEntityTitleService(value))),
        );
    }

    override findById(competencyId: number, courseId: number): Observable<HttpResponse<Competency>> {
        return this.competencyApi.getCompetency(competencyId, courseId).pipe(
            map((body) => new HttpResponse({ body: body ? this.hydrate(body) : null })),
            tap((response) => this.sendTitlesToEntityTitleService(response.body)),
        );
    }

    create(competency: Competency, courseId: number): Observable<HttpResponse<Competency>> {
        return defer(() => this.competencyApi.createCompetency(courseId, toCourseCompetencyRequestDTO(competency))).pipe(
            map((response) => response.clone({ body: response.body ? this.hydrate(response.body) : null })),
        );
    }

    /** Creates a competency through the server-owned Hyperion checklist provenance route. */
    createFromHyperionChecklist(competency: Competency, courseId: number): Observable<HttpResponse<Competency>> {
        return defer(() => this.competencyApi.createCompetencyGeneratedFromHyperionChecklist(courseId, toCourseCompetencyRequestDTO(competency))).pipe(
            map((response) => response.clone({ body: response.body ? this.hydrate(response.body) : null })),
        );
    }

    createBulkFromCourseDescription(competencies: Competency[], courseId: number): Observable<HttpResponse<Competency[]>> {
        return defer(() => this.competencyApi.createCompetenciesGeneratedFromDescription(courseId, competencies.map(toCourseCompetencyRequestDTO))).pipe(
            map((response) => response.clone({ body: response.body?.map((dto) => this.hydrate(dto)) ?? [] })),
        );
    }

    importBulk(courseCompetencies: CourseCompetency[], courseId: number, importRelations: boolean) {
        const payload: CompetencyImportOptions = {
            competencyIds: courseCompetencies.map((competency) => competency.id).filter((id): id is number => id !== undefined),
            importRelations,
            importExercises: false,
            importLectures: false,
        };
        return this.competencyApi.importCompetencies(courseId, payload).pipe(map((response) => this.mapImportResponse(response)));
    }

    override importAll(courseId: number, sourceCourseId: number, importRelations: boolean) {
        const payload: CompetencyImportOptions = {
            competencyIds: [],
            sourceCourseId,
            importRelations,
            importExercises: false,
            importLectures: false,
        };
        return this.competencyApi.importAllCompetenciesFromCourse(courseId, payload).pipe(map((response) => this.mapImportResponse(response)));
    }

    importStandardizedCompetencies(competencyIdsToImport: number[], courseId: number) {
        return this.competencyApi.importStandardizedCompetencies(courseId, competencyIdsToImport);
    }

    update(competency: Competency, courseId: number): Observable<HttpResponse<Competency>> {
        return defer(() => this.competencyApi.updateCompetency(courseId, toCourseCompetencyRequestDTO(competency))).pipe(
            map((body) => new HttpResponse({ body: body ? this.hydrate(body) : null })),
        );
    }

    delete(competencyId: number, courseId: number) {
        return this.competencyApi.deleteCompetency(competencyId, courseId);
    }

    private hydrate(dto: CourseCompetencyResponse): Competency {
        const competency = toCompetency(dto);
        this.postProcessCompetency(competency);
        return competency;
    }

    private mapImportResponse(response: HttpResponse<CompetencyWithTailRelation[]>): HttpResponse<CompetencyWithTailRelationDTO[]> {
        return response.clone({ body: response.body?.map((dto) => toCompetencyWithTailRelation(dto, (competency) => this.hydrate(competency))) ?? [] });
    }
}
