import { Observable } from 'rxjs';
import { TextExercise } from 'app/entities/text-exercise.model';

export class MockTextExerciseService {
    create = (textExercise: TextExercise) => Observable.of([{ id: 123 } as TextExercise]);
    update = (textExercise: TextExercise, req?: any) => Observable.of([{ id: 456 } as TextExercise]);
}
