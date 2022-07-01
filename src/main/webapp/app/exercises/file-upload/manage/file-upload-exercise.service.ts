import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseServicable, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

export type EntityResponseType = HttpResponse<FileUploadExercise>;
export type EntityArrayResponseType = HttpResponse<FileUploadExercise[]>;

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseService implements ExerciseServicable<FileUploadExercise> {
    private resourceUrl = SERVER_API_URL + 'api/file-upload-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    /**
     * Sends request to create new file upload exercise
     * @param fileUploadExercise that will be sent to the server
     */
    create(fileUploadExercise: FileUploadExercise): Observable<EntityResponseType> {
        let copy = ExerciseService.convertExerciseDatesFromClient(fileUploadExercise);
        copy = FileUploadExerciseService.formatFilePattern(copy);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<FileUploadExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Sends request to update file upload exercise on the server
     * @param fileUploadExercise that we want to update to
     * @param req request options passed to the server
     */
    update(fileUploadExercise: FileUploadExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = ExerciseService.convertExerciseDatesFromClient(fileUploadExercise);
        copy = FileUploadExerciseService.formatFilePattern(copy);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<FileUploadExercise>(`${this.resourceUrl}/${fileUploadExercise.id!}`, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Sends request to get exercise by its id
     * @param exerciseId id of the exercise
     */
    find(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<FileUploadExercise>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Sends request to get all available file upload exercises
     * @param req request options passed to the server
     */
    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<FileUploadExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.processExerciseEntityArrayResponse(res)));
    }

    /**
     * Sends request to delete file upload exercise by its id
     * @param exerciseId id of the exercise
     */
    delete(exerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' });
    }

    /**
     * Re-evaluates and updates a file upload exercise.
     *
     * @param fileUploadExercise that should be updated of type {FileUploadExercise}
     * @param req optional request options
     */
    reevaluateAndUpdate(fileUploadExercise: FileUploadExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = ExerciseService.convertExerciseDatesFromClient(fileUploadExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<FileUploadExercise>(`${this.resourceUrl}/${fileUploadExercise.id}/re-evaluate`, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    private static formatFilePattern(fileUploadExercise: FileUploadExercise): FileUploadExercise {
        fileUploadExercise.filePattern = fileUploadExercise.filePattern!.replace(/\s/g, '').toLowerCase();
        return fileUploadExercise;
    }
}
