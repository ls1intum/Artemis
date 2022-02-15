import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { createRequestOption } from 'app/shared/util/request.util';

export type EntityResponseType = HttpResponse<ApollonDiagram>;

@Injectable({ providedIn: 'root' })
export class ApollonDiagramService {
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

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
        return this.http.get<ApollonDiagram>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams/${diagramId}`, { observe: 'response' });
    }

    /**
     * Fetches the title of the diagram with the given id
     *
     * @param diagramId the id of the diagram
     * @return the title of the diagram in an HttpResponse, or an HttpErrorResponse on error
     */
    getTitle(diagramId: number): Observable<HttpResponse<string>> {
        return this.http.get(`${this.resourceUrl}/apollon-diagrams/${diagramId}/title`, { observe: 'response', responseType: 'text' });
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
        return this.http.get<ApollonDiagram[]>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams`, { params: options, observe: 'response' });
    }

    private convert(apollonDiagram: ApollonDiagram): ApollonDiagram {
        return Object.assign({}, apollonDiagram);
    }
}
