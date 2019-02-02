import { Pipe, PipeTransform } from '@angular/core';
import { ExerciseType } from "app/entities/exercise/exercise.model";

@Pipe({
    name: 'exerciseTypeLabel'
})
export class ExerciseTypePipe implements PipeTransform {

    transform(type: ExerciseType): string {
        switch (type) {
            case ExerciseType.PROGRAMMING:
                return 'Programming Exercise';
            case ExerciseType.MODELING:
                return 'Modeling Exercise';
            case ExerciseType.QUIZ:
                return 'Quiz Exercise';
            case ExerciseType.TEXT:
                return 'Text Exercise';
            case ExerciseType.FILE_UPLOAD:
                return 'File Upload Exercise';
            default:
                return 'Exercise'
        }
    }

}
