import { Component, Input, Output, EventEmitter } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';
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
    @Input() examId: number;
    @Input() exerciseGroupId: number;
    @Output() onDeleteExercise = new EventEmitter<void>();
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
                    content: 'Deleted a textExercise',
                });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Assemble the router link for editing the exercise.
     */
    createRouterLink(type: string): any[] {
        let link = ['/course-management', this.courseId, 'text-exercises'];
        if (this.examMode) {
            link = ['/course-management', this.courseId, 'exams', this.examId, 'exercise-groups', this.exerciseGroupId, 'text-exercises'];
        }
        switch (type) {
            default:
                link = link.concat([this.textExercise.id, 'edit']);
        }
        return link;
    }
}
