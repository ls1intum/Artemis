import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExamExercise } from 'app/entities/exam-exercise';

export class ExamExerciseOverviewItem {
    public exercise: ExamExercise;
    public icon: IconProp;
}
