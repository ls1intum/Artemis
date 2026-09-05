import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
    TumUiButtonComponent,
    TumUiDialogComponent,
    TumUiInputDirective,
    TumUiPaginatorComponent,
    TumUiTableDirective,
    TumUiTagComponent,
    TumUiTooltipDirective,
} from '@tumaet/ui-angular';
import { faCheck, faExternalLinkAlt, faPencil, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { BaseCourseRequest, CourseRequest, CourseRequestStatus } from 'app/course/request/course-request.model';
import { CourseRequestService } from 'app/course/request/course-request.service';
import { CourseRequestFormComponent } from 'app/course/request/course-request-form.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { onError } from 'app/foundation/util/global.utils';
import { regexValidator } from 'app/shared-ui/form/shortname-validator.directive';
import { applySemesterToDates, getCurrentAndFutureSemesters } from 'app/foundation/util/semester-utils';
import { SHORT_NAME_PATTERN } from 'app/foundation/constants/input.constants';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';

/**
 * Admin component for managing course creation requests.
 * Allows administrators to review, accept, reject, or edit pending course requests.
 */
@Component({
    selector: 'jhi-course-requests-admin',
    templateUrl: './course-requests.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        FormsModule,
        ReactiveFormsModule,
        RouterLink,
        FaIconComponent,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        TumUiPaginatorComponent,
        CourseRequestFormComponent,
        TumUiDialogComponent,
        TumUiButtonComponent,
        TumUiTableDirective,
        TumUiTagComponent,
        TumUiInputDirective,
        TumUiTooltipDirective,
    ],
})
export class CourseRequestsComponent implements OnInit {
    private readonly courseRequestService = inject(CourseRequestService);
    private readonly alertService = inject(AlertService);
    private readonly fb = inject(FormBuilder);

    protected readonly CourseRequestStatus = CourseRequestStatus;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faExternalLinkAlt = faExternalLinkAlt;
    protected readonly faSync = faSync;
    protected readonly faPencil = faPencil;
    protected readonly SHORT_NAME_PATTERN = SHORT_NAME_PATTERN;
    protected readonly semesters = getCurrentAndFutureSemesters();

    /** Pending course requests */
    readonly pendingRequests = signal<CourseRequest[]>([]);
    /** Decided course requests */
    readonly decidedRequests = signal<CourseRequest[]>([]);
    /** Total count of decided requests for pagination */
    readonly totalDecidedCount = signal(0);
    /** Current page for decided requests (1-indexed; the paginator emits 0-indexed pages) */
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
    /** Whether the edit modal dialog is visible */
    readonly editModalVisible = signal(false);

    // Edit form
    editForm = this.fb.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        shortName: ['', [Validators.required, Validators.minLength(3), regexValidator(SHORT_NAME_PATTERN)]],
        semester: ['', [Validators.required]],
        startDate: [undefined as dayjs.Dayjs | undefined, [Validators.required]],
        endDate: [undefined as dayjs.Dayjs | undefined, [Validators.required]],
        testCourse: [false],
        reason: ['', [Validators.required]],
    });
    /** Whether edit date range is invalid */
    readonly editDateRangeInvalid = signal(false);
    /** Whether edit is being submitted */
    readonly isSubmittingEdit = signal(false);

    private previousSemester?: string;

    constructor() {
        this.editForm.controls.semester.valueChanges.pipe(takeUntilDestroyed()).subscribe((semester) => {
            this.applySemesterDateRange(semester ?? undefined);
        });
    }

    /**
     * Applies the range of the newly selected semester to the two date controls, unless the admin picked a date by
     * hand.
     *
     * @param semester the newly selected semester
     */
    private applySemesterDateRange(semester: string | undefined): void {
        const { startDate, endDate } = applySemesterToDates(
            semester,
            this.previousSemester,
            this.editForm.controls.startDate.value ?? undefined,
            this.editForm.controls.endDate.value ?? undefined,
        );
        this.previousSemester = semester;
        this.editForm.controls.startDate.setValue(startDate);
        this.editForm.controls.endDate.setValue(endDate);
    }

    ngOnInit() {
        this.load();
    }

    load() {
        this.loading.set(true);
        // decidedPage is 1-indexed, but the API is 0-indexed
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

    /** Handles a paginator page change for the decided requests by converting the 0-indexed emitted page to the 1-indexed page and reloading. */
    onDecidedPaginatorChange(page: number): void {
        this.decidedPage.set(page + 1);
        this.onDecidedPageChange();
    }

    accept(request: CourseRequest) {
        if (!request.id) {
            return;
        }
        this.courseRequestService.acceptRequest(request.id).subscribe({
            next: (updated) => {
                // Move from pending to decided
                this.pendingRequests.update((reqs) => reqs.filter((req) => req.id !== updated.id));
                this.decidedRequests.update((reqs) => [updated, ...reqs]);
                this.totalDecidedCount.update((count) => count + 1);
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

    badgeSeverity(status?: CourseRequestStatus): 'success' | 'danger' | 'secondary' {
        switch (status) {
            case CourseRequestStatus.ACCEPTED:
                return 'success';
            case CourseRequestStatus.REJECTED:
                return 'danger';
            default:
                return 'secondary';
        }
    }

    openEditModal(request: CourseRequest) {
        this.selectedRequest.set(request);
        this.editDateRangeInvalid.set(false);
        this.isSubmittingEdit.set(false);
        // Must be set before reset(): reset() emits the semester valueChanges synchronously, and
        // applySemesterDateRange needs previousSemester to already reflect this request, not the one
        // that was open before it, or it judges this request's own stored dates against the wrong range.
        this.previousSemester = request.semester;
        // Computed explicitly, rather than left to the reset-triggered valueChanges above, because reset()
        // applies startDate/endDate after semester and would otherwise overwrite whatever that callback just
        // derived: a request without dates yet needs them filled from its own semester right here.
        const { startDate, endDate } = applySemesterToDates(request.semester, request.semester, request.startDate, request.endDate);
        this.editForm.reset({
            title: request.title,
            shortName: request.shortName,
            semester: request.semester ?? '',
            startDate,
            endDate,
            testCourse: request.testCourse ?? false,
            reason: request.reason,
        });
        this.editModalVisible.set(true);
    }

    saveEdit() {
        this.editDateRangeInvalid.set(false);
        const currentRequest = this.selectedRequest();
        if (this.editForm.invalid || !currentRequest?.id) {
            this.editForm.markAllAsTouched();
            return;
        }

        const startDate = this.editForm.get('startDate')!.value!;
        const endDate = this.editForm.get('endDate')!.value!;
        if (!startDate.isBefore(endDate)) {
            this.editDateRangeInvalid.set(true);
            return;
        }

        const payload: BaseCourseRequest = {
            title: this.editForm.get('title')!.value!,
            shortName: this.editForm.get('shortName')!.value!,
            semester: this.editForm.get('semester')!.value!,
            startDate,
            endDate,
            testCourse: this.editForm.get('testCourse')!.value ?? false,
            reason: this.editForm.get('reason')!.value!,
        };

        this.isSubmittingEdit.set(true);
        this.courseRequestService.updateRequest(currentRequest.id, payload).subscribe({
            next: (updated) => {
                // Update the request in the list
                this.pendingRequests.update((reqs) => {
                    const index = reqs.findIndex((req) => req.id === updated.id);
                    if (index !== -1) {
                        const newReqs = [...reqs];
                        newReqs[index] = updated;
                        return newReqs;
                    }
                    return reqs;
                });
                this.alertService.success('artemisApp.courseRequest.admin.editSuccess');
                this.editModalVisible.set(false);
                this.isSubmittingEdit.set(false);
                this.selectedRequest.set(undefined);
            },
            error: (error: HttpErrorResponse) => {
                this.handleEditError(error);
                this.isSubmittingEdit.set(false);
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
