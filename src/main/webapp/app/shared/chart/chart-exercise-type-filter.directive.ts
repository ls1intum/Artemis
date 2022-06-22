import { Injectable } from '@angular/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { ChartFilter } from 'app/shared/chart/chart-filter';

@Injectable({ providedIn: 'root' })
export class ChartExerciseTypeFilter extends ChartFilter {
    typeSet: Set<ExerciseType> = new Set();

    /**
     * Set up initial filter for the chart
     * @param exerciseScores the score objects containing an exercise type a filter should
     * be provided for
     */
    initializeFilterOptions(exerciseScores: any[]): void {
        this.typeSet = new Set(exerciseScores.map((score) => score.exerciseType));
        this.typeSet.forEach((type) => {
            this.filterMap.set(ChartExerciseTypeFilter.convertToMapKey(type), true);
        });
        this.numberOfActiveFilters = this.typeSet.size;
    }

    updateFilterOptions(exerciseScores: any[]): any[] {
        const updatedTypes = new Set(exerciseScores.map((score) => score.exerciseType));
        this.filterMap.forEach((value, key) => {
            if (!updatedTypes.has(ChartExerciseTypeFilter.convertToExerciseType(key))) {
                this.filterMap.set(key, false);
            }
        });
        updatedTypes.forEach((type) => {
            const convertedKey = ChartExerciseTypeFilter.convertToMapKey(type);
            if (this.filterMap.get(convertedKey) === undefined) {
                this.filterMap.set(convertedKey, true);
            }
        });

        this.numberOfActiveFilters = 0;
        this.filterMap.forEach((value) => (this.numberOfActiveFilters += value ? 1 : 0));
        return this.applyCurrentFilter(exerciseScores);
    }

    toggleAllTypes(exerciseScores: any[], includeAll: boolean): any[] {
        this.filterMap.forEach((value, key) => this.filterMap.set(key, includeAll));
        this.numberOfActiveFilters = 0;
        this.filterMap.forEach((value) => (this.numberOfActiveFilters += value ? 1 : 0));
        return this.applyCurrentFilter(exerciseScores);
    }

    /**
     * Handles selection or deselection of specific exercise type
     * @param type the ExerciseType the user changed the filter for
     * @param exerciseScores the score objects the updated filter should be applied against
     * @returns the exerciseScores filtered against the current state of the chart filter
     */
    toggleExerciseType(type: ExerciseType, exerciseScores: any[]): any[] {
        const convertedType = ChartExerciseTypeFilter.convertToMapKey(type);
        const isIncluded = this.filterMap.get(convertedType);
        this.filterMap.set(convertedType, !isIncluded);
        this.numberOfActiveFilters += !isIncluded ? 1 : -1;
        return this.applyCurrentFilter(exerciseScores);
    }

    applyCurrentFilter(exerciseScores: any[]): any[] {
        return exerciseScores.filter((score) => this.filterMap.get(ChartExerciseTypeFilter.convertToMapKey(score.exerciseType)));
    }

    /**
     * Converts a given exercise type to a map key and returns it
     * @param type the exercise type
     */
    static convertToMapKey(type: ExerciseType) {
        return type.toLowerCase().replace('_', '-');
    }

    static convertToExerciseType(type: string): ExerciseType {
        return type.replace('-', '_').toUpperCase() as ExerciseType;
    }
}
