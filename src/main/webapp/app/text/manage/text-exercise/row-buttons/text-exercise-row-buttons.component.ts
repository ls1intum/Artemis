import { Component, inject, input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faBook, faTable, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Course } from 'app/core/course/shared/entities/course.model';

@Component({
    selector: 'jhi-text-exercise-row-buttons',
    templateUrl: './text-exercise-row-buttons.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective, DeleteButtonDirective, ArtemisTranslatePipe],
})
export class TextExerciseRowButtonsComponent {
    private eventManager = inject(EventManager);
    private textExerciseService = inject(TextExerciseService);
    private exerciseService = inject(ExerciseService);

    course = input.required<Course>();
    exercise = input.required<TextExercise>();
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faTrash = faTrash;
    faBook = faBook;
    faWrench = faWrench;
    faUsers = faUsers;
    faTable = faTable;
    farListAlt = faListAlt;

    deleteExercise() {
        this.textExerciseService.delete(this.exercise().id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'textExerciseListModification',
                    content: 'Deleted a textExercise',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    fetchExerciseDeletionSummary(exerciseId: number): Observable<EntitySummary> {
        return this.exerciseService.getDeletionSummary(exerciseId);
    }
}
