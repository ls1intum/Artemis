import { Service, inject } from '@angular/core';
import { PagingService } from 'app/exercise/services/paging.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { fromFileUploadExerciseDTO } from 'app/fileupload/shared/entities/file-upload-exercise-dto';
import { SearchResult, SearchTermPageableSearch } from 'app/foundation/pagination/pageable-table';
import { FileUploadExerciseApi } from 'app/openapi/api/file-upload-exercise-api';
import { Observable, map } from 'rxjs';

@Service()
export class FileUploadExercisePagingService extends PagingService<FileUploadExercise> {
    private fileUploadExerciseApi = inject(FileUploadExerciseApi);

    /**
     * Searches the file upload exercises the user may import.
     *
     * @param pageable the search term, sort order and page
     * @param options whether to include course exercises, exam exercises, or both
     * @returns one page of matching file upload exercises with their course or exam context
     */
    public override search(pageable: SearchTermPageableSearch, options: { isCourseFilter: boolean; isExamFilter: boolean }): Observable<SearchResult<FileUploadExercise>> {
        return this.fileUploadExerciseApi
            .getAllExercisesOnPage(
                pageable.page,
                pageable.pageSize,
                pageable.sortingOrder,
                pageable.sortedColumn,
                pageable.searchTerm,
                options.isCourseFilter,
                options.isExamFilter,
            )
            .pipe(
                map((page) => ({
                    // The server leaves out an empty page of results.
                    resultsOnPage: (page.resultsOnPage ?? []).map(fromFileUploadExerciseDTO),
                    numberOfPages: page.numberOfPages,
                })),
            );
    }
}
