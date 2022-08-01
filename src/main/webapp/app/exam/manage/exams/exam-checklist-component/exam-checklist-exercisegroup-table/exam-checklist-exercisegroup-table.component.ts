import { Component, Input, OnChanges } from '@angular/core';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExerciseGroupVariantColumn } from 'app/entities/exercise-group-variant-column.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exam-checklist-exercisegroup-table',
    templateUrl: './exam-checklist-exercisegroup-table.component.html',
    styleUrls: ['./exam-checklist-exercisegroup-table.component.scss'],
})
export class ExamChecklistExerciseGroupTableComponent implements OnChanges {
    @Input() exerciseGroups: ExerciseGroup[];
    exerciseGroupVariantColumns: ExerciseGroupVariantColumn[] = [];
    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    ngOnChanges() {
        this.exerciseGroupVariantColumns = []; // Clear any previously existing entries
        if (this.exerciseGroups) {
            let exerciseGroupIndex = 1;
            this.exerciseGroups.forEach((exerciseGroup) => {
                const exerciseGroupVariantColumn = new ExerciseGroupVariantColumn();
                exerciseGroupVariantColumn.exerciseGroupTitle = exerciseGroup.title;
                exerciseGroupVariantColumn.indexExerciseGroup = exerciseGroupIndex;

                if (!exerciseGroup.exercises || exerciseGroup.exercises.length === 0) {
                    exerciseGroupVariantColumn.noExercises = true;
                    this.exerciseGroupVariantColumns.push(exerciseGroupVariantColumn);
                } else {
                    // set points and checks
                    const maxPoints = exerciseGroup.exercises?.[0].maxPoints;
                    exerciseGroupVariantColumn.exerciseGroupPointsEqual = true;
                    exerciseGroupVariantColumn.exerciseGroupPointsEqual = !exerciseGroup.exercises?.some((exercise) => {
                        return exercise.maxPoints !== maxPoints;
                    });

                    exerciseGroupVariantColumn.noExercises = false;
                    let exerciseVariantIndex = 1;
                    exerciseGroup.exercises!.forEach((exercise, index) => {
                        // generate columns for each exercise
                        let exerciseVariantColumn;
                        if (index === 0) {
                            // the first exercise uses the exercisegroup column
                            exerciseVariantColumn = exerciseGroupVariantColumn;
                            exerciseVariantColumn.indexExercise = exerciseVariantIndex;
                        } else {
                            exerciseVariantColumn = new ExerciseGroupVariantColumn();
                            exerciseVariantColumn.indexExercise = exerciseVariantIndex;
                            exerciseVariantColumn.exerciseGroupTitle = '';
                        }
                        // set properties
                        exerciseVariantColumn.exerciseTitle = exercise.title;
                        exerciseVariantColumn.exerciseType = exercise.type;
                        exerciseVariantColumn.exerciseNumberOfParticipations = exercise.numberOfParticipations ? exercise.numberOfParticipations : 0;
                        exerciseVariantColumn.exerciseMaxPoints = exercise.maxPoints;

                        this.exerciseGroupVariantColumns.push(exerciseVariantColumn);
                        exerciseVariantIndex++;
                    });
                }
                exerciseGroupIndex++;
            });
        }
    }
}
