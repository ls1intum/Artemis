import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class ExerciseGroupColumn {
    indexExerciseGroup!: number;
    exerciseGroupTitle?: string;
    exerciseGroupPointsEqual?: boolean;
    noExercises = false;
    variants: ExerciseGroupVariantColumn[] = [];
}

export class ExerciseGroupVariantColumn {
    indexExercise!: number;
    exerciseTitle?: string;
    exerciseType?: ExerciseType;
    exerciseMaxPoints?: number;
    exerciseNumberOfParticipations?: number;
}
