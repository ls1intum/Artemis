import { Component, Input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { EventManager } from 'app/core/util/event-manager.service';

@Component({
    selector: 'jhi-text-exercise-row-buttons',
    templateUrl: './text-exercise-row-buttons.component.html',
})
export class TextExerciseRowButtonsComponent {
    @Input() courseId: number;
    @Input() exercise: TextExercise;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private textExerciseService: TextExerciseService, private eventManager: EventManager) {}

    deleteExercise() {
        this.textExerciseService.delete(this.exercise.id!).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'textExerciseListModification',
                    content: 'Deleted a textExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }
}
