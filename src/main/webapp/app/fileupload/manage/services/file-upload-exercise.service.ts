import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ExerciseServicable, ExerciseService, ExerciseUpdateRequestOptions } from 'app/exercise/services/exercise.service';
import { toUpdateFileUploadExerciseDTO } from 'app/fileupload/shared/entities/update-file-upload-exercise-dto';
import { fromFileUploadExerciseDTO, toFileUploadExerciseInputDTO } from 'app/fileupload/shared/entities/file-upload-exercise-dto';
import { FileUploadExerciseApi } from 'app/openapi/api/file-upload-exercise-api';
import { FileUploadExercise as FileUploadExerciseResponse } from 'app/openapi/model/file-upload-exercise';

@Service()
export class FileUploadExerciseService implements ExerciseServicable<FileUploadExercise> {
    private fileUploadExerciseApi = inject(FileUploadExerciseApi);
    private exerciseService = inject(ExerciseService);

    /**
     * Sends request to create new file upload exercise
     * @param fileUploadExercise that will be sent to the server
     */
    create(fileUploadExercise: FileUploadExercise): Observable<FileUploadExercise> {
        let copy = FileUploadExerciseService.formatFilePattern(fileUploadExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        return this.processResponse(this.fileUploadExerciseApi.createFileUploadExercise(toFileUploadExerciseInputDTO(copy)));
    }

    /**
     * Sends request to update file upload exercise on the server
     * @param fileUploadExercise that we want to update to (must have an id)
     * @param req request options passed to the server
     * @throws Error if the exercise does not have an id
     */
    update(fileUploadExercise: FileUploadExercise, req?: ExerciseUpdateRequestOptions): Observable<FileUploadExercise> {
        if (fileUploadExercise.id === undefined) {
            throw new Error('Cannot update exercise without an ID');
        }
        const copy = FileUploadExerciseService.formatFilePattern(fileUploadExercise);
        const dto = toUpdateFileUploadExerciseDTO(copy);
        return this.processResponse(this.fileUploadExerciseApi.updateFileUploadExercise(fileUploadExercise.id, dto, req?.notificationText));
    }

    /**
     * Sends request to get exercise by its id
     * @param exerciseId id of the exercise
     */
    find(exerciseId: number): Observable<FileUploadExercise> {
        return this.processResponse(this.fileUploadExerciseApi.getFileUploadExercise(exerciseId));
    }

    /** Loads management details of an exam exercise with the same conversion as course details. */
    findForExam(exerciseId: number): Observable<FileUploadExercise> {
        return this.processResponse(this.fileUploadExerciseApi.getFileUploadExerciseForExam(exerciseId));
    }

    /**
     * Sends request to delete file upload exercise by its id
     * @param exerciseId id of the exercise
     */
    delete(exerciseId: number): Observable<void> {
        return this.fileUploadExerciseApi.deleteFileUploadExercise(exerciseId);
    }

    /**
     * Re-evaluates and updates a file upload exercise.
     *
     * @param fileUploadExercise that should be updated of type {FileUploadExercise} (must have an id)
     * @param req optional request options
     * @throws Error if the exercise does not have an id
     */
    reevaluateAndUpdate(fileUploadExercise: FileUploadExercise, req?: ExerciseUpdateRequestOptions): Observable<FileUploadExercise> {
        if (fileUploadExercise.id === undefined) {
            throw new Error('Cannot re-evaluate exercise without an ID');
        }
        const copy = FileUploadExerciseService.formatFilePattern(fileUploadExercise);
        const dto = toUpdateFileUploadExerciseDTO(copy);
        return this.processResponse(this.fileUploadExerciseApi.reEvaluateAndUpdateFileUploadExercise(fileUploadExercise.id, dto, req?.deleteFeedback));
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
    import(adaptedSourceFileUploadExercise: FileUploadExercise): Observable<FileUploadExercise> {
        if (adaptedSourceFileUploadExercise.id === undefined) {
            throw new Error('Cannot import exercise without an ID');
        }
        const copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(adaptedSourceFileUploadExercise);
        return this.processResponse(this.fileUploadExerciseApi.importFileUploadExercise(adaptedSourceFileUploadExercise.id, toFileUploadExerciseInputDTO(copy)));
    }

    private processResponse(response: Observable<FileUploadExerciseResponse>): Observable<FileUploadExercise> {
        return response.pipe(map((dto) => this.exerciseService.processExerciseEntity(fromFileUploadExerciseDTO(dto))));
    }
}
