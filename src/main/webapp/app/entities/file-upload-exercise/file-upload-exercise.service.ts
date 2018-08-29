import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { FileUploadExercise } from './file-upload-exercise.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<FileUploadExercise>;

@Injectable()
export class FileUploadExerciseService {

    private resourceUrl =  SERVER_API_URL + 'api/file-upload-exercises';

    constructor(private http: HttpClient) { }

    create(fileUploadExercise: FileUploadExercise): Observable<EntityResponseType> {
        const copy = this.convert(fileUploadExercise);
        return this.http.post<FileUploadExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(fileUploadExercise: FileUploadExercise): Observable<EntityResponseType> {
        const copy = this.convert(fileUploadExercise);
        return this.http.put<FileUploadExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<FileUploadExercise>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<FileUploadExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<FileUploadExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<FileUploadExercise[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: FileUploadExercise = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<FileUploadExercise[]>): HttpResponse<FileUploadExercise[]> {
        const jsonResponse: FileUploadExercise[] = res.body;
        const body: FileUploadExercise[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to FileUploadExercise.
     */
    private convertItemFromServer(fileUploadExercise: FileUploadExercise): FileUploadExercise {
        const copy: FileUploadExercise = Object.assign({}, fileUploadExercise);
        return copy;
    }

    /**
     * Convert a FileUploadExercise to a JSON which can be sent to the server.
     */
    private convert(fileUploadExercise: FileUploadExercise): FileUploadExercise {
        const copy: FileUploadExercise = Object.assign({}, fileUploadExercise);
        return copy;
    }
}
