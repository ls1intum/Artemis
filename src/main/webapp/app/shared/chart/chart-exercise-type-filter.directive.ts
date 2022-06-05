import { Directive } from '@angular/core';
import { ExerciseType } from 'app/entities/exercise.model';

@Directive()
export class ChartExerciseTypeFilterDirective {
    // Ideally I would design the filter as map from ExerciseType to boolean.
    // But I observed some unexpected casting of the ExerciseType in the ExerciseDTO
    // that leads to the following situation: When trying to look up a value given an ExerciseType in a map with structure: ExerciseType -> boolean
    // instead of comparing the string value of the enum, the enum key was taken as string and then used as key for the map
    // E.g. ExerciseType.PROGRAMMING would lead to chartFilter.get('PROGRAMMING') instead of chartFilter.get('programming')
    // This way, never a value was returned as the map did not contain such key
    chartFilter: Map<string, boolean> = new Map();
    numberOfActiveFilters = 0;
    typeSet: Set<ExerciseType> = new Set();

    /**
     * Set up initial filter for the chart
     * @param exerciseScores the score objects containing an exercise type a filter should
     * be provided for
     * @protected
     */
    protected initializeFilterOptions(exerciseScores: any[]): void {
        this.typeSet = new Set(exerciseScores.map((score) => score.exerciseType));
        this.typeSet.forEach((type) => {
            this.chartFilter.set(ChartExerciseTypeFilterDirective.convertToMapKey(type), true);
        });
        this.numberOfActiveFilters = this.typeSet.size;
    }

    /**
     * Handles selection or deselection of specific exercise type
     * @param type the ExerciseType the user changed the filter for
     * @param exerciseScores the score objects the updated filter should be applied against
     * @returns the exerciseScores filtered against the current state of the chart filter
     * @protected
     */
    protected toggleExerciseType(type: ExerciseType, exerciseScores: any[]): any {
        const convertedType = ChartExerciseTypeFilterDirective.convertToMapKey(type);
        const isIncluded = this.chartFilter.get(convertedType);
        this.chartFilter.set(convertedType, !isIncluded);
        this.numberOfActiveFilters += !isIncluded ? 1 : -1;
        return this.applyCurrentFilter(exerciseScores);
    }

    private applyCurrentFilter(exerciseScores: any[]) {
        return exerciseScores.filter((score) => this.chartFilter.get(ChartExerciseTypeFilterDirective.convertToMapKey(score.exerciseType)));
    }

    /**
     * Converts a given exercise type to a map key and returns it
     * @param type the exercise type
     * @protected
     */
    protected static convertToMapKey(type: ExerciseType) {
        return type.toLowerCase().replace('_', '-');
    }
}
