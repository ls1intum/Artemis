import { Injectable } from '@angular/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { ChartFilter } from 'app/shared/chart/chart-filter';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';
import { ExerciseScoresDTO } from 'app/overview/visualizations/exercise-scores-chart.service';

type TypeFilterOperatingType = ExerciseScoresDTO | CourseManagementStatisticsModel;

@Injectable({ providedIn: 'root' })
export class ChartExerciseTypeFilter extends ChartFilter {
    typeSet: Set<ExerciseType> = new Set();

    /**
     * Set up initial filter for the chart
     * @param exerciseScores the score objects containing an exercise type a filter should
     * be provided for
     */
    initializeFilterOptions(exerciseScores: TypeFilterOperatingType[]): void {
        this.typeSet = new Set(exerciseScores.map((score) => score.exerciseType));
        this.typeSet.forEach((type) => {
            this.filterMap.set(ChartExerciseTypeFilter.convertToMapKey(type), true);
        });
        this.numberOfActiveFilters = this.typeSet.size;
    }

    /**
     * Handles selection or deselection of specific exercise type
     * @param type the ExerciseType the user changed the filter for
     * @param exerciseScores the score objects the updated filter should be applied against
     * @returns the exerciseScores filtered against the current state of the chart filter
     */
    toggleExerciseType<E extends TypeFilterOperatingType>(type: ExerciseType, exerciseScores: TypeFilterOperatingType[]): Array<E> {
        const convertedType = ChartExerciseTypeFilter.convertToMapKey(type);
        const isIncluded = this.filterMap.get(convertedType);
        this.filterMap.set(convertedType, !isIncluded);
        this.numberOfActiveFilters += !isIncluded ? 1 : -1;
        return this.applyCurrentFilter<E>(exerciseScores);
    }

    /**
     * Applies the current filter setting to the provided exercise scores
     * @param exerciseScores the exercise scores that should be filtered against the current filter setting
     * @returns exerciseScores filtered against the current filter setting
     */
    applyCurrentFilter<E extends TypeFilterOperatingType>(exerciseScores: TypeFilterOperatingType[]): Array<E> {
        return exerciseScores.filter((score) => this.filterMap.get(ChartExerciseTypeFilter.convertToMapKey(score.exerciseType))) as Array<E>;
    }

    /**
     * Converts a given exercise type to a map key and returns it
     * @param type the exercise type
     * @returns string representation of the exercise type as it is stored in the Map
     */
    static convertToMapKey(type: ExerciseType) {
        return type.toLowerCase().replace('_', '-');
    }
}
