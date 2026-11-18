import { BuildRunState, IProgrammingBuildRunService } from 'app/programming/shared/services/programming-build-run.service';
import { EMPTY, Observable, of } from 'rxjs';

export class MockProgrammingBuildRunService implements IProgrammingBuildRunService {
    getBuildRunUpdates(programmingExerciseId: number): Observable<BuildRunState> {
        return of(BuildRunState.COMPLETED);
    }

    getSelectedBuildRunCompletions(programmingExerciseId: number): Observable<string> {
        return EMPTY;
    }
}
