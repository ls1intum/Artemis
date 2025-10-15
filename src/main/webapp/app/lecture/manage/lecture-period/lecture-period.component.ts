import { Component, Input, computed, input, signal, viewChildren } from '@angular/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-lecture-update-period',
    templateUrl: './lecture-period.component.html',
    imports: [TranslateDirective, FormDateTimePickerComponent, FormsModule, ArtemisTranslatePipe, FaIconComponent],
    styleUrl: './lecture-period.component.scss',
})
export class LectureUpdatePeriodComponent {
    private static readonly MAX_RECOMMENDED_LECTURE_LENGTH_IN_HOURS = 8;
    protected readonly faTriangleExclamation = faTriangleExclamation;

    @Input() validateDatesFunction: () => void;
    lecture = input.required<Lecture>();
    periodSectionDatepickers = viewChildren(FormDateTimePickerComponent);
    isLectureLongerThanRecommended = signal(false);
    isPeriodSectionValid = computed(() => this.computeIsPeriodSectionValid());

    onDateChange() {
        this.isLectureLongerThanRecommended.set(this.computeIsLectureLongerThanRecommended());
        this.validateDatesFunction();
    }

    private computeIsLectureLongerThanRecommended(): boolean {
        const lecture = this.lecture();
        const start = lecture.startDate;
        const end = lecture.endDate;
        if (!start || !end) {
            return false;
        }
        const differenceInHours = dayjs(end).diff(dayjs(start), 'hour');
        return differenceInHours > LectureUpdatePeriodComponent.MAX_RECOMMENDED_LECTURE_LENGTH_IN_HOURS;
    }

    private computeIsPeriodSectionValid(): boolean {
        for (const periodSectionDatepicker of this.periodSectionDatepickers()) {
            if (!periodSectionDatepicker.isValid()) {
                return false;
            }
        }
        return true;
    }
}
