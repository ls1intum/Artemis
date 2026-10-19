import { Component, Signal, WritableSignal, computed, effect, inject, input, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiDatePickerComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { Dayjs } from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { TranslateService } from '@ngx-translate/core';

export interface TimelineItem {
    kind: 'required' | 'optional';
    labelStringKey: string;
    date: WritableSignal<Dayjs | undefined>;
    warningStringKey?: Signal<string | undefined>;
    errorStringKey?: Signal<string | undefined>;
    disabled?: boolean;
}

export interface InvalidTimelineItem {
    labelStringKey: string;
    reasonKey: string;
    dateName: string;
}

export interface TimelineStatus {
    valid: boolean;
    empty: boolean;
    invalidItems: InvalidTimelineItem[];
}

type InternalTimelineItem = TimelineItem & {
    isInputRequiredButUndefined: boolean;
    hasInvalidDateOrder: boolean;
    isInvalidInput: boolean;
    isDisabled: boolean;
    /** Whether the field is painted as an error. A missing required date only counts once the field was left. */
    isInvalid: boolean;
    hasExternalError: boolean;
    hasWarning: boolean;
    tooltip: string | undefined;
};

@Component({
    selector: 'jhi-timeline',
    imports: [FaIconComponent, TumAetUiDatePickerComponent, TumAetUiTooltipDirective, TranslateDirective],
    templateUrl: './timeline.component.html',
    styleUrl: './timeline.component.scss',
})
export class TimelineComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    protected readonly faCircleInfo = faCircleInfo;
    /** Label keys of items whose typed text is non-empty but not a valid date, as reported by their date field. */
    private readonly invalidInputKeys = signal<ReadonlySet<string>>(new Set());
    /** Label keys of items that the user has left once, so that a missing required date is only flagged after that. */
    private readonly touchedKeys = signal<ReadonlySet<string>>(new Set());

    timelineItems = input.required<TimelineItem[]>();
    internalTimelineItems = computed<InternalTimelineItem[]>(() => this.computeInternalTimelineItems());
    timelineStatus = computed<TimelineStatus>(() => this.computeExerciseTimelineStatus());
    timelineStatusChange = output<TimelineStatus>();

    constructor() {
        effect(() => {
            const timelineStatus = this.timelineStatus();
            this.timelineStatusChange.emit(timelineStatus);
        });
    }

    protected setInputValidity(item: TimelineItem, valid: boolean) {
        this.invalidInputKeys.update((keys) => this.withMembership(keys, item.labelStringKey, !valid));
    }

    protected markTouched(item: TimelineItem) {
        this.touchedKeys.update((keys) => this.withMembership(keys, item.labelStringKey, true));
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

    private computeInternalTimelineItems(): InternalTimelineItem[] {
        const invalidInputKeys = this.invalidInputKeys();
        const touchedKeys = this.touchedKeys();
        return this.timelineItems().map((item, index, items) => {
            this.currentLocale();
            const date = item.date();
            const hasInvalidDateOrder =
                date !== undefined &&
                items.slice(0, index).some((previousItem) => {
                    const previousDate = previousItem.date();
                    return previousDate !== undefined && !date.isAfter(previousDate);
                });
            const isInputRequiredButUndefined = item.kind === 'required' && date === undefined;
            const isInvalidInput = invalidInputKeys.has(item.labelStringKey);
            const isDisabled = item.disabled ?? false;
            const hasInternalError = isInvalidInput || hasInvalidDateOrder || isInputRequiredButUndefined;
            const errorStringKey = item.errorStringKey?.();
            const hasExternalError = errorStringKey !== undefined;
            const warningStringKey = item.warningStringKey?.();
            const hasWarning = !hasInternalError && !hasExternalError && warningStringKey !== undefined;
            const isInvalid = isInvalidInput || hasInvalidDateOrder || hasExternalError || (touchedKeys.has(item.labelStringKey) && isInputRequiredButUndefined);
            let tooltip: string | undefined;
            if (isInvalidInput) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateInvalidTooltip');
            } else if (hasInvalidDateOrder) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateStrictOrderTooltip');
            } else if (isInputRequiredButUndefined) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateRequiredTooltip');
            } else if (errorStringKey !== undefined) {
                tooltip = this.translateService.instant(errorStringKey);
            } else if (warningStringKey !== undefined) {
                tooltip = this.translateService.instant(warningStringKey);
            }

            return {
                kind: item.kind,
                labelStringKey: item.labelStringKey,
                date: item.date,
                isInputRequiredButUndefined,
                hasInvalidDateOrder,
                isInvalidInput,
                isDisabled,
                isInvalid,
                hasExternalError,
                // Carried through so the invalid-item reason can reuse the key the tooltip already shows.
                errorStringKey: item.errorStringKey,
                hasWarning,
                tooltip,
            };
        });
    }

    private computeExerciseTimelineStatus(): TimelineStatus {
        const items = this.internalTimelineItems();
        const invalidItems = items.flatMap((item) => {
            const reasonKey = this.determineInvalidReasonKey(item);
            // Explicitly undefined, not falsy: an item whose errorStringKey resolves to an empty string still counts
            // as an external error and is still painted as one, so dropping it here would report the timeline valid
            // while the field shows red.
            if (reasonKey === undefined) {
                return [];
            }
            return [{ labelStringKey: item.labelStringKey, reasonKey, dateName: this.translateService.instant(item.labelStringKey) }];
        });
        return {
            // Derived from the reasons rather than enumerated separately, so validity and the explanation of it cannot
            // drift apart. determineInvalidReasonKey covers exactly the flags develop gates validity on.
            valid: invalidItems.length === 0,
            empty: items.some((item) => item.date() === undefined),
            invalidItems,
        };
    }

    /** Mirrors the tooltip precedence in {@link computeInternalTimelineItems}. */
    private determineInvalidReasonKey(item: InternalTimelineItem): string | undefined {
        if (item.isInvalidInput) {
            return 'artemisApp.exercise.form.timeline.invalidInput';
        }
        if (item.hasInvalidDateOrder) {
            return 'artemisApp.exercise.form.timeline.strictOrder';
        }
        if (item.isInputRequiredButUndefined) {
            return 'artemisApp.exercise.form.timeline.required';
        }
        // The item supplies its own key here, which is already what the tooltip shows for an external error.
        return item.errorStringKey?.();
    }
}
