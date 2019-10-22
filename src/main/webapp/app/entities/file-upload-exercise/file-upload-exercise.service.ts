import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import { FileUploadExercise } from './file-upload-exercise.model';
import { createRequestOption } from 'app/shared';
import { ExerciseService } from 'app/entities/exercise';
import { throwError } from 'rxjs';

export type EntityResponseType = HttpResponse<FileUploadExercise>;
export type EntityArrayResponseType = HttpResponse<FileUploadExercise[]>;

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/file-upload-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    /**
     * Sends request to create new file upload exercise
     * @param fileUploadExercise that will be send to the server
     */
    create(fileUploadExercise: FileUploadExercise): Observable<EntityResponseType> {
        let copy = this.exerciseService.convertDateFromClient(fileUploadExercise);
        copy = this.formatFilePattern(copy);
        return this.http
            .post<FileUploadExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    /**
     * Sends request to update file upload exercise on the server
     * @param fileUploadExercise that we want to update to
     * @param exerciseId id of the exercise that will be updated
     * @param req request options passed to the server
     */
    update(fileUploadExercise: FileUploadExercise, exerciseId: number, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = this.exerciseService.convertDateFromClient(fileUploadExercise);
        copy = this.formatFilePattern(copy);
        return this.http
            .put<FileUploadExercise>(`${this.resourceUrl}/${exerciseId}`, copy, { params: options, observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    /**
     * Sends request to get exercise by its id
     * @param exerciseId id of the exercise
     */
    find(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<FileUploadExercise>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    /**
     * Sends request to get all available file upload exercises
     * @param req request options passed to the server
     */
    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<FileUploadExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res));
    }

    /**
     * Sends request to delete file upload exercise by its id
     * @param exerciseId id of the exercise
     */
    delete(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' });
    }

    private formatFilePattern(fileUploadExercise: FileUploadExercise): FileUploadExercise {
        fileUploadExercise.filePattern = fileUploadExercise.filePattern.replace(/\s/g, '').toLowerCase();
        return fileUploadExercise;
    }
}
