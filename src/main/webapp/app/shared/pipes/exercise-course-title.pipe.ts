import { Pipe, PipeTransform } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';

@Pipe({
    name: 'courseTitle',
})
export class ExerciseCourseTitlePipe implements PipeTransform {
    /**
     * Returns the course title of the exercise via the exerciseGroup or the usual course member
     *
     * @param exercise for which the course title should should be retrieved
     * @returns title of the exercise course
     */
    transform(exercise: Exercise): string {
        if (exercise.exerciseGroup?.exam?.course) {
            return exercise.exerciseGroup?.exam?.course.title;
        } else if (exercise.course) {
            return exercise.course.title;
        }
        return '';
    }
}
