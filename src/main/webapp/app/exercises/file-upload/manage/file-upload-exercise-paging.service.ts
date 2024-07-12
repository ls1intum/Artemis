import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

@Injectable({ providedIn: 'root' })
export class FileUploadExercisePagingService extends ExercisePagingService<FileUploadExercise> {
    private static readonly RESOURCE_URL = 'api/file-upload-exercises';

    constructor(http: HttpClient) {
        super(http, FileUploadExercisePagingService.RESOURCE_URL);
    }
}
