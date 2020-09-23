import { Component, Input } from '@angular/core';
import { Subject } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-modeling-exercise-row-buttons',
    templateUrl: './modeling-exercise-row-buttons.component.html',
    styles: [],
})
export class ModelingExerciseRowButtonsComponent {
    @Input() courseId: number;
    @Input() exercise: ModelingExercise;
    @Input() course: Course;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private modelingExerciseService: ModelingExerciseService, private eventManager: JhiEventManager) {}

    /**
     * delete modeling exercise
     */
    deleteModelingExercise() {
        this.modelingExerciseService.delete(this.exercise.id).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'modelingExerciseListModification',
                    content: 'Deleted an modelingExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }
}
