import { NgClass } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { NgbModal, NgbModalRef, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { faCheck, faEdit, faExternalLinkAlt, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { BaseCourseRequest, CourseRequest, CourseRequestStatus } from 'app/core/shared/entities/course-request.model';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { CourseRequestFormComponent } from 'app/core/course/request/course-request-form.component';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { onError } from 'app/shared/util/global.utils';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';
import { getCurrentAndFutureSemesters } from 'app/shared/util/semester-utils';
import { SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';

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
        ReactiveFormsModule,
        RouterLink,
        FaIconComponent,
        NgbPagination,
        CourseRequestFormComponent,
    ],
})
export class CourseRequestsComponent implements OnInit {
    private courseRequestService = inject(CourseRequestService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private fb = inject(FormBuilder);

    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly CourseRequestStatus = CourseRequestStatus;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faExternalLinkAlt = faExternalLinkAlt;
    protected readonly faSync = faSync;
    protected readonly faEdit = faEdit;
    protected readonly SHORT_NAME_PATTERN = SHORT_NAME_PATTERN;
    protected readonly semesters = getCurrentAndFutureSemesters();

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

    // Edit form
    editForm = this.fb.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        shortName: ['', [Validators.required, Validators.minLength(3), regexValidator(SHORT_NAME_PATTERN)]],
        semester: ['', [Validators.required]],
        startDate: [undefined as any],
        endDate: [undefined as any],
        testCourse: [false],
        reason: ['', [Validators.required]],
    });
    editDateRangeInvalid = false;
    isSubmittingEdit = false;

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
            error: (error: HttpErrorResponse) => this.handleAcceptError(error, request),
        });
    }

    private handleAcceptError(error: HttpErrorResponse, request: CourseRequest): void {
        const errorKey = error.error?.errorKey;
        const isShortNameConflict = errorKey === 'courseShortNameExists' || errorKey === 'courseRequestShortNameExists';

        if (isShortNameConflict) {
            const suggestedShortName = error.error?.params?.suggestedShortName;
            this.alertService.warning('artemisApp.courseRequest.admin.shortNameConflict', { suggestedShortName: suggestedShortName ?? '', shortName: request.shortName });
            return;
        }

        onError(this.alertService, error);
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

    openEditModal(content: any, request: CourseRequest) {
        this.selectedRequest = request;
        this.editDateRangeInvalid = false;
        this.isSubmittingEdit = false;
        this.editForm.reset({
            title: request.title,
            shortName: request.shortName,
            semester: request.semester ?? '',
            startDate: request.startDate,
            endDate: request.endDate,
            testCourse: request.testCourse ?? false,
            reason: request.reason,
        });
        this.modalRef = this.modalService.open(content, { size: 'lg' });
    }

    saveEdit() {
        this.editDateRangeInvalid = false;
        if (this.editForm.invalid || !this.selectedRequest?.id) {
            this.editForm.markAllAsTouched();
            return;
        }

        const startDate = this.editForm.get('startDate')!.value;
        const endDate = this.editForm.get('endDate')!.value;
        if (startDate && endDate && !startDate.isBefore(endDate)) {
            this.editDateRangeInvalid = true;
            return;
        }

        const payload: BaseCourseRequest = {
            title: this.editForm.get('title')!.value!,
            shortName: this.editForm.get('shortName')!.value!,
            semester: this.editForm.get('semester')!.value ?? undefined,
            startDate,
            endDate,
            testCourse: this.editForm.get('testCourse')!.value ?? false,
            reason: this.editForm.get('reason')!.value!,
        };

        this.isSubmittingEdit = true;
        this.courseRequestService.updateRequest(this.selectedRequest.id, payload).subscribe({
            next: (updated) => {
                // Update the request in the list
                const index = this.pendingRequests.findIndex((req) => req.id === updated.id);
                if (index !== -1) {
                    this.pendingRequests[index] = updated;
                }
                this.alertService.success('artemisApp.courseRequest.admin.editSuccess');
                this.modalRef?.close();
                this.isSubmittingEdit = false;
                this.selectedRequest = undefined;
            },
            error: (error: HttpErrorResponse) => {
                this.handleEditError(error);
                this.isSubmittingEdit = false;
            },
        });
    }

    private handleEditError(error: HttpErrorResponse): void {
        const errorKey = error.error?.errorKey;
        const isShortNameConflict = errorKey === 'courseShortNameExists' || errorKey === 'courseRequestShortNameExists';

        if (isShortNameConflict) {
            const suggestedShortName = error.error?.params?.suggestedShortName;
            this.alertService.warning('artemisApp.courseRequest.form.shortNameNotUnique', { suggestedShortName: suggestedShortName ?? '' });
            if (suggestedShortName) {
                this.editForm.patchValue({ shortName: suggestedShortName });
            }
            return;
        }

        onError(this.alertService, error);
    }
}
