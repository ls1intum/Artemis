import { Component, Input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { Exam } from 'app/entities/exam/exam.model';
import { round } from 'app/shared/util/utils';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { getRelativeWorkingTimeExtension } from 'app/exam/participate/exam.utils';

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
})
export class WorkingTimeControlComponent implements ControlValueAccessor {
    // Control disabled state
    @Input() disabled = false;
    @Input() allowNegative = false;

    // Whether the percentage-based working time extension control should be shown
    @Input() relative = false;

    // Labels for the working time duration inputs
    @Input() durationLabelText?: string;
    @Input() relativeLabelText?: string;

    @Input()
    set exam(exam: Exam | undefined) {
        this.currentExam = exam;
        this.initWorkingTimeFromCurrentExam();
    }

    get exam(): Exam | undefined {
        return this.currentExam;
    }

    // The exam for which the working time should be updated
    // Used to calculate the relative working time extension
    private currentExam?: Exam;

    workingTime = {
        hours: 0,
        minutes: 0,
        seconds: 0,
        percent: 0,
    };

    private touched = false;
    private onTouched = () => {};
    private onChange: (_: number) => void = () => {};

    constructor(private artemisDurationFromSecondsPipe: ArtemisDurationFromSecondsPipe) {}

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

    setDisabledState(disabled: boolean) {
        this.disabled = disabled;
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
        if (this.exam) {
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
        if (this.exam) {
            this.workingTime.percent = getRelativeWorkingTimeExtension(this.exam, this.workingTimeSeconds);
        }
    }

    /**
     * Updates the working time duration values of the control based on the current working time percent.
     * @private
     */
    private updateWorkingTimeDurationFromPercent() {
        if (this.exam) {
            const regularWorkingTime = this.exam.workingTime!;
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
