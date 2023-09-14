import { Component, Input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { Exam } from 'app/entities/exam.model';
import { getRelativeWorkingTimeExtension, normalWorkingTime } from 'app/exam/participate/exam.utils';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { round } from 'app/shared/util/utils';

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
    @Input() showRelative = false;

    @Input() durationLabelText?: string;
    @Input() relativeLabelText?: string;

    // The exam for which the working time should be updated
    @Input()
    set exam(exam: Exam | undefined) {
        this.currentExam = exam;
        this.initWorkingTimeFromCurrentExam();
    }

    get exam(): Exam | undefined {
        return this.currentExam;
    }

    private currentExam?: Exam;
    private touched = false;
    private onTouched = () => {};
    private onChange: (_: number) => void = () => {};

    workingTime = {
        hours: 0,
        minutes: 0,
        seconds: 0,
        percent: 0,
    };

    constructor(private artemisDurationFromSecondsPipe: ArtemisDurationFromSecondsPipe) {}

    /**
     * Updates the working time duration inputs whenever
     * the value of the form control changes.
     * @param seconds
     */
    writeValue(seconds: number) {
        if (seconds) {
            this.setWorkingTimeDuration(seconds);
            this.updateWorkingTimePercentFromDuration();
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

    /**
     * Updates the controls based on the working time of the student exam.
     */
    private initWorkingTimeFromCurrentExam() {
        if (this.exam) {
            this.setWorkingTimeDuration(normalWorkingTime(this.exam)!);
            this.updateWorkingTimePercentFromDuration();
            this.onChange(this.getWorkingTimeSeconds());
        }
    }

    /**
     * Updates the working time duration values of the control whenever the percent value was changed.
     * After the update, the onChange callback is called with the new working time in seconds.
     */
    onPercentChanged() {
        this.markAsTouched();
        this.updateWorkingTimeDurationFromPercent();
        this.onChange(this.getWorkingTimeSeconds());
    }

    /**
     * Updates the working time percent value of the control whenever the duration values were changed.
     * After the update, the onChange callback is called with the new working time in seconds.
     */
    onDurationChanged() {
        this.markAsTouched();
        this.updateWorkingTimePercentFromDuration();
        this.onChange(this.getWorkingTimeSeconds());
    }

    /**
     * Updates the working time percent value of the control based on the current working time duration.
     */
    private updateWorkingTimePercentFromDuration() {
        if (this.exam) {
            this.workingTime.percent = getRelativeWorkingTimeExtension(this.exam, this.getWorkingTimeSeconds());
        }
    }

    /**
     * Updates the working time duration values of the control based on the current working time percent.
     */
    private updateWorkingTimeDurationFromPercent() {
        if (this.exam) {
            const regularWorkingTime = this.exam.workingTime!;
            const absoluteWorkingTimeSeconds = round(regularWorkingTime * (1.0 + this.workingTime.percent / 100), 0);
            console.log(regularWorkingTime, absoluteWorkingTimeSeconds);
            this.setWorkingTimeDuration(absoluteWorkingTimeSeconds);
        }
    }

    /**
     * Sets the working time duration values of the respective controls by
     * converting the given seconds into hours, minutes and seconds.
     * @param seconds the total number of seconds of working time.
     */
    private setWorkingTimeDuration(seconds: number) {
        const workingTime = this.artemisDurationFromSecondsPipe.secondsToDuration(seconds);
        this.workingTime.hours = workingTime.days * 24 + workingTime.hours;
        this.workingTime.minutes = workingTime.minutes;
        this.workingTime.seconds = workingTime.seconds;
    }

    /**
     * Returns the seconds of the current working time duration.
     */
    private getWorkingTimeSeconds(): number {
        return this.artemisDurationFromSecondsPipe.durationToSeconds({
            days: 0,
            hours: this.workingTime.hours,
            minutes: this.workingTime.minutes,
            seconds: this.workingTime.seconds,
        });
    }
}
