import { Component, Input, computed, input, viewChildren } from '@angular/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-lecture-update-period',
    templateUrl: './lecture-period.component.html',
    imports: [TranslateDirective, FormDateTimePickerComponent, FormsModule, ArtemisTranslatePipe],
    styleUrl: './lecture-period.component.scss',
})
export class LectureUpdatePeriodComponent {
    @Input() validateDatesFunction: () => void;
    lecture = input.required<Lecture>();
    periodSectionDatepickers = viewChildren(FormDateTimePickerComponent);
    isPeriodSectionValid = computed(() => this.computeIsPeriodSectionValid());

    onDateChange() {
        this.validateDatesFunction();
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
