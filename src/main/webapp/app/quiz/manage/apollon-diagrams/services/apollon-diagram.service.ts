import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { UMLDiagramType } from '@ls1intum/apollon';
import { createRequestOption } from 'app/shared/util/request.util';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';

export type EntityResponseType = HttpResponse<ApollonDiagram>;

/**
 * DTO for creating and updating ApollonDiagrams.
 */
export interface ApollonDiagramUpdateDTO {
    id?: number;
    title?: string;
    jsonRepresentation?: string;
    diagramType?: UMLDiagramType;
    courseId: number;
}

@Injectable({ providedIn: 'root' })
export class ApollonDiagramService {
    private http = inject(HttpClient);
    private entityTitleService = inject(EntityTitleService);

    private resourceUrl = 'api/modeling';

    /**
     * Creates diagram.
     * @param apollonDiagram - apollonDiagram to be created.
     * @param courseId - id of the course.
     */
    create(apollonDiagram: ApollonDiagram, courseId: number): Observable<EntityResponseType> {
        const dto = this.toDTO(apollonDiagram, courseId);
        return this.http.post<ApollonDiagram>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams`, dto, { observe: 'response' });
    }

    /**
     * Updates diagram.
     * @param apollonDiagram - apollonDiagram to be updated.
     * @param courseId - id of the course.
     */
    update(apollonDiagram: ApollonDiagram, courseId: number): Observable<EntityResponseType> {
        const dto = this.toDTO(apollonDiagram, courseId);
        return this.http.put<ApollonDiagram>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams`, dto, { observe: 'response' });
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

    private toDTO(apollonDiagram: ApollonDiagram, courseId: number): ApollonDiagramUpdateDTO {
        return {
            id: apollonDiagram.id,
            title: apollonDiagram.title,
            jsonRepresentation: apollonDiagram.jsonRepresentation,
            diagramType: apollonDiagram.diagramType,
            courseId: courseId,
        };
    }

    private sendTitlesToEntityTitleService(diagram: ApollonDiagram | undefined | null) {
        this.entityTitleService.setTitle(EntityType.DIAGRAM, [diagram?.id], diagram?.title);
    }
}
