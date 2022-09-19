import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { Course } from 'app/entities/course.model';

@Component({ selector: 'jhi-tutorial-group-form', template: '' })
export class TutorialGroupFormStubComponent {
    @Input() course: Course;
    @Input() isEditMode = false;
    @Input() formData: TutorialGroupFormData;
    @Output() formSubmitted: EventEmitter<TutorialGroupFormData> = new EventEmitter<TutorialGroupFormData>();
}
