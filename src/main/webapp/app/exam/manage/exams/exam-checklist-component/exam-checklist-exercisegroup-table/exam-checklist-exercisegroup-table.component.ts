import { Component, effect, input, signal } from '@angular/core';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseType, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroupColumn, ExerciseGroupVariantColumn } from 'app/exam/shared/entities/exercise-group-column.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiTableDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NoDataComponent } from 'app/shared-ui/components/no-data/no-data-component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exam-checklist-exercisegroup-table',
    templateUrl: './exam-checklist-exercisegroup-table.component.html',
    styleUrls: ['./exam-checklist-exercisegroup-table.component.scss'],
    imports: [TranslateDirective, TumAetUiTableDirective, TumAetUiTooltipDirective, FaIconComponent, NoDataComponent, ArtemisTranslatePipe],
})
export class ExamChecklistExerciseGroupTableComponent {
    quizExamMaxPoints = input.required<number>();
    exerciseGroups = input.required<ExerciseGroup[]>();
    readonly exerciseGroupColumns = signal<ExerciseGroupColumn[]>([]);
    readonly totalPoints = signal<number | undefined>(undefined);
    readonly hasPointMismatch = signal<boolean>(false);
    readonly getIcon = getIcon;
    readonly getIconTooltip = getIconTooltip;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    readonly totalParticipants = signal<number>(undefined!);

    constructor() {
        effect(() => {
            this.recomputeColumns();
        });
    }

    private recomputeColumns() {
        const exerciseGroupColumns: ExerciseGroupColumn[] = []; // Clear any previously existing entries
        let totalParticipants = this.totalParticipants();
        let totalPoints = 0;
        if (this.exerciseGroups()) {
            let exerciseGroupIndex = 1;
            this.exerciseGroups().forEach((exerciseGroup) => {
                const exerciseGroupColumn = new ExerciseGroupColumn();
                exerciseGroupColumn.exerciseGroupTitle = exerciseGroup.title;
                exerciseGroupColumn.indexExerciseGroup = exerciseGroupIndex;

                if (!exerciseGroup.exercises || exerciseGroup.exercises.length === 0) {
                    exerciseGroupColumn.noExercises = true;
                } else {
                    // set points and checks
                    const maxPoints = exerciseGroup.exercises?.[0].maxPoints;
                    exerciseGroupColumn.exerciseGroupPointsEqual = true;
                    exerciseGroupColumn.exerciseGroupPointsEqual = !exerciseGroup.exercises?.some((exercise) => {
                        return exercise.maxPoints !== maxPoints;
                    });

                    if (!exerciseGroupColumn.exerciseGroupPointsEqual) {
                        this.hasPointMismatch.set(true);
                    }

                    exerciseGroupColumn.noExercises = false;
                    let exerciseVariantIndex = 1;
                    totalParticipants = 0;
                    exerciseGroup.exercises.forEach((exercise, index) => {
                        // generate columns for each exercise
                        const exerciseVariantColumn = new ExerciseGroupVariantColumn();
                        // set properties
                        exerciseVariantColumn.indexExercise = exerciseVariantIndex;
                        exerciseVariantColumn.exerciseTitle = exercise.title;
                        exerciseVariantColumn.exerciseType = exercise.type;
                        exerciseVariantColumn.exerciseNumberOfParticipations = exercise.numberOfParticipations ? exercise.numberOfParticipations : 0;
                        exerciseVariantColumn.exerciseMaxPoints = exercise.maxPoints;

                        totalParticipants += exerciseVariantColumn.exerciseNumberOfParticipations;

                        exerciseGroupColumn.variants.push(exerciseVariantColumn);
                        exerciseVariantIndex++;
                    });
                    totalPoints += maxPoints ?? 0;
                }
                exerciseGroupColumns.push(exerciseGroupColumn);
                exerciseGroupIndex++;
            });
        }
        this.totalParticipants.set(totalParticipants);
        this.exerciseGroupColumns.set(exerciseGroupColumns);
        this.totalPoints.set(totalPoints);
    }

    protected readonly ExerciseType = ExerciseType;
}
