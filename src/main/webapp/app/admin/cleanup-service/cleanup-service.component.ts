import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { CleanupOperation, OperationName } from 'app/admin/cleanup-service/cleanup-operation.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { CleanupConfiguration, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { CleanupOperationModalComponent } from 'app/admin/cleanup-service/cleanup-operation-modal.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { TumUiButtonDirective, TumUiDatePickerComponent, TumUiTableDirective } from '@tumaet/ui-angular';
import { cleanupActionIcon, cleanupActionLabelKey, cleanupActionSeverity } from 'app/admin/cleanup-service/cleanup-action.util';

/** The unit a configured retention period is expressed in, used to pick the matching singular/plural label key. */
type DurationUnit = 'day' | 'week' | 'month' | 'year';

/**
 * The interpolation values of an operation's description line. Cutoffs stay dayjs objects so the template can format
 * them with the locale-reactive `artemisDate` pipe; the period is a translation key plus its count, resolved by the
 * `artemisTranslate` pipe (ngx-translate has no ICU support here, so singular and plural are separate keys).
 */
export interface OperationDescription {
    cutoff?: dayjs.Dayjs;
    periodKey?: string;
    periodCount?: number;
    secondaryCutoff?: dayjs.Dayjs;
    secondaryPeriodKey?: string;
    secondaryPeriodCount?: number;
}

/**
 * Admin component for managing data cleanup operations.
 * Allows scheduling and executing various cleanup tasks like deleting orphaned entities.
 */
@Component({
    selector: 'jhi-cleanup-service',
    templateUrl: './cleanup-service.component.html',
    imports: [
        ArtemisTranslatePipe,
        HelpIconComponent,
        TranslateDirective,
        FormsModule,
        ArtemisDatePipe,
        AdminTitleBarTitleDirective,
        CleanupOperationModalComponent,
        TumUiTableDirective,
        TumUiButtonDirective,
        TumUiDatePickerComponent,
        FaIconComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CleanupServiceComponent implements OnInit {
    private readonly dataCleanupService = inject(DataCleanupService);
    private readonly alertService = inject(AlertService);

    protected readonly cleanupActionIcon = cleanupActionIcon;
    protected readonly cleanupActionLabelKey = cleanupActionLabelKey;
    protected readonly cleanupActionSeverity = cleanupActionSeverity;

    // Maps each client operation to the server CleanupJobType.label() it corresponds to. The names differ for
    // several jobs (e.g. 'deleteOldRatedResults' -> server 'deleteRatedResults'), so the execution records must
    // be matched by this explicit job type, NOT by array position (which silently mislabels dates if the server
    // ever changes the order or the set of returned job types).
    private readonly serverJobTypeByName: Record<OperationName, string> = {
        deleteOrphans: 'deleteOrphans',
        deletePlagiarismComparisons: 'deletePlagiarismComparisons',
        deleteNonRatedResults: 'deleteNonRatedResults',
        deleteOldRatedResults: 'deleteRatedResults',
        deleteOldSubmissionVersions: 'deleteSubmissionVersions',
        deleteOldFeedback: 'deleteFeedback',
        warnOldCoursesReset: 'warnOldCoursesReset',
        resetOldCourses: 'resetOldCourses',
        deleteOldCourseSubmissionVersions: 'deleteOldCourseSubmissionVersions',
        warnNotEnrolledUsers: 'warnNotEnrolledUsers',
        deleteNotEnrolledUsers: 'deleteNotEnrolledUsers',
        deletePlagiarismCases: 'deletePlagiarismCases',
    };

    /** Whether the cleanup operation modal is visible */
    showCleanupModal = signal<boolean>(false);

    /** The currently selected operation for the modal */
    selectedOperation = signal<CleanupOperation | undefined>(undefined);

    /**
     * The effective retention cutoffs of the age-based operations, once loaded. Until then the description lines of
     * those operations render without a date rather than with a wrong one, see {@link descriptionOf}.
     */
    readonly configuration = signal<CleanupConfiguration | undefined>(undefined);

    /** Cleanup operations data - uses signal for reactivity */
    readonly cleanupOperations = signal<CleanupOperation[]>([
        {
            name: 'deleteOrphans',
            action: 'delete',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
            ageBased: true,
        },
        {
            name: 'deletePlagiarismComparisons',
            action: 'delete',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        },
        {
            name: 'deleteNonRatedResults',
            action: 'delete',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        },
        {
            name: 'deleteOldRatedResults',
            action: 'delete',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        },
        {
            name: 'deleteOldSubmissionVersions',
            action: 'delete',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        },
        // Age-based operations: no admin-picked date range, driven by configurable server-side cutoffs. They render
        // without date pickers and are always valid (deleteFromValid/deleteToValid stay true and are unused).
        {
            name: 'warnOldCoursesReset',
            action: 'warn',
            deleteFrom: undefined,
            deleteTo: undefined,
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
            ageBased: true,
        },
        {
            name: 'resetOldCourses',
            action: 'reset',
            deleteFrom: undefined,
            deleteTo: undefined,
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
            ageBased: true,
        },
        {
            name: 'deleteOldFeedback',
            action: 'delete',
            deleteFrom: undefined,
            deleteTo: undefined,
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
            ageBased: true,
        },
        {
            name: 'deleteOldCourseSubmissionVersions',
            action: 'delete',
            deleteFrom: undefined,
            deleteTo: undefined,
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
            ageBased: true,
        },
        {
            name: 'warnNotEnrolledUsers',
            action: 'warn',
            deleteFrom: undefined,
            deleteTo: undefined,
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
            ageBased: true,
        },
        {
            name: 'deleteNotEnrolledUsers',
            action: 'delete',
            deleteFrom: undefined,
            deleteTo: undefined,
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
            ageBased: true,
        },
        {
            name: 'deletePlagiarismCases',
            action: 'delete',
            deleteFrom: undefined,
            deleteTo: undefined,
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
            ageBased: true,
        },
    ]);

    ngOnInit(): void {
        this.loadLastExecutions();
        this.loadConfiguration();
    }

    loadLastExecutions(): void {
        this.dataCleanupService.getLastExecutions().subscribe({
            next: (response) => {
                const executionRecords = response.body ?? [];
                // Match by server job type, not array position (see serverJobTypeByName).
                const executionDateByJobType = new Map(executionRecords.map((record) => [record.jobType, record.executionDate]));
                this.cleanupOperations.update((operations) =>
                    operations.map((operation) => {
                        const executionDate = executionDateByJobType.get(this.serverJobTypeByName[operation.name]);
                        return executionDate ? cloneWith(operation, { lastExecuted: convertDateFromServer(executionDate) }) : operation;
                    }),
                );
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Loads the effective retention cutoffs so each age-based operation can name the data it affects.
     */
    loadConfiguration(): void {
        this.dataCleanupService.getCleanupConfiguration().subscribe({
            next: (configuration) => this.configuration.set(configuration),
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * The interpolation values for an operation's description line. Operations with an admin-picked date range describe
     * themselves through their pickers and therefore need no values at all.
     *
     * @param operation the operation whose description line is rendered
     * @return the cutoffs and periods referenced by `cleanupService.description.<operation name>`
     */
    descriptionOf(operation: CleanupOperation): OperationDescription {
        const configuration = this.configuration();
        if (!configuration) {
            return {};
        }
        switch (operation.name) {
            case 'warnOldCoursesReset':
                return describe({
                    cutoff: configuration.gradeRelevantCoursesEndedBefore,
                    unit: 'year',
                    count: configuration.gradeRelevantRetentionYears,
                    secondaryCutoff: configuration.nonGradeRelevantCoursesEndedBefore,
                    secondaryUnit: 'year',
                    secondaryCount: configuration.nonGradeRelevantRetentionYears,
                });
            case 'resetOldCourses':
                return describe({ cutoff: configuration.coursesWarnedBefore, unit: 'day', count: configuration.resetWarningGracePeriodDays });
            case 'deleteOldFeedback':
                return describe({ cutoff: configuration.oldFeedbackCoursesEndedBefore, unit: 'week', count: configuration.oldFeedbackCutoffWeeks });
            case 'deleteOldCourseSubmissionVersions':
                return describe({ cutoff: configuration.oldSubmissionVersionsCoursesEndedBefore, unit: 'week', count: configuration.oldSubmissionVersionsCutoffWeeks });
            case 'warnNotEnrolledUsers':
                return describe({ cutoff: configuration.usersInactiveBefore, unit: 'month', count: configuration.notEnrolledUsersInactivityMonths });
            case 'deleteNotEnrolledUsers':
                return describe({
                    cutoff: configuration.usersWarnedBefore,
                    unit: 'day',
                    count: configuration.notEnrolledUsersWarningGracePeriodDays,
                    secondaryCutoff: configuration.usersInactiveBefore,
                    secondaryUnit: 'month',
                    secondaryCount: configuration.notEnrolledUsersInactivityMonths,
                });
            case 'deletePlagiarismCases':
                return describe({ cutoff: configuration.gradeRelevantCoursesEndedBefore, unit: 'year', count: configuration.gradeRelevantRetentionYears });
            default:
                return {};
        }
    }

    validateDates(operation: CleanupOperation): void {
        const datesValid = !!(operation.deleteFrom && operation.deleteTo && dayjs(operation.deleteTo).isAfter(dayjs(operation.deleteFrom)));
        operation.datesValid.set(datesValid);
    }

    onDeleteFromChange(operation: CleanupOperation, value: dayjs.Dayjs | undefined): void {
        operation.deleteFrom = value;
        this.validateDates(operation);
    }

    onDeleteToChange(operation: CleanupOperation, value: dayjs.Dayjs | undefined): void {
        operation.deleteTo = value;
        this.validateDates(operation);
    }

    /**
     * Handles displaying the modal with operation details and counts.
     */
    openCleanupOperationModal(operation: CleanupOperation): void {
        this.selectedOperation.set(operation);
        this.showCleanupModal.set(true);
    }
}

interface DescriptionInput {
    cutoff: dayjs.Dayjs;
    unit: DurationUnit;
    count: number;
    secondaryCutoff?: dayjs.Dayjs;
    secondaryUnit?: DurationUnit;
    secondaryCount?: number;
}

function describe(input: DescriptionInput): OperationDescription {
    return {
        cutoff: input.cutoff,
        periodKey: durationKey(input.unit, input.count),
        periodCount: input.count,
        secondaryCutoff: input.secondaryCutoff,
        secondaryPeriodKey: input.secondaryUnit && input.secondaryCount !== undefined ? durationKey(input.secondaryUnit, input.secondaryCount) : undefined,
        secondaryPeriodCount: input.secondaryCount,
    };
}

/** Picks the singular or plural duration key for `count`, so "1 year" does not render as "1 years". */
function durationKey(unit: DurationUnit, count: number): string {
    return `cleanupService.duration.${unit}${count === 1 ? '' : 's'}`;
}
