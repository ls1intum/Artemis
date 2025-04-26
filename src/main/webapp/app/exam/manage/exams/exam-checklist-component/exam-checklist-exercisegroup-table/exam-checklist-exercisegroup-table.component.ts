import { Component, OnChanges, input } from '@angular/core';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseType, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroupVariantColumn } from 'app/exam/shared/entities/exercise-group-variant-column.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NoDataComponent } from 'app/shared/components/no-data/no-data-component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exam-checklist-exercisegroup-table',
    templateUrl: './exam-checklist-exercisegroup-table.component.html',
    styleUrls: ['./exam-checklist-exercisegroup-table.component.scss'],
    imports: [TranslateDirective, NgbTooltip, FaIconComponent, NoDataComponent, ArtemisTranslatePipe],
})
export class ExamChecklistExerciseGroupTableComponent implements OnChanges {
    quizExamMaxPoints = input.required<number>();
    exerciseGroups = input.required<ExerciseGroup[]>();
    exerciseGroupVariantColumns: ExerciseGroupVariantColumn[] = [];
    readonly getIcon = getIcon;
    readonly getIconTooltip = getIconTooltip;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    totalParticipants: number;

    ngOnChanges() {
        this.exerciseGroupVariantColumns = []; // Clear any previously existing entries
        if (this.exerciseGroups()) {
            let exerciseGroupIndex = 1;
            this.exerciseGroups()!.forEach((exerciseGroup) => {
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
                    this.totalParticipants = 0;
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

                        this.totalParticipants += exerciseVariantColumn.exerciseNumberOfParticipations;

                        this.exerciseGroupVariantColumns.push(exerciseVariantColumn);
                        exerciseVariantIndex++;
                    });
                }
                exerciseGroupIndex++;
            });
        }
    }

    protected readonly ExerciseType = ExerciseType;
}
