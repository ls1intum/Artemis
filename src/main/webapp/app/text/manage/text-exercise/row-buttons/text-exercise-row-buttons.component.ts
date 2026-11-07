import { Component, inject, input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { faBook, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { exerciseDeletionTarget, isDeletionCompleted } from 'app/shared-ui/delete-dialog/entity-deletion-target.util';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Course } from 'app/course/shared/entities/course.model';

@Component({
    selector: 'jhi-text-exercise-row-buttons',
    templateUrl: './text-exercise-row-buttons.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective, DeleteButtonDirective, ArtemisTranslatePipe],
})
export class TextExerciseRowButtonsComponent {
    private eventManager = inject(EventManager);
    private textExerciseService = inject(TextExerciseService);
    private exerciseService = inject(ExerciseService);
    private entityDeletionService = inject(EntityDeletionService);

    courseId = input.required<number>();
    course = input.required<Course>();
    exercise = input.required<TextExercise>();
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faTrash = faTrash;
    faBook = faBook;
    faWrench = faWrench;
    faUsers = faUsers;
    farListAlt = faListAlt;

    deleteExercise() {
        const exercise = this.exercise();
        this.entityDeletionService.delete(exerciseDeletionTarget(exercise), this.textExerciseService.delete(exercise.id!)).subscribe({
            next: (progress) => {
                if (isDeletionCompleted(progress)) {
                    this.eventManager.broadcast({
                        name: 'textExerciseListModification',
                        content: 'Deleted a textExercise',
                    });
                }
                // A failed deletion is reported by the progress dialog; this only ends the confirmation dialog.
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    fetchExerciseDeletionSummary(): Observable<EntitySummaryCategory[]> {
        return this.exerciseService.getDeletionSummary(this.exercise());
    }
}
