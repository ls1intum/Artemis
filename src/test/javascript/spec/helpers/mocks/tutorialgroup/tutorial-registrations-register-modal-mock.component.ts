import { Component, input, signal } from '@angular/core';

@Component({
    selector: 'jhi-tutorial-registrations-register-modal',
    template: '',
})
export class TutorialRegistrationsRegisterModalMockComponent {
    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    isOpen = signal(false);

    open() {
        this.isOpen.set(true);
    }
}
