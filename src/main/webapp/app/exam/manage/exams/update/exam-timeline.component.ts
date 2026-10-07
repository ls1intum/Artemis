import { Component, Signal, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Dayjs } from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiDatePickerComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { InvalidTimelineItem, TimelineStatus } from 'app/shared-ui/timeline/timeline.component';

/** One date of the exam timeline. The dates are required and must be in strictly ascending order. */
interface ExamTimelineItem {
    /** Stable key of the item, also used for the id of its date field. */
    key: 'visibleDate' | 'startDate' | 'endDate';
    labelStringKey: string;
    date: Signal<Dayjs | undefined>;
    setDate: (date: Dayjs | undefined) => void;
    warningStringKey?: Signal<string | undefined>;
}

interface InternalExamTimelineItem {
    item: ExamTimelineItem;
    inputId: string;
    date: Dayjs | undefined;
    hasInvalidDateOrder: boolean;
    isInvalidInput: boolean;
    isInputRequiredButUndefined: boolean;
    hasWarning: boolean;
    isInvalid: boolean;
    tooltipStringKey: string | undefined;
}

/**
 * The three dates of an exam (visible from, start and end of the working time or window) as a vertical timeline
 * with a required marker per date. It reports its validity through {@link timelineStatusChange}.
 */
@Component({
    selector: 'jhi-exam-timeline',
    imports: [FaIconComponent, TumAetUiDatePickerComponent, TumAetUiTooltipDirective, ArtemisTranslatePipe],
    templateUrl: './exam-timeline.component.html',
})
export class ExamTimelineComponent {
    private readonly translateService = inject(TranslateService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    protected readonly faCircleInfo = faCircleInfo;

    readonly testExam = input(false);
    readonly visibleDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly endDate = model<Dayjs | undefined>();
    readonly timelineStatusChange = output<TimelineStatus>();
    readonly datesChanged = output<void>();

    /** Keys of the items whose typed text is not a date, as reported by their date field. */
    private readonly invalidInputKeys = signal<ReadonlySet<string>>(new Set());
    /** Keys of the items that the user has left once, so that a missing date is only flagged after that. */
    private readonly touchedKeys = signal<ReadonlySet<string>>(new Set());

    private readonly visibleDateWarningStringKey = computed<string | undefined>(() => {
        const visibleDate = this.visibleDate();
        const startDate = this.startDate();
        if (visibleDate === undefined || startDate === undefined) {
            return undefined;
        }
        return startDate.diff(visibleDate, 'minute') > 240 ? 'entity.visibleDateWarningError' : undefined;
    });

    readonly timelineItems = computed<ExamTimelineItem[]>(() => {
        const testExamKeyPart = this.testExam() ? '.testExam' : '';
        return [
            {
                key: 'visibleDate',
                labelStringKey: 'artemisApp.examManagement.visibleDate',
                date: this.visibleDate,
                setDate: (date) => this.visibleDate.set(date),
                warningStringKey: this.visibleDateWarningStringKey,
            },
            {
                key: 'startDate',
                labelStringKey: `artemisApp.examManagement${testExamKeyPart}.startDate`,
                date: this.startDate,
                setDate: (date) => this.startDate.set(date),
            },
            {
                key: 'endDate',
                labelStringKey: `artemisApp.examManagement${testExamKeyPart}.endDate`,
                date: this.endDate,
                setDate: (date) => this.endDate.set(date),
            },
        ];
    });

    protected readonly internalTimelineItems = computed<InternalExamTimelineItem[]>(() => {
        const invalidInputKeys = this.invalidInputKeys();
        const touchedKeys = this.touchedKeys();
        return this.timelineItems().map((item, index, items) => {
            const date = item.date();
            const hasInvalidDateOrder =
                date !== undefined &&
                items.slice(0, index).some((previousItem) => {
                    const previousDate = previousItem.date();
                    return previousDate !== undefined && !date.isAfter(previousDate);
                });
            const isInputRequiredButUndefined = date === undefined;
            const isInvalidInput = invalidInputKeys.has(item.key);
            const hasInternalError = isInvalidInput || hasInvalidDateOrder || isInputRequiredButUndefined;
            const warningStringKey = item.warningStringKey?.();
            const hasWarning = !hasInternalError && warningStringKey !== undefined;
            // A missing date is only painted as an error once the field was left, but it always keeps the timeline invalid.
            const isInvalid = isInvalidInput || hasInvalidDateOrder || (touchedKeys.has(item.key) && isInputRequiredButUndefined);
            let tooltipStringKey: string | undefined;
            if (isInvalidInput) {
                tooltipStringKey = 'artemisApp.exercise.timelineDateInvalidTooltip';
            } else if (hasInvalidDateOrder) {
                tooltipStringKey = 'artemisApp.exercise.timelineDateStrictOrderTooltip';
            } else if (isInputRequiredButUndefined) {
                tooltipStringKey = 'artemisApp.exercise.timelineDateRequiredTooltip';
            } else {
                tooltipStringKey = warningStringKey;
            }
            return {
                item,
                inputId: `exam-${item.key}`,
                date,
                hasInvalidDateOrder,
                isInvalidInput,
                isInputRequiredButUndefined,
                hasWarning,
                isInvalid,
                tooltipStringKey,
            };
        });
    });

    readonly timelineStatus = computed<TimelineStatus>(() => {
        // Recomputed on a language change, since the names of the dates are part of the status.
        this.currentLocale();
        const items = this.internalTimelineItems();
        const invalidItems: InvalidTimelineItem[] = items.flatMap((internalItem) => {
            const reasonKey = this.determineInvalidReasonKey(internalItem);
            if (reasonKey === undefined) {
                return [];
            }
            return [{ labelStringKey: internalItem.item.labelStringKey, reasonKey, dateName: this.translateService.instant(internalItem.item.labelStringKey) }];
        });
        return {
            valid: invalidItems.length === 0,
            empty: items.some((internalItem) => internalItem.date === undefined),
            invalidItems,
        };
    });

    constructor() {
        effect(() => {
            this.startDate();
            this.endDate();
            this.datesChanged.emit();
        });
        effect(() => {
            this.timelineStatusChange.emit(this.timelineStatus());
        });
    }

    protected setInputValidity(key: string, valid: boolean) {
        this.invalidInputKeys.update((keys) => this.withMembership(keys, key, !valid));
    }

    protected markTouched(key: string) {
        this.touchedKeys.update((keys) => this.withMembership(keys, key, true));
    }

    /** Returns the set with the key added or removed, or the same set if nothing changes. */
    private withMembership(keys: ReadonlySet<string>, key: string, member: boolean): ReadonlySet<string> {
        if (keys.has(key) === member) {
            return keys;
        }
        const next = new Set(keys);
        if (member) {
            next.add(key);
        } else {
            next.delete(key);
        }
        return next;
    }

    /** Mirrors the tooltip precedence of {@link internalTimelineItems}. */
    private determineInvalidReasonKey(internalItem: InternalExamTimelineItem): string | undefined {
        if (internalItem.isInvalidInput) {
            return 'artemisApp.exercise.form.timeline.invalidInput';
        }
        if (internalItem.hasInvalidDateOrder) {
            return 'artemisApp.exercise.form.timeline.strictOrder';
        }
        if (internalItem.isInputRequiredButUndefined) {
            return 'artemisApp.exercise.form.timeline.required';
        }
        return undefined;
    }
}
