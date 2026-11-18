import { Component, input, signal } from '@angular/core';

@Component({
    selector: 'jhi-tutorial-registrations-import-modal',
    template: '',
})
export class TutorialRegistrationsImportModalMockComponent {
    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    isOpen = signal(false);

    open() {
        this.isOpen.set(true);
    }
}
