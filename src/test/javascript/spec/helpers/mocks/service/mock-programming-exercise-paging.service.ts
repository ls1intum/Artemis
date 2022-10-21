import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { of } from 'rxjs';

export class MockProgrammingExercisePagingService {
    searchForExercises = (pageable: PageableSearch) => of({} as SearchResult<ProgrammingExercise>);
}
