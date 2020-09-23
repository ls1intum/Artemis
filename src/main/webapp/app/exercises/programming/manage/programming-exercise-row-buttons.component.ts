import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { JhiEventManager } from 'ng-jhipster';
import { Subject } from 'rxjs';
import { OrionState, isOrion } from 'app/shared/orion/orion';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-programming-exercise-row-buttons',
    templateUrl: './programming-exercise-row-buttons.component.html',
    styles: [],
})
export class ProgrammingExerciseRowButtonsComponent {
    @Input() courseId: number;
    @Input() exercise: ProgrammingExercise;
    private dialogErrorSource = new Subject<string>();
    readonly ActionType = ActionType;
    readonly isOrion = isOrion;
    FeatureToggle = FeatureToggle;
    orionState: OrionState;
    dialogError$ = this.dialogErrorSource.asObservable();
    constructor(private programmingExerciseService: ProgrammingExerciseService, private eventManager: JhiEventManager, private exerciseService: ExerciseService) {}

    /**
     * Deletes programming exercise
     * @param programmingExerciseId the id of the programming exercise that we want to delete
     * @param $event contains additional checks for deleting exercise
     */
    deleteProgrammingExercise(programmingExerciseId: number, $event: { [key: string]: boolean }) {
        return this.programmingExerciseService.delete(programmingExerciseId, $event.deleteStudentReposBuildPlans, $event.deleteBaseReposBuildPlans).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted an programmingExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Resets programming exercise
     * @param programmingExerciseId the id of the programming exercise that we want to delete
     */
    resetProgrammingExercise(programmingExerciseId: number) {
        this.exerciseService.reset(programmingExerciseId).subscribe(
            () => this.dialogErrorSource.next(''),
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }
}
