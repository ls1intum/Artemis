import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseServicable, ExerciseService } from 'app/exercise/services/exercise.service';
import { toUpdateFileUploadExerciseDTO } from 'app/fileupload/shared/entities/update-file-upload-exercise-dto';

export type EntityResponseType = HttpResponse<FileUploadExercise>;
export type EntityArrayResponseType = HttpResponse<FileUploadExercise[]>;

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseService implements ExerciseServicable<FileUploadExercise> {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);

    private resourceUrl = 'api/fileupload/file-upload-exercises';

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
     * @param fileUploadExercise that we want to update to (must have an id)
     * @param req request options passed to the server
     * @throws Error if the exercise does not have an id
     */
    update(fileUploadExercise: FileUploadExercise, req?: any): Observable<EntityResponseType> {
        if (fileUploadExercise.id === undefined) {
            throw new Error('Cannot update exercise without an ID');
        }
        const options = createRequestOption(req);
        let copy = FileUploadExerciseService.formatFilePattern(fileUploadExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        const dto = toUpdateFileUploadExerciseDTO(copy);
        return this.http
            .put<FileUploadExercise>(`${this.resourceUrl}/${fileUploadExercise.id!}`, dto, { params: options, observe: 'response' })
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
    delete(exerciseId: number): Observable<HttpResponse<any>> {
        return this.http.delete(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' });
    }

    /**
     * Re-evaluates and updates a file upload exercise.
     *
     * @param fileUploadExercise that should be updated of type {FileUploadExercise} (must have an id)
     * @param req optional request options
     * @throws Error if the exercise does not have an id
     */
    reevaluateAndUpdate(fileUploadExercise: FileUploadExercise, req?: any): Observable<EntityResponseType> {
        if (fileUploadExercise.id === undefined) {
            throw new Error('Cannot re-evaluate exercise without an ID');
        }
        const options = createRequestOption(req);
        let copy = FileUploadExerciseService.formatFilePattern(fileUploadExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        const dto = toUpdateFileUploadExerciseDTO(copy);
        return this.http
            .put<FileUploadExercise>(`${this.resourceUrl}/${fileUploadExercise.id}/re-evaluate`, dto, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    private static formatFilePattern(fileUploadExercise: FileUploadExercise): FileUploadExercise {
        if (fileUploadExercise.filePattern) {
            fileUploadExercise.filePattern = fileUploadExercise.filePattern.replace(/\s/g, '').toLowerCase();
        }
        return fileUploadExercise;
    }

    /**
     * Imports a file upload exercise by cloning the entity itself plus example solutions and example submissions
     *
     * @param adaptedSourceFileUploadExercise The exercise that should be imported (must have an id), including adapted values for the
     * new exercise. E.g. with another title than the original exercise. Old values that should get discarded
     * (like the old ID) will be handled by the server.
     * @throws Error if the exercise does not have an id
     */
    import(adaptedSourceFileUploadExercise: FileUploadExercise) {
        if (adaptedSourceFileUploadExercise.id === undefined) {
            throw new Error('Cannot import exercise without an ID');
        }
        let copy = ExerciseService.convertExerciseDatesFromClient(adaptedSourceFileUploadExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<FileUploadExercise>(`${this.resourceUrl}/import/${adaptedSourceFileUploadExercise.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }
}
