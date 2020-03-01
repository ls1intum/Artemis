import { of } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';

export class MockProgrammingExercisePagingService {
    searchForExercises = (pageable: PageableSearch) => of({} as SearchResult<ProgrammingExercise>);
}
