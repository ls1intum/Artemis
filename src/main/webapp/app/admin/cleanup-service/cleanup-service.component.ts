import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { EMPTY } from 'rxjs';
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
import { CLEANUP_ACTION_PRESENTATION } from 'app/admin/cleanup-service/cleanup-action.util';

/** The unit a configured retention period is expressed in, used to pick the matching singular/plural label key. */
type DurationUnit = 'day' | 'week' | 'month' | 'year';

/**
 * The already-formatted interpolation values of an operation's description line. A type alias rather than an interface,
 * so that it keeps the implicit index signature `translateValues` requires.
 */
export type OperationDescription = {
    cutoff: string;
    period: string;
    secondaryCutoff?: string;
    secondaryPeriod?: string;
};

/** Which cutoff and period of the configuration an operation's description line quotes. */
interface DescriptionSource {
    cutoff: (configuration: CleanupConfiguration) => dayjs.Dayjs;
    unit: DurationUnit;
    count: (configuration: CleanupConfiguration) => number;
    secondaryCutoff?: (configuration: CleanupConfiguration) => dayjs.Dayjs;
    secondaryUnit?: DurationUnit;
    secondaryCount?: (configuration: CleanupConfiguration) => number;
}

/**
 * The cutoff each age-based operation's description line quotes, mirroring what the corresponding server job filters on.
 * Operations missing here describe themselves without a date: the four date-range ones state their scope through their
 * pickers, and `deleteOrphans` has no time bound at all.
 */
const DESCRIPTION_SOURCES: Partial<Record<OperationName, DescriptionSource>> = {
    // Both retention periods are named, since which one applies depends on the course's grade relevance.
    warnOldCoursesReset: {
        cutoff: (configuration) => configuration.gradeRelevantCoursesEndedBefore,
        unit: 'year',
        count: (configuration) => configuration.gradeRelevantRetentionYears,
        secondaryCutoff: (configuration) => configuration.nonGradeRelevantCoursesEndedBefore,
        secondaryUnit: 'year',
        secondaryCount: (configuration) => configuration.nonGradeRelevantRetentionYears,
    },
    // The grace period runs from the warning, not from the course end, so this quotes the warning cutoff.
    resetOldCourses: { cutoff: (configuration) => configuration.coursesWarnedBefore, unit: 'day', count: (configuration) => configuration.resetWarningGracePeriodDays },
    deleteOldFeedback: { cutoff: (configuration) => configuration.oldFeedbackCoursesEndedBefore, unit: 'week', count: (configuration) => configuration.oldFeedbackCutoffWeeks },
    deleteOldCourseSubmissionVersions: {
        cutoff: (configuration) => configuration.oldSubmissionVersionsCoursesEndedBefore,
        unit: 'week',
        count: (configuration) => configuration.oldSubmissionVersionsCutoffWeeks,
    },
    warnNotEnrolledUsers: { cutoff: (configuration) => configuration.usersInactiveBefore, unit: 'month', count: (configuration) => configuration.notEnrolledUsersInactivityMonths },
    // Deliberately no second cutoff: phase 2 compares each user's last login against their own warning date, which no
    // global cutoff can express. The description states that rule in words instead.
    deleteNotEnrolledUsers: {
        cutoff: (configuration) => configuration.usersWarnedBefore,
        unit: 'day',
        count: (configuration) => configuration.notEnrolledUsersWarningGracePeriodDays,
    },
    deletePlagiarismCases: {
        cutoff: (configuration) => configuration.gradeRelevantCoursesEndedBefore,
        unit: 'year',
        count: (configuration) => configuration.gradeRelevantRetentionYears,
    },
};

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
    private readonly translateService = inject(TranslateService);
    private readonly datePipe = inject(ArtemisDatePipe);

    // Reading this in a computed re-resolves the description lines on a language change, the way the pipes would.
    private readonly languageChange = toSignal(this.translateService.onLangChange ?? EMPTY);

    protected readonly actionPresentation = CLEANUP_ACTION_PRESENTATION;

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
     * The effective retention cutoffs of the age-based operations, once loaded. Until then (and if the request fails)
     * those operations fall back to a description without a date rather than showing a wrong one, see {@link descriptions}.
     */
    readonly configuration = signal<CleanupConfiguration | undefined>(undefined);

    /** Whether the configuration request failed, which turns "not loaded yet" from a transient state into a permanent one. */
    readonly configurationFailed = signal(false);

    /** The operations whose description line quotes a cutoff, and therefore cannot be rendered without the configuration. */
    protected readonly quotesCutoff: Partial<Record<OperationName, boolean>> = Object.fromEntries(Object.keys(DESCRIPTION_SOURCES).map((name) => [name, true]));

    /**
     * The interpolation values of each age-based operation's description line, with the cutoff and the period already
     * formatted. Resolved here rather than by pipes in the template so that the object handed to `translateValues` keeps
     * its identity between change detections; reading {@link languageChange} keeps it locale-reactive all the same.
     * An operation is absent while the configuration is unknown, which is what makes the template fall back.
     */
    readonly descriptions = computed<Partial<Record<OperationName, OperationDescription>>>(() => {
        this.languageChange();
        const configuration = this.configuration();
        if (!configuration) {
            return {};
        }
        const descriptions: Partial<Record<OperationName, OperationDescription>> = {};
        for (const [name, source] of Object.entries(DESCRIPTION_SOURCES) as [OperationName, DescriptionSource][]) {
            descriptions[name] = {
                cutoff: this.datePipe.transform(source.cutoff(configuration), 'long-date'),
                period: this.duration(source.unit, source.count(configuration)),
                secondaryCutoff: source.secondaryCutoff ? this.datePipe.transform(source.secondaryCutoff(configuration), 'long-date') : undefined,
                secondaryPeriod: source.secondaryUnit && source.secondaryCount ? this.duration(source.secondaryUnit, source.secondaryCount(configuration)) : undefined,
            };
        }
        return descriptions;
    });

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
            error: (error: HttpErrorResponse) => {
                // The alert disappears, so the affected rows have to keep saying that their scope is unknown.
                this.configurationFailed.set(true);
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Resolves a configured period to a localized label, picking the singular or plural key so that a period of one
     * does not read "1 years" (ngx-translate has no ICU support here).
     */
    private duration(unit: DurationUnit, count: number): string {
        return this.translateService.instant(`cleanupService.duration.${unit}${count === 1 ? '' : 's'}`, { count });
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
