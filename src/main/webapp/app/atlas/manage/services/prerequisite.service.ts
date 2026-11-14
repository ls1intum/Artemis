import { HttpResponse } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable, defer } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { toCompetencyWithTailRelation, toCourseCompetencyRequestDTO, toPrerequisite } from 'app/atlas/shared/dto/course-competency-response.dto';
import { CompetencyWithTailRelationDTO, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { PrerequisiteApi } from 'app/openapi/api/prerequisite-api';
import { CompetencyImportOptions } from 'app/openapi/model/competency-import-options';
import { CompetencyWithTailRelation } from 'app/openapi/model/competency-with-tail-relation';
import { CourseCompetencyResponse } from 'app/openapi/model/course-competency-response';

@Service()
export class PrerequisiteService extends CourseCompetencyService {
    private readonly prerequisiteApi = inject(PrerequisiteApi);

    override getAllForCourse(courseId: number): Observable<HttpResponse<Prerequisite[]>> {
        return this.prerequisiteApi.getPrerequisitesWithProgress(courseId).pipe(
            // Preserve the response shape shared with CourseCompetencyService for body-only operations.
            map((body) => new HttpResponse({ body: body?.map((dto) => this.hydrate(dto)) ?? [] })),
            tap((response) => response.body?.forEach((value) => this.sendTitlesToEntityTitleService(value))),
        );
    }

    override findById(prerequisiteId: number, courseId: number): Observable<HttpResponse<Prerequisite>> {
        return this.prerequisiteApi.getPrerequisite(prerequisiteId, courseId).pipe(
            map((body) => new HttpResponse({ body: body ? this.hydrate(body) : null })),
            tap((response) => this.sendTitlesToEntityTitleService(response.body)),
        );
    }

    create(prerequisite: Prerequisite, courseId: number): Observable<HttpResponse<Prerequisite>> {
        return defer(() => this.prerequisiteApi.createPrerequisite(courseId, toCourseCompetencyRequestDTO(prerequisite))).pipe(
            map((response) => response.clone({ body: response.body ? this.hydrate(response.body) : null })),
        );
    }

    importBulk(courseCompetencies: CourseCompetency[], courseId: number, importRelations: boolean) {
        const payload: CompetencyImportOptions = {
            competencyIds: courseCompetencies.map((competency) => competency.id).filter((id): id is number => id !== undefined),
            importRelations,
            importExercises: false,
            importLectures: false,
        };
        return this.prerequisiteApi.importPrerequisites(courseId, payload).pipe(map((response) => this.mapImportResponse(response)));
    }

    override importAll(courseId: number, sourceCourseId: number, importRelations: boolean) {
        const payload: CompetencyImportOptions = {
            competencyIds: [],
            sourceCourseId,
            importRelations,
            importExercises: false,
            importLectures: false,
        };
        return this.prerequisiteApi.importAllPrerequisitesFromCourse(courseId, payload).pipe(map((response) => this.mapImportResponse(response)));
    }

    importStandardizedCompetencies(competencyIdsToImport: number[], courseId: number) {
        return this.prerequisiteApi.importStandardizedPrerequisites(courseId, competencyIdsToImport);
    }

    update(prerequisite: Prerequisite, courseId: number): Observable<HttpResponse<Prerequisite>> {
        return defer(() => this.prerequisiteApi.updatePrerequisite(courseId, toCourseCompetencyRequestDTO(prerequisite))).pipe(
            map((body) => new HttpResponse({ body: body ? this.hydrate(body) : null })),
        );
    }

    delete(prerequisiteId: number, courseId: number) {
        return this.prerequisiteApi.deletePrerequisite(prerequisiteId, courseId);
    }

    private hydrate(dto: CourseCompetencyResponse): Prerequisite {
        const prerequisite = toPrerequisite(dto);
        this.postProcessCompetency(prerequisite);
        return prerequisite;
    }

    private mapImportResponse(response: HttpResponse<CompetencyWithTailRelation[]>): HttpResponse<CompetencyWithTailRelationDTO[]> {
        return response.clone({ body: response.body?.map((dto) => toCompetencyWithTailRelation(dto, (competency) => this.hydrate(competency))) ?? [] });
    }
}
