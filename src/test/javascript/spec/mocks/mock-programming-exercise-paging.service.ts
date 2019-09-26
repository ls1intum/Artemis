import { PageableSearch, SearchResult } from 'app/components/table';
import { of } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

export class MockProgrammingExercisePagingService {
    searchForExercises = (pageable: PageableSearch) => of({} as SearchResult<ProgrammingExercise>);
}
