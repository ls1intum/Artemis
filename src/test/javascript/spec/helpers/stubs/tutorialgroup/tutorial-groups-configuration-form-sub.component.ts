import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroupsConfigurationFormData } from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { Course } from 'app/core/course/shared/entities/course.model';

@Component({ selector: 'jhi-tutorial-groups-configuration-form', template: '' })
export class TutorialGroupsConfigurationFormStubComponent {
    @Input() isEditMode = false;
    @Input() formData: TutorialGroupsConfigurationFormData;
    @Output() formSubmitted: EventEmitter<TutorialGroupsConfigurationFormData> = new EventEmitter<TutorialGroupsConfigurationFormData>();

    @Input()
    course: Course;
}
