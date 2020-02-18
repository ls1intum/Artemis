import { of } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';
import { PageableSearch, SearchResult } from 'app/components/table/pageable-table';

export class MockProgrammingExercisePagingService {
    searchForExercises = (pageable: PageableSearch) => of({} as SearchResult<ProgrammingExercise>);
}
