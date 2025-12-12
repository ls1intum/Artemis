import { NgClass } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { NgbModal, NgbModalRef, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { faCheck, faExternalLinkAlt, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';

import { CourseRequest, CourseRequestStatus } from 'app/core/shared/entities/course-request.model';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FormsModule } from '@angular/forms';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-course-requests-admin',
    templateUrl: './course-requests.component.html',
    imports: [NgClass, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, ButtonComponent, FormsModule, RouterLink, FaIconComponent, NgbPagination],
})
export class CourseRequestsComponent implements OnInit {
    private courseRequestService = inject(CourseRequestService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);

    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly CourseRequestStatus = CourseRequestStatus;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faExternalLinkAlt = faExternalLinkAlt;
    protected readonly faSync = faSync;

    pendingRequests: CourseRequest[] = [];
    decidedRequests: CourseRequest[] = [];
    totalDecidedCount = 0;
    decidedPage = 1; // NgbPagination uses 1-indexed pages
    decidedPageSize = 20;

    loading = false;
    selectedRequest?: CourseRequest;
    decisionReason = '';
    reasonInvalid = false;
    modalRef?: NgbModalRef;

    ngOnInit() {
        this.load();
    }

    load() {
        this.loading = true;
        // NgbPagination is 1-indexed, but API is 0-indexed
        this.courseRequestService.findAdminOverview(this.decidedPage - 1, this.decidedPageSize).subscribe({
            next: (overview) => {
                this.pendingRequests = overview.pendingRequests;
                this.decidedRequests = overview.decidedRequests;
                this.totalDecidedCount = overview.totalDecidedCount;
                this.loading = false;
            },
            error: (error) => {
                onError(this.alertService, error);
                this.loading = false;
            },
        });
    }

    onDecidedPageChange() {
        this.load();
    }

    accept(request: CourseRequest) {
        if (!request.id) {
            return;
        }
        this.courseRequestService.acceptRequest(request.id).subscribe({
            next: (updated) => {
                // Move from pending to decided
                this.pendingRequests = this.pendingRequests.filter((req) => req.id !== updated.id);
                this.decidedRequests = [updated, ...this.decidedRequests];
                this.totalDecidedCount++;
                this.alertService.success('artemisApp.courseRequest.admin.acceptSuccess', { title: updated.title, shortName: updated.shortName });
            },
            error: (error) => onError(this.alertService, error),
        });
    }

    openRejectModal(content: any, request: CourseRequest) {
        this.selectedRequest = request;
        this.decisionReason = '';
        this.reasonInvalid = false;
        this.modalRef = this.modalService.open(content, { size: 'lg' });
    }

    reject() {
        if (!this.selectedRequest?.id) {
            return;
        }
        if (!this.decisionReason.trim()) {
            this.reasonInvalid = true;
            return;
        }
        this.courseRequestService.rejectRequest(this.selectedRequest.id, this.decisionReason).subscribe({
            next: (updated) => {
                // Move from pending to decided
                this.pendingRequests = this.pendingRequests.filter((req) => req.id !== updated.id);
                this.decidedRequests = [updated, ...this.decidedRequests];
                this.totalDecidedCount++;
                this.alertService.success('artemisApp.courseRequest.admin.rejectSuccess', { title: updated.title });
                this.modalRef?.close();
                this.reasonInvalid = false;
                this.selectedRequest = undefined;
            },
            error: (error) => onError(this.alertService, error),
        });
    }

    badgeClass(status?: CourseRequestStatus) {
        switch (status) {
            case CourseRequestStatus.ACCEPTED:
                return 'bg-success';
            case CourseRequestStatus.REJECTED:
                return 'bg-danger';
            default:
                return 'bg-secondary';
        }
    }

    formatInstructorCount(count?: number): string {
        if (count === undefined || count === null) {
            return '-';
        }
        return count > 0 ? `Yes (${count})` : 'No';
    }
}
