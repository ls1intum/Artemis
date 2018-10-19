import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IFileUploadExercise } from 'app/shared/model/file-upload-exercise.model';
import { Principal } from 'app/core';
import { FileUploadExerciseService } from './file-upload-exercise.service';

@Component({
    selector: 'jhi-file-upload-exercise',
    templateUrl: './file-upload-exercise.component.html'
})
export class FileUploadExerciseComponent implements OnInit, OnDestroy {
    fileUploadExercises: IFileUploadExercise[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.fileUploadExerciseService.query().subscribe(
            (res: HttpResponse<IFileUploadExercise[]>) => {
                this.fileUploadExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInFileUploadExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IFileUploadExercise) {
        return item.id;
    }

    registerChangeInFileUploadExercises() {
        this.eventSubscriber = this.eventManager.subscribe('fileUploadExerciseListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
