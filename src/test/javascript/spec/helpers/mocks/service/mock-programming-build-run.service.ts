import { Observable, of } from 'rxjs';

import { BuildRunState, IProgrammingBuildRunService } from 'app/exercises/programming/participate/programming-build-run.service';

export class MockProgrammingBuildRunService implements IProgrammingBuildRunService {
    getBuildRunUpdates(programmingExerciseId: number): Observable<BuildRunState> {
        return of(BuildRunState.COMPLETED);
    }
}
