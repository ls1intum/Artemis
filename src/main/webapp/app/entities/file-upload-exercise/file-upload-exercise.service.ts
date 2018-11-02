import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import * as moment from 'moment';

import { FileUploadExercise } from './file-upload-exercise.model';
import { createRequestOption } from '../../shared';
import { Exercise } from 'app/entities/exercise';

export type EntityResponseType = HttpResponse<FileUploadExercise>;
export type EntityArrayResponseType = HttpResponse<FileUploadExercise[]>;

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/file-upload-exercises';

    constructor(private http: HttpClient) {}

    create(fileUploadExercise: FileUploadExercise): Observable<EntityResponseType> {
        const copy = Exercise.convertDateFromClient(fileUploadExercise);
        return this.http
            .post<FileUploadExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => Exercise.convertDateFromServer(res));
    }

    update(fileUploadExercise: FileUploadExercise): Observable<EntityResponseType> {
        const copy = Exercise.convertDateFromClient(fileUploadExercise);
        return this.http
            .put<FileUploadExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => Exercise.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<FileUploadExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => Exercise.convertDateFromServer(res));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<FileUploadExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: EntityArrayResponseType) => Exercise.convertDateArrayFromServer(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
