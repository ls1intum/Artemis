import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';

@Component({ selector: 'jhi-tutorial-group-form', template: '' })
export class TutorialGroupFormStubComponent {
    @Input() courseId: number;
    @Input() isEditMode = false;
    @Input() formData: TutorialGroupFormData;
    @Output() formSubmitted: EventEmitter<TutorialGroupFormData> = new EventEmitter<TutorialGroupFormData>();
}
