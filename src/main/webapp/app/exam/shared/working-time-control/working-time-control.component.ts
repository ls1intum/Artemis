import { Component, effect, inject, input } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';

import { Exam } from 'app/exam/shared/entities/exam.model';
import { round } from 'app/shared/util/utils';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { getRelativeWorkingTimeExtension } from 'app/exam/overview/exam.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-working-time-control',
    templateUrl: './working-time-control.component.html',
    styleUrls: ['./working-time-control.component.scss'],
    providers: [
        ArtemisDurationFromSecondsPipe,
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: WorkingTimeControlComponent,
        },
    ],
    imports: [TranslateDirective, FormsModule],
})
export class WorkingTimeControlComponent implements ControlValueAccessor {
    private artemisDurationFromSecondsPipe = inject(ArtemisDurationFromSecondsPipe);

    // Control disabled state
    disabled = input(false);
    allowNegative = input(false);

    // Whether the percentage-based working time extension control should be shown
    relative = input(false);

    // Labels for the working time duration inputs
    durationLabelText = input<string>();
    relativeLabelText = input<string>();

    exam = input<Exam | undefined>();

    constructor() {
        // Set up an effect to respond whenever the input signal changes.
        effect(() => {
            this.initWorkingTimeFromCurrentExam();
        });
    }

    workingTime = {
        hours: 0,
        minutes: 0,
        seconds: 0,
        percent: 0,
    };

    private touched = false;
    private onTouched = () => {};
    private onChange: (_: number) => void = () => {};

    /**
     * Updates the working time duration inputs whenever
     * the value of the form control changes.
     * @param seconds
     */
    writeValue(seconds: number | undefined | null) {
        if (typeof seconds === 'number') {
            this.workingTimeSeconds = seconds;
        }
    }

    registerOnChange(onChange: any) {
        this.onChange = onChange;
    }

    registerOnTouched(onTouched: any) {
        this.onTouched = onTouched;
    }

    private markAsTouched() {
        if (!this.touched) {
            this.onTouched();
            this.touched = true;
        }
    }

    set workingTimeSeconds(seconds: number) {
        this.setWorkingTimeDuration(seconds);
        this.updateWorkingTimePercentFromDuration();
    }

    /**
     * The seconds of the current working time duration.
     */
    get workingTimeSeconds(): number {
        return this.artemisDurationFromSecondsPipe.durationToSeconds({
            days: 0,
            hours: this.workingTime.hours,
            minutes: this.workingTime.minutes,
            seconds: this.workingTime.seconds,
        });
    }

    /**
     * Updates the controls based on the working time of the student exam.
     */
    private initWorkingTimeFromCurrentExam() {
        if (this.exam()) {
            // this.setWorkingTimeDuration(examWorkingTime(this.exam)!);
            this.updateWorkingTimePercentFromDuration();
            this.emitWorkingTimeChange();
        }
    }

    /**
     * Updates the working time duration values of the control whenever the percent value was changed.
     * After the update, the onChange callback is called with the new working time in seconds.
     */
    onPercentChanged() {
        this.markAsTouched();
        this.updateWorkingTimeDurationFromPercent();
        this.emitWorkingTimeChange();
    }

    /**
     * Updates the working time percent value of the control whenever the duration values were changed.
     * After the update, the onChange callback is called with the new working time in seconds.
     */
    onDurationChanged() {
        this.markAsTouched();
        this.updateWorkingTimePercentFromDuration();
        this.emitWorkingTimeChange();
    }

    /**
     * Updates the working time percent value of the control based on the current working time duration.
     * @private
     */
    private updateWorkingTimePercentFromDuration() {
        const exam = this.exam();
        if (exam) {
            this.workingTime.percent = getRelativeWorkingTimeExtension(exam, this.workingTimeSeconds);
        }
    }

    /**
     * Updates the working time duration values of the control based on the current working time percent.
     * @private
     */
    private updateWorkingTimeDurationFromPercent() {
        const exam = this.exam();
        if (exam) {
            const regularWorkingTime = exam.workingTime!;
            const absoluteWorkingTimeSeconds = round(regularWorkingTime * (1.0 + this.workingTime.percent / 100), 0);
            this.setWorkingTimeDuration(absoluteWorkingTimeSeconds);
        }
    }

    /**
     * Sets the working time duration values of the respective controls by
     * converting the given seconds into hours, minutes and seconds.
     * @param seconds the total number of seconds of working time.
     * @private
     */
    private setWorkingTimeDuration(seconds: number) {
        const workingTime = this.artemisDurationFromSecondsPipe.secondsToDuration(seconds);
        this.workingTime.hours = workingTime.days * 24 + workingTime.hours;
        this.workingTime.minutes = workingTime.minutes;
        this.workingTime.seconds = workingTime.seconds;
    }

    /**
     * Calls the onChange callback with the current working time in seconds.
     * @private
     */
    private emitWorkingTimeChange() {
        this.onChange(this.workingTimeSeconds);
    }
}
