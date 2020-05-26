import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { createRequestOption } from 'app/shared/util/request-util';

export type EntityResponseType = HttpResponse<ApollonDiagram>;

@Injectable({ providedIn: 'root' })
export class ApollonDiagramService {
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    /**
     * Creates diagram.
     * @param apollonDiagram - apollonDiagram to be created.
     */
    create(apollonDiagram: ApollonDiagram, courseId: number): Observable<EntityResponseType> {
        const copy = this.convert(apollonDiagram);
        return this.http
            .post<ApollonDiagram>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams`, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    /**
     * Updates diagram.
     * @param apollonDiagram - apollonDiagram to be updated.
     */
    update(apollonDiagram: ApollonDiagram, courseId: number): Observable<EntityResponseType> {
        const copy = this.convert(apollonDiagram);
        return this.http
            .put<ApollonDiagram>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams`, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    /**
     * Finds diagram.
     * @param id - id of diagram to be found.
     */
    find(id: number, courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ApollonDiagram>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    /**
     * Queries for all diagrams with option req.
     * @param req? - options of the query.
     */
    query(req?: any): Observable<HttpResponse<ApollonDiagram[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<ApollonDiagram[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<ApollonDiagram[]>) => this.convertArrayResponse(res));
    }

    /**
     * Deletes diagram with that id.
     * @param id - id of diagram to be deleted.
     */
    delete(id: number, courseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams/${id}`, { observe: 'response' });
    }

    /**
     * Gets all apollon diagrams that belong to the course with the id courseId.
     */
    getDiagramsByCourse(courseId: number): Observable<HttpResponse<ApollonDiagram[]>> {
        const options = createRequestOption(courseId);
        return this.http
            .get<ApollonDiagram[]>(`${this.resourceUrl}/course/${courseId}/apollon-diagrams`, { params: options, observe: 'response' })
            .map((res: HttpResponse<ApollonDiagram[]>) => this.convertArrayResponse(res));
    }

    /**
     * Converts JSON object within HttpResponse to ApollonDiagram and returns it within an HttpResponse.
     */
    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ApollonDiagram = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    /**
     * Converts JSON object array within HttpResponse to ApollonDiagram array and returns it within an HttpResponse.
     */
    private convertArrayResponse(res: HttpResponse<ApollonDiagram[]>): HttpResponse<ApollonDiagram[]> {
        const jsonResponse: ApollonDiagram[] = res.body!;
        const body: ApollonDiagram[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Converts a returned JSON object to ApollonDiagram.
     */
    private convertItemFromServer(apollonDiagram: ApollonDiagram): ApollonDiagram {
        const copy: ApollonDiagram = Object.assign({}, apollonDiagram);
        return copy;
    }

    /**
     * Converts a ApollonDiagram to a JSON which can be sent to the server.
     */
    private convert(apollonDiagram: ApollonDiagram): ApollonDiagram {
        const copy: ApollonDiagram = Object.assign({}, apollonDiagram);
        return copy;
    }
}
