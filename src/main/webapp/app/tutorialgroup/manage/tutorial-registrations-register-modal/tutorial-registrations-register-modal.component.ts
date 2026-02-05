import { Component, computed, inject, input, signal } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialRegistrationsRegisterSearchBarComponent } from 'app/tutorialgroup/manage/tutorial-registrations-register-search-bar/tutorial-registrations-register-search-bar.component';

@Component({
    selector: 'jhi-tutorial-registrations-register-modal',
    imports: [Dialog, TutorialRegistrationsRegisterSearchBarComponent],
    templateUrl: './tutorial-registrations-register-modal.component.html',
    styleUrl: './tutorial-registrations-register-modal.component.scss',
})
export class TutorialRegistrationsRegisterModalComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    isOpen = signal(false);
    header = computed<string>(() => this.computeHeader());
    selectedStudents = signal<TutorialGroupRegisteredStudentDTO[]>([]);

    open() {
        this.isOpen.set(true);
    }

    selectStudent(student: TutorialGroupRegisteredStudentDTO) {
        if (this.selectedStudents().every((otherStudent) => otherStudent.id !== student.id)) {
            this.selectedStudents.update((students) => [...students, student]);
        }
    }

    private computeHeader(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.registerModal.header');
    }
}
