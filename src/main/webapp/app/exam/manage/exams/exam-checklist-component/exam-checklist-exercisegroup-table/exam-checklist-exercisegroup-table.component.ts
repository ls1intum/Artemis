import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupVariantColumn } from 'app/entities/exercise-group-variant-column.model';

@Component({
    selector: 'jhi-exam-checklist-exercisegroup-table',
    templateUrl: './exam-checklist-exercisegroup-table.component.html',
})
export class ExamChecklistExerciseGroupTableComponent implements OnInit, OnChanges {
    @Input() exerciseGroups: ExerciseGroup[];
    exerciseGroupVariantColumns: ExerciseGroupVariantColumn[] = [];

    ngOnInit() {}

    ngOnChanges() {
        if (this.exerciseGroups) {
            let exerciseGroupIndex = 1;
            let exerciseVariantIndex = 1;
            this.exerciseGroups.forEach((exerciseGroup) => {
                const exerciseGroupVariantColumn = new ExerciseGroupVariantColumn();
                exerciseGroupVariantColumn.exerciseGroupTitle = exerciseGroup.title;

                exerciseGroupVariantColumn.indexExerciseGroup = exerciseGroupIndex;
                exerciseGroupIndex++;

                const maxPoints = exerciseGroup.exercises?.[0].maxPoints;
                exerciseGroupVariantColumn.exerciseGroupPointsEqual = exerciseGroup.exercises?.some((exercise) => {
                    return exercise.maxPoints !== maxPoints;
                });
                if (!exerciseGroup.exercises || exerciseGroup.exercises.length === 0) {
                    exerciseGroupVariantColumn.noExercises = true;
                } else {
                    exerciseGroupVariantColumn.noExercises = false;
                    exerciseGroup.exercises!.forEach((exercise, index) => {
                        let exerciseVariantColumn;
                        if (index === 0) {
                            exerciseVariantColumn = exerciseGroupVariantColumn;
                            exerciseVariantColumn.indexExercise = exerciseVariantIndex;
                        } else {
                            exerciseVariantColumn = new ExerciseGroupVariantColumn();
                            exerciseVariantColumn.indexExercise = exerciseVariantIndex;
                        }
                        exerciseVariantColumn.exerciseTitle = exercise.title;
                        exerciseVariantColumn.exerciseNumberOfParticipations = exercise.numberOfParticipations;
                        exerciseVariantColumn.exerciseMaxPoints = exercise.maxPoints;

                        exerciseVariantIndex++;
                    });
                }
            });
        }
    }
}

/*
    ngOnChanges() {
        if (this.exerciseGroups) {
            this.exerciseGroups.forEach((exerciseGroup) => {
                if (exerciseGroup.exercises) {
                    exerciseGroup.exercises!.forEach((exercise) => {
                        exercise.exerciseGroup = exerciseGroup;
                        this.groupsWithExercises.push(exercise);
                    });
                    const maxPoints = exerciseGroup.exercises?.[0].maxPoints;
                    exerciseGroup.allPointsEqual = exerciseGroup.exercises?.some((exercise) => {
                        return exercise.maxPoints !== maxPoints;
                    });
                } else {
                    this.groupsWithoutExercises.push(exerciseGroup);
                }
            });
        }
    }
 */
