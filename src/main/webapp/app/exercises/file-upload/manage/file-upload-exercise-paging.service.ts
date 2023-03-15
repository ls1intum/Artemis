import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class FileUploadExercisePagingService extends ExercisePagingService<FileUploadExercise> {
    private static readonly resourceUrl = SERVER_API_URL + 'api/file-upload-exercises';

    constructor(http: HttpClient) {
        super(http, FileUploadExercisePagingService.resourceUrl);
    }
}
