import { Observable } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

export class MockCourseExerciseService {
    startExercise = (courseId: number, exerciseId: number) => Observable.of({} as StudentParticipation);
}
