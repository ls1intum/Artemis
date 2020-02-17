import { Observable } from 'rxjs';
import { AgentParticipation } from 'app/entities/participation';

export class MockCourseExerciseService {
    startExercise = (courseId: number, exerciseId: number) => Observable.of({} as AgentParticipation);
}
