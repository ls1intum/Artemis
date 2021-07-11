export class ExerciseGroupVariantColumn {
    indexExerciseGroup: number;
    exerciseGroupTitle?: string;
    exerciseGroupPointsEqual?: boolean;

    indexExercise: number;
    exerciseTitle?: string;
    exerciseType?: string;
    exerciseMaxPoints?: number;
    exerciseNumberOfParticipations?: number;
    noExercises = false;
}
