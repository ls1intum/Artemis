import { Component, input, output } from '@angular/core';
import { TutorialGroupsConfigurationFormData } from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { Course } from 'app/course/shared/entities/course.model';
import { TutorialGroupsConfigurationSettings } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';

@Component({ selector: 'jhi-tutorial-groups-configuration-form', template: '' })
export class TutorialGroupsConfigurationFormStubComponent {
    isEditMode = input(false);
    formData = input<TutorialGroupsConfigurationFormData>();
    formSubmitted = output<TutorialGroupsConfigurationSettings>();

    course = input<Course>();
}
