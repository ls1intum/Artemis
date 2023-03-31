import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Exercise } from 'app/entities/exercise.model';

export class ExamExerciseOverviewItem {
    public exercise: Exercise;
    public icon: IconProp;
}
