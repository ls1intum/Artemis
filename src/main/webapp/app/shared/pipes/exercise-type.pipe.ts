import { Pipe, PipeTransform } from '@angular/core';
import { ExerciseType } from 'app/entities/exercise.model';

@Pipe({ name: 'exerciseTypeLabel' })
export class ExerciseTypePipe implements PipeTransform {
    /**
     * Returns the type of the exercise as a string.
     * @param type the type of the exercise as an object.
     */
    transform(type: ExerciseType): string {
        switch (type) {
            case ExerciseType.PROGRAMMING:
                return 'Programming';
            case ExerciseType.MODELING:
                return 'Modeling';
            case ExerciseType.QUIZ:
                return 'Quiz';
            case ExerciseType.TEXT:
                return 'Text';
            case ExerciseType.FILE_UPLOAD:
                return 'File Upload';
            default:
                return 'Exercise';
        }
    }
}
