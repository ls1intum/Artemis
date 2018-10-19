import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IFileUploadExercise } from 'app/shared/model/file-upload-exercise.model';

@Component({
    selector: 'jhi-file-upload-exercise-detail',
    templateUrl: './file-upload-exercise-detail.component.html'
})
export class FileUploadExerciseDetailComponent implements OnInit {
    fileUploadExercise: IFileUploadExercise;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ fileUploadExercise }) => {
            this.fileUploadExercise = fileUploadExercise;
        });
    }

    previousState() {
        window.history.back();
    }
}
