import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class ExamExerciseOverviewItem {
    public exercise!: Exercise;
    public icon!: IconProp;
}
