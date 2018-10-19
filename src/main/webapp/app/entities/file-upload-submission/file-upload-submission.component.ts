import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IFileUploadSubmission } from 'app/shared/model/file-upload-submission.model';
import { Principal } from 'app/core';
import { FileUploadSubmissionService } from './file-upload-submission.service';

@Component({
    selector: 'jhi-file-upload-submission',
    templateUrl: './file-upload-submission.component.html'
})
export class FileUploadSubmissionComponent implements OnInit, OnDestroy {
    fileUploadSubmissions: IFileUploadSubmission[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.fileUploadSubmissionService.query().subscribe(
            (res: HttpResponse<IFileUploadSubmission[]>) => {
                this.fileUploadSubmissions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInFileUploadSubmissions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IFileUploadSubmission) {
        return item.id;
    }

    registerChangeInFileUploadSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe('fileUploadSubmissionListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
