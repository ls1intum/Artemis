import { Subject } from 'rxjs';
import { Component, Input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faBook, faTable, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';

import { MathExercise } from 'app/entities/math-exercise.model';
import { MathExerciseManageService } from 'app/exercises/math/manage/math-exercise-manage.service';
import { EventManager } from 'app/core/util/event-manager.service';

@Component({
    selector: 'jhi-math-exercise-row-buttons',
    templateUrl: './math-exercise-row-buttons.component.html',
    styleUrls: ['./math-exercise-row-buttons.component.scss'],
})
export class MathExerciseRowButtonsComponent {
    @Input() courseId: number;
    @Input() exercise: MathExercise;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faTrash = faTrash;
    faBook = faBook;
    faWrench = faWrench;
    faUsers = faUsers;
    faTable = faTable;
    farListAlt = faListAlt;

    constructor(
        private mathExerciseService: MathExerciseManageService,
        private eventManager: EventManager,
    ) {}

    deleteExercise() {
        this.mathExerciseService.delete(this.exercise.id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'mathExerciseListModification',
                    content: 'Deleted a math exercise',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
