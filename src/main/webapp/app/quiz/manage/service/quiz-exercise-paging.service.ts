import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { PagingService } from 'app/exercise/services/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/foundation/pagination/pageable-table';
import { QuizExerciseRetrievalApi } from 'app/openapi/api/quiz-exercise-retrieval-api';
import { hydrate } from 'app/foundation/util/deep-clone.util';

@Service()
export class QuizExercisePagingService extends PagingService<QuizExercise> {
    private quizExerciseRetrievalApi = inject(QuizExerciseRetrievalApi);

    /**
     * Searches the quiz exercises the user may import.
     *
     * @param pageable the search term, sort order and page
     * @param options whether to include course exercises, exam exercises, or both
     * @returns one page of matching quiz exercises, each carrying only what the import table shows
     */
    public override search(pageable: SearchTermPageableSearch, options: { isCourseFilter: boolean; isExamFilter: boolean }): Observable<SearchResult<QuizExercise>> {
        return this.quizExerciseRetrievalApi
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
                    resultsOnPage: (page.resultsOnPage ?? []).map((row) => {
                        const quizExercise: QuizExercise = hydrate(new QuizExercise(undefined, undefined), row);
                        return quizExercise;
                    }),
                    numberOfPages: page.numberOfPages ?? 0,
                })),
            );
    }
}
