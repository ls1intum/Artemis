import { Component, Input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';

@Component({
    selector: 'jhi-text-exercise-row-buttons',
    templateUrl: './text-exercise-row-buttons.component.html',
})
export class TextExerciseRowButtonsComponent {
    @Input() courseId: number;
    @Input() textExercise: TextExercise;
    @Input() examMode = false;
    @Input() exerciseGroupId: number;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private textExerciseService: TextExerciseService, private eventManager: JhiEventManager) {}

    /**
     * Deletes the text exercise
     * @param textExerciseId id of the exercise that will be deleted
     */
    deleteTextExercise(textExerciseId: number) {
        this.textExerciseService.delete(textExerciseId).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'textExerciseListModification',
                    content: 'Deleted an textExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Assemble the router link for editing the exercise.
     */
    createRouterLinkForEdit(): any[] {
        if (this.examMode) {
            return [this.exerciseGroupId, 'text-exercises', this.textExercise.id, 'edit'];
        }
        return ['/course-management', this.courseId, 'text-exercises', this.textExercise.id, 'edit'];
    }
}
