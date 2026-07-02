import { Component, input, output } from '@angular/core';
import { TutorialGroupStudent } from 'app/openapi/models/tutorial-group-student';

@Component({
    selector: 'jhi-tutorial-registrations-register-search-bar',
    template: '',
})
export class TutorialRegistrationsRegisterSearchBarStubComponent {
    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    onStudentSelected = output<TutorialGroupStudent>();
}
