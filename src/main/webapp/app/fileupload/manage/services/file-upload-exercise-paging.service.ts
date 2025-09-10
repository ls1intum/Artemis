import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ExercisePagingService } from 'app/exercise/services/exercise-paging.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';

@Injectable({ providedIn: 'root' })
export class FileUploadExercisePagingService extends ExercisePagingService<FileUploadExercise> {
    private static readonly RESOURCE_URL = 'api/fileupload/file-upload-exercises';

    constructor() {
        const http = inject(HttpClient);

        super(http, FileUploadExercisePagingService.RESOURCE_URL);
    }
}
