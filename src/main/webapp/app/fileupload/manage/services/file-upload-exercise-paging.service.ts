import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ExercisePagingService } from 'app/exercise/services/exercise-paging.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { FileUploadExerciseDto, fromFileUploadExerciseDTO } from 'app/fileupload/shared/entities/file-upload-exercise-dto';
import { SearchResult, SearchTermPageableSearch } from 'app/foundation/pagination/pageable-table';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { Observable, map } from 'rxjs';

interface FileUploadExerciseSearchResultDto {
    resultsOnPage?: FileUploadExerciseDto[];
    numberOfPages: number;
}

@Injectable({ providedIn: 'root' })
export class FileUploadExercisePagingService extends ExercisePagingService<FileUploadExercise> {
    private static readonly RESOURCE_URL = 'api/fileupload/file-upload-exercises';

    constructor() {
        const http = inject(HttpClient);

        super(http, FileUploadExercisePagingService.RESOURCE_URL);
    }

    public override search(
        pageable: SearchTermPageableSearch,
        options: { isCourseFilter: boolean; isExamFilter: boolean; programmingLanguage?: ProgrammingLanguage },
    ): Observable<SearchResult<FileUploadExercise>> {
        let params = this.createHttpParams(pageable);
        params = params.set('isCourseFilter', String(options.isCourseFilter)).set('isExamFilter', String(options.isExamFilter));
        return this.http.get<FileUploadExerciseSearchResultDto>(this.resourceUrl, { params }).pipe(
            map((result) => ({
                resultsOnPage: (result.resultsOnPage ?? []).map(fromFileUploadExerciseDTO),
                numberOfPages: result.numberOfPages,
            })),
        );
    }
}
