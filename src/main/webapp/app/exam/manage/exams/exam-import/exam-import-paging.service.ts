import { Service, inject } from '@angular/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { toExamForImport } from 'app/exam/manage/util/exam-import-paging.util';
import { PagingService } from 'app/exercise/services/paging.service';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { ExamApi } from 'app/openapi/api/exam-api';
import { SortingOrder as GeneratedSortingOrder } from 'app/openapi/model/sorting-order';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Service()
export class ExamImportPagingService extends PagingService<Exam> {
    private examApi = inject(ExamApi);

    /**
     * Method to get (possible) exams for import from the server
     * @param pageable object specifying search parameters
     * @param options withExercises if only exams with exercises should be included in the results
     */
    override search(pageable: SearchTermPageableSearch, options: { withExercises: boolean }): Observable<SearchResult<Exam>> {
        const sortingOrder = pageable.sortingOrder === SortingOrder.ASCENDING ? GeneratedSortingOrder.Ascending : GeneratedSortingOrder.Descending;
        return this.examApi
            .getAllExamsOnPage(pageable.searchTerm, options.withExercises, pageable.page, pageable.pageSize, sortingOrder, pageable.sortedColumn)
            .pipe(map((result) => ({ resultsOnPage: (result.resultsOnPage ?? []).map(toExamForImport), numberOfPages: result.numberOfPages ?? 0 })));
    }
}
