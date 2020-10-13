import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { ISubmission } from 'app/shared/model/submission.model';
import { SubmissionService } from './submission.service';
import { SubmissionDeleteDialogComponent } from './submission-delete-dialog.component';

@Component({
    selector: 'jhi-submission',
    templateUrl: './submission.component.html',
})
export class SubmissionComponent implements OnInit, OnDestroy {
    submissions?: ISubmission[];
    eventSubscriber?: Subscription;

    constructor(protected submissionService: SubmissionService, protected eventManager: JhiEventManager, protected modalService: NgbModal) {}

    loadAll(): void {
        this.submissionService.query().subscribe((res: HttpResponse<ISubmission[]>) => (this.submissions = res.body || []));
    }

    ngOnInit(): void {
        this.loadAll();
        this.registerChangeInSubmissions();
    }

    ngOnDestroy(): void {
        if (this.eventSubscriber) {
            this.eventManager.destroy(this.eventSubscriber);
        }
    }

    trackId(index: number, item: ISubmission): number {
        // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
        return item.id!;
    }

    registerChangeInSubmissions(): void {
        this.eventSubscriber = this.eventManager.subscribe('submissionListModification', () => this.loadAll());
    }

    delete(submission: ISubmission): void {
        const modalRef = this.modalService.open(SubmissionDeleteDialogComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.submission = submission;
    }
}
