import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import { FileUploadExercise } from './file-upload-exercise.model';
import { createRequestOption } from 'app/shared';
import { ExerciseService } from 'app/entities/exercise';

export type EntityResponseType = HttpResponse<FileUploadExercise>;
export type EntityArrayResponseType = HttpResponse<FileUploadExercise[]>;

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/file-upload-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    create(fileUploadExercise: FileUploadExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(fileUploadExercise);
        return this.http
            .post<FileUploadExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    update(fileUploadExercise: FileUploadExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(fileUploadExercise);
        return this.http
            .put<FileUploadExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<FileUploadExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<FileUploadExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
