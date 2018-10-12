import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { FileUploadExercise } from './file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';

@Component({
    selector: 'jhi-file-upload-exercise-detail',
    templateUrl: './file-upload-exercise-detail.component.html'
})
export class FileUploadExerciseDetailComponent implements OnInit, OnDestroy {
    fileUploadExercise: FileUploadExercise;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private fileUploadExerciseService: FileUploadExerciseService,
        private route: ActivatedRoute
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load(params['id']);
        });
        this.registerChangeInFileUploadExercises();
    }

    load(id: number) {
        this.fileUploadExerciseService.find(id).subscribe((fileUploadExerciseResponse: HttpResponse<FileUploadExercise>) => {
            this.fileUploadExercise = fileUploadExerciseResponse.body;
        });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInFileUploadExercises() {
        this.eventSubscriber = this.eventManager.subscribe('fileUploadExerciseListModification', () =>
            this.load(this.fileUploadExercise.id)
        );
    }
}
