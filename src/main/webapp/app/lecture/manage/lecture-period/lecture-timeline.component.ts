import { ChangeDetectionStrategy, Component, computed, effect, model, output, signal } from '@angular/core';
import { TumAetUiDatePickerComponent, TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Dayjs } from 'dayjs/esm';

/** Start and end of a lecture. Both are optional; when both are set, the end has to come after the start. */
@Component({
    selector: 'jhi-lecture-timeline',
    templateUrl: './lecture-timeline.component.html',
    styleUrl: './lecture-timeline.component.scss',
    imports: [TranslateDirective, ArtemisTranslatePipe, TumAetUiDatePickerComponent, TumAetUiMessageComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LectureTimelineComponent {
    readonly startDate = model<Dayjs | undefined>();
    readonly endDate = model<Dayjs | undefined>();
    /** Whether each field holds a date or nothing, and the end comes after the start. */
    readonly periodValidChange = output<boolean>();
    readonly datesChanged = output<void>();

    /** The picker keeps its last valid value while the typed text is not a date yet, so that text is tracked separately. */
    protected readonly isStartTextValid = signal(true);
    protected readonly isEndTextValid = signal(true);

    readonly isEndBeforeStart = computed(() => {
        const startDate = this.startDate();
        const endDate = this.endDate();
        return !!startDate?.isValid() && !!endDate?.isValid() && !endDate.isAfter(startDate);
    });

    readonly isValid = computed(() => this.isStartTextValid() && this.isEndTextValid() && !this.isEndBeforeStart());

    constructor() {
        effect(() => {
            this.startDate();
            this.endDate();
            this.datesChanged.emit();
        });

        effect(() => this.periodValidChange.emit(this.isValid()));
    }
}
