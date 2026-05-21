import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { CourseOperationProgressDTO, CourseOperationStatus, CourseOperationType } from 'app/core/course/shared/entities/course-operation-progress.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faExclamationTriangle, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { ProgressBarModule } from 'primeng/progressbar';

// Minimum interval between UI updates to prevent flickering (in milliseconds)
const UPDATE_BUFFER_MS = 150;

@Component({
    selector: 'jhi-course-operation-progress',
    standalone: true,
    imports: [CommonModule, FaIconComponent, DialogModule, ProgressBarModule],
    templateUrl: './course-operation-progress.component.html',
    styleUrls: ['./course-operation-progress.component.scss'],
})
export class CourseOperationProgressComponent {
    private translateService = inject(TranslateService);

    progress = input<CourseOperationProgressDTO | undefined>(undefined);
    closeOverlay = output<void>();

    protected readonly faSpinner = faSpinner;
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faExclamationTriangle = faExclamationTriangle;

    // Buffered progress to prevent UI flickering on rapid updates
    private lastUpdateTime = 0;
    private pendingUpdate: CourseOperationProgressDTO | undefined;
    private updateTimer: ReturnType<typeof setTimeout> | undefined;
    bufferedProgress = signal<CourseOperationProgressDTO | undefined>(undefined);

    constructor() {
        // Effect to buffer rapid progress updates
        effect(() => {
            const newProgress = this.progress();
            this.scheduleUpdate(newProgress);
        });
    }

    /**
     * Schedules a buffered update to prevent UI flickering.
     * Updates are throttled to occur at most once per UPDATE_BUFFER_MS.
     * Status changes (completed/failed) are applied immediately.
     */
    private scheduleUpdate(newProgress: CourseOperationProgressDTO | undefined): void {
        const now = Date.now();
        const timeSinceLastUpdate = now - this.lastUpdateTime;

        // Always update immediately for status changes or initial/final states
        const isStatusChange =
            newProgress?.status !== this.bufferedProgress()?.status ||
            newProgress?.status === CourseOperationStatus.COMPLETED ||
            newProgress?.status === CourseOperationStatus.FAILED;

        if (isStatusChange || !newProgress || timeSinceLastUpdate >= UPDATE_BUFFER_MS) {
            this.applyUpdate(newProgress);
        } else {
            // Buffer the update
            this.pendingUpdate = newProgress;
            if (!this.updateTimer) {
                const delay = UPDATE_BUFFER_MS - timeSinceLastUpdate;
                this.updateTimer = setTimeout(() => {
                    this.updateTimer = undefined;
                    if (this.pendingUpdate) {
                        this.applyUpdate(this.pendingUpdate);
                        this.pendingUpdate = undefined;
                    }
                }, delay);
            }
        }
    }

    private applyUpdate(progress: CourseOperationProgressDTO | undefined): void {
        this.lastUpdateTime = Date.now();
        this.bufferedProgress.set(progress);
    }

    // Dialog visibility - show when there's progress data
    dialogVisible = computed(() => !!this.bufferedProgress());

    isInProgress = computed(() => this.bufferedProgress()?.status === CourseOperationStatus.IN_PROGRESS);
    isCompleted = computed(() => this.bufferedProgress()?.status === CourseOperationStatus.COMPLETED);
    isFailed = computed(() => this.bufferedProgress()?.status === CourseOperationStatus.FAILED);

    /**
     * Returns the weighted progress percentage (0-100) based on operation complexity.
     * Uses the server-calculated weighted progress which accounts for:
     * - Exercise type complexity (programming > other)
     * - Number of participations, submissions, and results
     * - Number of student exams for exam operations
     * - Number of posts and other entities
     */
    progressPercentage = computed(() => {
        const p = this.bufferedProgress();
        if (!p) return 0;
        return Math.round(p.weightedProgressPercent);
    });

    /**
     * Returns the CSS class for the PrimeNG progress bar based on status.
     */
    progressStyleClass = computed(() => {
        if (this.isFailed()) return 'progress-danger';
        if (this.isCompleted()) return 'progress-success';
        return '';
    });

    operationTitle = computed(() => {
        const operationType = this.bufferedProgress()?.operationType;
        switch (operationType) {
            case CourseOperationType.DELETE:
                return this.translateService.instant('artemisApp.course.operationProgress.delete');
            case CourseOperationType.RESET:
                return this.translateService.instant('artemisApp.course.operationProgress.reset');
            case CourseOperationType.ARCHIVE:
                return this.translateService.instant('artemisApp.course.operationProgress.archive');
            case CourseOperationType.IMPORT:
                return this.translateService.instant('artemisApp.course.operationProgress.import');
            default:
                return '';
        }
    });

    completedMessage = computed(() => this.translateService.instant('artemisApp.course.operationProgress.completed'));

    failedMessage = computed(() => this.translateService.instant('artemisApp.course.operationProgress.failed'));

    /**
     * Calculates the estimated time remaining based on weighted progress.
     * Uses the weighted progress percentage which provides more accurate estimates
     * than simple step counting, as it accounts for operation complexity.
     */
    eta = computed(() => {
        const p = this.bufferedProgress();
        if (!p || !p.startedAt || p.weightedProgressPercent <= 0) {
            return undefined;
        }

        const remainingPercent = 100 - p.weightedProgressPercent;
        if (remainingPercent <= 0) {
            return undefined;
        }

        const now = dayjs();
        const startedAt = dayjs(p.startedAt);
        const passedSeconds = now.diff(startedAt, 'second');

        if (passedSeconds <= 0) {
            return undefined;
        }

        // Calculate remaining time based on weighted progress
        const remainingSeconds = (passedSeconds / p.weightedProgressPercent) * remainingPercent;

        const hours = Math.floor(remainingSeconds / 3600);
        const minutes = Math.floor((remainingSeconds % 3600) / 60);
        const seconds = Math.floor(remainingSeconds % 60);

        let result = '';
        if (hours > 0) result += hours + 'h ';
        if (minutes > 0 || hours > 0) result += minutes + 'm ';
        result += seconds + 's';

        return result.trim();
    });

    onClose() {
        this.closeOverlay.emit();
    }
}
