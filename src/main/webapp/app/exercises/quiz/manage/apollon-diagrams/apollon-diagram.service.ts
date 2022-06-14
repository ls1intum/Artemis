import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';

export type EntityResponseType = HttpResponse<ApollonDiagram>;

@Injectable({ providedIn: 'root' })
export class ApollonDiagramService {
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient, private entityTitleService: EntityTitleService) {}

    /**
     * Creates diagram.
     * @param apollonDiagram - apollonDiagram to be created.
     * @param courseId - id of the course.
     */
    create(apollonDiagram: ApollonDiagram, courseId: number): Observable<EntityResponseType> {
        const copy = this.convert(apollonDiagram);
        return this.http.post<ApollonDiagram>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams`, copy, { observe: 'response' });
    }

    /**
     * Updates diagram.
     * @param apollonDiagram - apollonDiagram to be updated.
     * @param courseId - id of the course.
     */
    update(apollonDiagram: ApollonDiagram, courseId: number): Observable<EntityResponseType> {
        const copy = this.convert(apollonDiagram);
        return this.http.put<ApollonDiagram>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams`, copy, { observe: 'response' });
    }

    /**
     * Finds diagram.
     * @param diagramId - id of diagram to be found.
     * @param courseId - id of the course.
     */
    find(diagramId: number, courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ApollonDiagram>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams/${diagramId}`, { observe: 'response' })
            .pipe(tap((res) => this.sendTitlesToEntityTitleService(res?.body)));
    }

    /**
     * Deletes diagram with that id.
     * @param diagramId - id of diagram to be deleted.
     * @param courseId - id of the course.
     */
    delete(diagramId: number, courseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams/${diagramId}`, { observe: 'response' });
    }

    /**
     * Gets all apollon diagrams that belong to the course with the id courseId.
     */
    getDiagramsByCourse(courseId: number): Observable<HttpResponse<ApollonDiagram[]>> {
        const options = createRequestOption(courseId);
        return this.http
            .get<ApollonDiagram[]>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams`, { params: options, observe: 'response' })
            .pipe(tap((res) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
    }

    private convert(apollonDiagram: ApollonDiagram): ApollonDiagram {
        return Object.assign({}, apollonDiagram);
    }

    private sendTitlesToEntityTitleService(diagram: ApollonDiagram | undefined | null) {
        this.entityTitleService.setTitle(EntityType.DIAGRAM, [diagram?.id], diagram?.title);
    }
}
