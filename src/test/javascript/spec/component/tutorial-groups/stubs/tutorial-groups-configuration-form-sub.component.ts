import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroupsConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';

@Component({ selector: 'jhi-tutorial-groups-configuration-form', template: '' })
export class TutorialGroupsConfigurationFormStubComponent {
    @Input() isEditMode = false;
    @Input() formData: TutorialGroupsConfigurationFormData;
    @Output() formSubmitted: EventEmitter<TutorialGroupsConfigurationFormData> = new EventEmitter<TutorialGroupsConfigurationFormData>();
}
