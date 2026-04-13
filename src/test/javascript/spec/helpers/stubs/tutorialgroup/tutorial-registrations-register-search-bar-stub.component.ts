import { Component, input, output } from '@angular/core';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

@Component({
    selector: 'jhi-tutorial-registrations-register-search-bar',
    template: '',
})
export class TutorialRegistrationsRegisterSearchBarStubComponent {
    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    onStudentSelected = output<TutorialGroupRegisteredStudentDTO>();
}
