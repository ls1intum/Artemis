import { Component, input, output } from '@angular/core';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

@Component({
    selector: 'jhi-tutorial-registrations-register-search-bar',
    template: '',
})
export class TutorialRegistrationsRegisterSearchBarStubComponent {
    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    onStudentSelected = output<TutorialGroupStudent>();
}
