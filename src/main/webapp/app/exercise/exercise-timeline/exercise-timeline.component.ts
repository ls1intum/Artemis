import { Component, WritableSignal, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DatePickerModule } from 'primeng/datepicker';
// Still needed for the `pTooltip` on the invalid-date info icon; the lock overlay uses the kit tooltip.
import { TooltipModule } from 'primeng/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faLock } from '@fortawesome/free-solid-svg-icons';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';
import dayjs, { Dayjs } from 'dayjs/esm';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { TimeZoneWarningComponent } from 'app/shared-ui/date-time-picker/time-zone-warning.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

export interface TimelineItem {
    kind: 'required' | 'optional';
    labelStringKey: string;
    date: WritableSignal<Dayjs | undefined>;
    otherRequiredItem?: TimelineItem;
    mustBeStrictlyAfterPrevious?: boolean;
    helpKey?: string;
    /**
     * Restricts the ordering check to these items. The default (no item may precede any earlier one) is too strict for
     * e.g. a group's example solution publication date, which only needs `>= releaseDate`.
     */
    orderCheckAgainst?: TimelineItem[];
}

export interface ExerciseTimelineStatus {
    valid: boolean;
    empty: boolean;
}

type InternalTimelineItem = TimelineItem & {
    internalDate: Date | undefined;
    violationKey: string | undefined;
};

@Component({
    selector: 'jhi-exercise-timeline',
    imports: [
        DatePickerModule,
        FormsModule,
        TooltipModule,
        TumUiTooltipDirective,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TimeZoneWarningComponent,
        HelpIconComponent,
    ],
    templateUrl: './exercise-timeline.component.html',
    styleUrl: './exercise-timeline.component.scss',
})
export class ExerciseTimelineComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private readonly fullDateTimePattern = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;
    private readonly dateTimeFormat = 'DD.MM.YYYY HH:mm';
    protected readonly Date = Date;
    protected readonly faLock = faLock;
    /** Label keys of items whose typed text is non-empty but not a valid date. Drives the invalid state. */
    private invalidInputKeys = signal<Set<string>>(new Set());

    readonly timelineItems = input.required<TimelineItem[]>();
    readonly readonly = input<boolean>(false);
    readonly showInvalidBeforeTouched = input<boolean>(false);
    /** Dates governed by the variant group: datepickers are disabled and clicks emit {@link lockedClick}. */
    readonly lockedToGroup = input<boolean>(false);
    /** Emitted when the user clicks the timeline while {@link lockedToGroup} is set. */
    readonly lockedClick = output<void>();
    /** Effective read-only state: either explicitly {@link readonly} or locked to the variant group. */
    readonly isReadonly = computed<boolean>(() => this.readonly() || this.lockedToGroup());
    readonly internalTimelineItems = computed<InternalTimelineItem[]>(() => this.computeInternalTimelineItems());
    readonly timelineStatus = computed<ExerciseTimelineStatus>(() => this.computeExerciseTimelineStatus());
    readonly timelineStatusChange = output<ExerciseTimelineStatus>();

    constructor() {
        effect(() => {
            const timelineStatus = this.timelineStatus();
            this.timelineStatusChange.emit(timelineStatus);
        });
    }

    updateDate(item: TimelineItem, newInternalDate: Date | string | null) {
        const currentDate = item.date();
        const newDate = newInternalDate instanceof Date ? dayjs(newInternalDate) : undefined;
        const oldAndNewDateUndefined = currentDate === undefined && newDate === undefined;
        const oldAndNewDatesAreTheSame = currentDate !== undefined && newDate !== undefined && currentDate.isSame(newDate);
        if (oldAndNewDateUndefined || oldAndNewDatesAreTheSame) return;
        item.date.set(newDate);
    }

    handleManualInput(item: TimelineItem, event: Event) {
        const value = (event.target as HTMLInputElement).value;
        const parsedDate = this.parseManualInput(value);
        if (parsedDate !== undefined) {
            this.setDateIfChanged(item, parsedDate);
            this.setInvalidInput(item, false);
        } else if (value === '') {
            this.setDateIfChanged(item, undefined);
            this.setInvalidInput(item, false);
        }
        // A not-yet-parseable value is left alone while typing and only flagged on blur (see handleBlur).
    }

    handleBlur(item: TimelineItem, event: Event) {
        const input = (event.target as HTMLInputElement).value;
        const inputWasCleared = input === '';
        const currentInputIsInvalidDate = this.parseManualInput(input) === undefined;
        // Keep the entered text (keepInvalid) and flag it instead of silently reverting it (PR #13009 review).
        this.setInvalidInput(item, currentInputIsInvalidDate && !inputWasCleared);
    }

    private setDateIfChanged(item: TimelineItem, newDate?: Dayjs) {
        const currentDate = item.date();
        if (currentDate?.isSame(newDate)) return;
        item.date.set(newDate);
    }

    /** Adds or removes the item's label key from the invalid-input set (no-op if already in that state). */
    private setInvalidInput(item: TimelineItem, invalid: boolean) {
        this.invalidInputKeys.update((keys) => {
            if (invalid === keys.has(item.labelStringKey)) {
                return keys;
            }
            const next = new Set(keys);
            if (invalid) {
                next.add(item.labelStringKey);
            } else {
                next.delete(item.labelStringKey);
            }
            return next;
        });
    }

    private parseManualInput(value: string): Dayjs | undefined {
        if (!this.fullDateTimePattern.test(value)) return undefined;
        const parsedDate = dayjs(value, this.dateTimeFormat, true);
        return parsedDate.isValid() ? parsedDate : undefined;
    }

    private computeInternalTimelineItems(): InternalTimelineItem[] {
        const invalidInputKeys = this.invalidInputKeys();
        return this.timelineItems().map((item, index, items) => {
            this.currentLocale();
            const date = item.date();
            const violatesPreviousDate =
                date !== undefined &&
                (item.orderCheckAgainst ?? items.slice(0, index)).some((previousItem) => {
                    const previousDate = previousItem.date();
                    return previousDate !== undefined && (date.isBefore(previousDate) || (item.mustBeStrictlyAfterPrevious && date.isSame(previousDate)));
                });
            const isInputRequiredButUndefined = item.kind === 'required' && date === undefined;
            const otherRequiredItem = item.otherRequiredItem;
            const isOtherRequiredItemDateUndefined = date !== undefined && otherRequiredItem !== undefined && otherRequiredItem.date() === undefined;
            const isInvalidInput = invalidInputKeys.has(item.labelStringKey);
            let violationKey: string | undefined = undefined;
            if (isInvalidInput) {
                violationKey = this.translateService.instant('artemisApp.exercise.timelineDateInvalidTooltip');
            } else if (violatesPreviousDate) {
                if (item.mustBeStrictlyAfterPrevious) {
                    violationKey = this.translateService.instant('artemisApp.exercise.timelineDateStrictOrderTooltip');
                } else {
                    violationKey = this.translateService.instant('artemisApp.exercise.timelineDateOrderTooltip');
                }
            } else if (isInputRequiredButUndefined) {
                violationKey = this.translateService.instant('artemisApp.exercise.timelineDateRequiredTooltip');
            } else if (isOtherRequiredItemDateUndefined && otherRequiredItem) {
                const otherInputName = this.translateService.instant(otherRequiredItem.labelStringKey);
                violationKey = this.translateService.instant('artemisApp.exercise.timelineOtherRequiredDateTooltip', { otherInputName });
            }

            return {
                ...item,
                internalDate: date?.toDate(),
                violationKey,
            };
        });
    }

    private computeExerciseTimelineStatus(): ExerciseTimelineStatus {
        const items = this.internalTimelineItems();
        return {
            valid: items.every((item) => !item.violationKey),
            empty: items.every((item) => item.date() === undefined),
        };
    }
}
