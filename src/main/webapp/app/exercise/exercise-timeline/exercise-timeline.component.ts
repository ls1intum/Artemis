import { Component, Signal, WritableSignal, computed, effect, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';
import dayjs, { Dayjs } from 'dayjs/esm';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { TranslateService } from '@ngx-translate/core';

type BaseTimelineItem = {
    labelStringKey: string;
    otherRequiredItem?: TimelineItem;
    mustBeStrictlyAfterPrevious?: boolean;
};

export type TimelineItem =
    | (BaseTimelineItem & {
          kind: 'required' | 'optional';
          date: WritableSignal<Dayjs | undefined>;
      })
    | (BaseTimelineItem & {
          kind: 'derived';
          date: Signal<Dayjs | undefined>;
      });

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
    imports: [DatePickerModule, FormsModule, TooltipModule, TranslateDirective],
    templateUrl: './exercise-timeline.component.html',
    styleUrl: './exercise-timeline.component.scss',
})
export class ExerciseTimelineComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private readonly fullDateTimePattern = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;
    private readonly dateTimeFormat = 'DD.MM.YYYY HH:mm';
    protected readonly Date = Date;

    readonly timelineItems = input.required<TimelineItem[]>();
    readonly readonly = input<boolean>(false);
    readonly showInvalidBeforeTouched = input<boolean>(false);

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
        if (item.kind === 'derived') {
            return;
        }
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
        }
        if (value === '') {
            this.setDateIfChanged(item, undefined);
        }
    }

    handleBlur(item: TimelineItem, event: Event) {
        const inputElement = event.target as HTMLInputElement;
        const input = inputElement.value;
        const inputWasCleared = input === '';
        const currentInputIsInvalidDate = this.parseManualInput(input) === undefined;
        if (currentInputIsInvalidDate && !inputWasCleared) {
            const previousDate = item.date();
            inputElement.value = previousDate ? previousDate.format(this.dateTimeFormat) : '';
        }
    }

    private setDateIfChanged(item: TimelineItem, newDate?: Dayjs) {
        if (item.kind === 'derived') {
            return;
        }
        const currentDate = item.date();
        if (currentDate?.isSame(newDate)) return;
        item.date.set(newDate);
    }

    private parseManualInput(value: string): Dayjs | undefined {
        if (!this.fullDateTimePattern.test(value)) return undefined;
        const parsedDate = dayjs(value, this.dateTimeFormat, true);
        return parsedDate.isValid() ? parsedDate : undefined;
    }

    private computeInternalTimelineItems(): InternalTimelineItem[] {
        this.currentLocale();
        return this.timelineItems().map((item, index, items) => {
            const date = item.date();
            const violatesPreviousDate =
                date !== undefined &&
                items.slice(0, index).some((previousItem) => {
                    const previousDate = previousItem.date();
                    return previousDate !== undefined && (date.isBefore(previousDate) || (item.mustBeStrictlyAfterPrevious && date.isSame(previousDate)));
                });
            const isInputRequiredButUndefined = item.kind === 'required' && date === undefined;
            const otherRequiredItem = item.otherRequiredItem;
            const isOtherRequiredItemDateUndefined = date !== undefined && otherRequiredItem !== undefined && otherRequiredItem.date() === undefined;
            let violationKey: string | undefined = undefined;
            if (violatesPreviousDate) {
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
