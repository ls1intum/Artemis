import { IProgrammingBuildRunService } from 'app/programming-submission/programming-build-run.service';
import { of, Observable } from 'rxjs';

export class MockProgrammingBuildRunService implements IProgrammingBuildRunService {
    emitBuildRunUpdate(programmingExerciseId: number, isBuilding: boolean): void {}

    getBuildRunUpdates(programmingExerciseId: number): Observable<boolean> {
        return of(false);
    }
}
