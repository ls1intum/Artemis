import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';

@Component({
    selector: 'jhi-file-upload-exercise-update',
    templateUrl: './file-upload-exercise-update.component.html'
})
export class FileUploadExerciseUpdateComponent implements OnInit {
    fileUploadExercise: FileUploadExercise;
    isSaving: boolean;

    constructor(private fileUploadExerciseService: FileUploadExerciseService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ fileUploadExercise }) => {
            this.fileUploadExercise = fileUploadExercise;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.fileUploadExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.fileUploadExerciseService.update(this.fileUploadExercise));
        } else {
            this.subscribeToSaveResponse(this.fileUploadExerciseService.create(this.fileUploadExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<FileUploadExercise>>) {
        result.subscribe((res: HttpResponse<FileUploadExercise>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
