import { Component, Input, Signal, computed, input, viewChildren } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from '../../shared/language/translate.directive';
import { FormDateTimePickerComponent as FormDateTimePickerComponent_1 } from '../../shared/date-time-picker/date-time-picker.component';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from '../../shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-lecture-update-period',
    templateUrl: './lecture-period.component.html',
    imports: [TranslateDirective, FormDateTimePickerComponent_1, FormsModule, ArtemisTranslatePipe],
})
export class LectureUpdatePeriodComponent {
    lecture = input.required<Lecture>();
    @Input() validateDatesFunction: () => void;

    periodSectionDatepickers = viewChildren(FormDateTimePickerComponent);

    isPeriodSectionValid: Signal<boolean> = computed(() => {
        for (const periodSectionDatepicker of this.periodSectionDatepickers()) {
            if (!periodSectionDatepicker.isValid()) {
                return false;
            }
        }
        return true;
    });
}
