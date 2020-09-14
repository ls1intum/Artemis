import { Component, Input } from '@angular/core';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Subject } from 'rxjs';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { JhiEventManager } from 'ng-jhipster';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-file-upload-exercise-row-buttons',
    templateUrl: './file-upload-exercise-row-buttons.component.html',
})
export class FileUploadExerciseRowButtonsComponent {
    @Input() courseId: number;
    @Input() exercise: FileUploadExercise;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    constructor(private fileUploadExerciseService: FileUploadExerciseService, private eventManager: JhiEventManager) {}

    deleteFileUploadExercise() {
        this.fileUploadExerciseService.delete(this.exercise.id).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'fileUploadExerciseListModification',
                    content: 'Deleted an fileUploadExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }
}
