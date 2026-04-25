import { NgClass } from '@angular/common';
import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { DialogModule } from 'primeng/dialog';
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
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { AcceptCourseRequestModalComponent } from './accept-course-request-modal.component';

/**
 * Admin component for managing course creation requests.
 * Allows administrators to review, accept, or reject pending course requests.
 */
@Component({
    selector: 'jhi-course-requests-admin',
    templateUrl: './course-requests.component.html',
    imports: [
        NgClass,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        ButtonComponent,
        FormsModule,
        RouterLink,
        FaIconComponent,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        NgbPagination,
        AcceptCourseRequestModalComponent,
        DialogModule,
    ],
})
export class CourseRequestsComponent implements OnInit {
    private readonly courseRequestService = inject(CourseRequestService);
    private readonly alertService = inject(AlertService);

    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly CourseRequestStatus = CourseRequestStatus;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faExternalLinkAlt = faExternalLinkAlt;
    protected readonly faSync = faSync;

    /** Reference to the accept modal component */
    readonly acceptModal = viewChild.required(AcceptCourseRequestModalComponent);

    /** Pending course requests */
    readonly pendingRequests = signal<CourseRequest[]>([]);
    /** Decided course requests */
    readonly decidedRequests = signal<CourseRequest[]>([]);
    /** Total count of decided requests for pagination */
    readonly totalDecidedCount = signal(0);
    /** Current page for decided requests (NgbPagination uses 1-indexed pages) */
    readonly decidedPage = signal(1);
    /** Page size for decided requests */
    readonly decidedPageSize = 20;

    /** Loading state */
    readonly loading = signal(false);
    /** Currently selected request for modal operations */
    readonly selectedRequest = signal<CourseRequest | undefined>(undefined);
    /** Reason for rejection */
    readonly decisionReason = signal('');
    /** Whether reason is invalid */
    readonly reasonInvalid = signal(false);
    /** Whether the reject modal dialog is visible */
    readonly rejectModalVisible = signal(false);

    ngOnInit() {
        this.load();
    }

    load() {
        this.loading.set(true);
        // NgbPagination is 1-indexed, but API is 0-indexed
        this.courseRequestService.findAdminOverview(this.decidedPage() - 1, this.decidedPageSize).subscribe({
            next: (overview) => {
                this.pendingRequests.set(overview.pendingRequests);
                this.decidedRequests.set(overview.decidedRequests);
                this.totalDecidedCount.set(overview.totalDecidedCount);
                this.loading.set(false);
            },
            error: (error) => {
                onError(this.alertService, error);
                this.loading.set(false);
            },
        });
    }

    onDecidedPageChange() {
        this.load();
    }

    openAcceptModal(request: CourseRequest) {
        this.acceptModal().open(request, (updated) => {
            // Move from pending to decided
            this.pendingRequests.update((reqs) => reqs.filter((req) => req.id !== updated.id));
            this.decidedRequests.update((reqs) => [updated, ...reqs]);
            this.totalDecidedCount.update((count) => count + 1);
        });
    }

    openRejectModal(request: CourseRequest) {
        this.selectedRequest.set(request);
        this.decisionReason.set('');
        this.reasonInvalid.set(false);
        this.rejectModalVisible.set(true);
    }

    reject() {
        const currentRequest = this.selectedRequest();
        if (!currentRequest?.id) {
            return;
        }
        if (!this.decisionReason().trim()) {
            this.reasonInvalid.set(true);
            return;
        }
        this.courseRequestService.rejectRequest(currentRequest.id, this.decisionReason()).subscribe({
            next: (updated) => {
                // Move from pending to decided
                this.pendingRequests.update((reqs) => reqs.filter((req) => req.id !== updated.id));
                this.decidedRequests.update((reqs) => [updated, ...reqs]);
                this.totalDecidedCount.update((count) => count + 1);
                this.alertService.success('artemisApp.courseRequest.admin.rejectSuccess', { title: updated.title });
                this.rejectModalVisible.set(false);
                this.reasonInvalid.set(false);
                this.selectedRequest.set(undefined);
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

    /**
     * Formats the instructor course count for display.
     * This is only used for pending requests where the count is always computed.
     * Due to @JsonInclude(NON_EMPTY), a count of 0 is omitted and received as undefined.
     *
     * @param count the instructor course count (undefined means 0 due to JSON serialization)
     * @return "No" if count is 0/undefined, "Yes (count)" if count > 0
     */
    formatInstructorCount(count?: number): string {
        if (!count) {
            return 'No';
        }
        return `Yes (${count})`;
    }
}
