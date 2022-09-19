import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';

@Component({ selector: 'jhi-tutorial-free-period-form', template: '' })
export class TutorialGroupFreePeriodFormStubComponent {
    @Input() isEditMode = false;
    @Input() formData: TutorialGroupFreePeriodFormData;
    @Output() formSubmitted: EventEmitter<TutorialGroupFreePeriodFormData> = new EventEmitter<TutorialGroupFreePeriodFormData>();
}
