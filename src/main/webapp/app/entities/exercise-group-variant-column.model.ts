import { ExerciseType } from 'app/entities/exercise.model';

export class ExerciseGroupVariantColumn {
    indexExerciseGroup: number;
    exerciseGroupTitle?: string;
    exerciseGroupPointsEqual?: boolean;

    indexExercise: number;
    exerciseTitle?: string;
    exerciseType?: ExerciseType;
    exerciseMaxPoints?: number;
    exerciseNumberOfParticipations?: number;
    noExercises = false;
}
