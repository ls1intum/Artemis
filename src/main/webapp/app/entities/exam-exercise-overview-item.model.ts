import { Exercise } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

export class ExamExerciseOverviewItem {
    public exercise?: Exercise;
    public submission?: Submission;
    public icon?: IconProp;
}
