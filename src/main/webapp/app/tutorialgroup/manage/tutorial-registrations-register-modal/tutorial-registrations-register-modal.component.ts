import { Component, computed, inject, input, signal } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialRegistrationsRegisterSearchBarComponent } from 'app/tutorialgroup/manage/tutorial-registrations-register-search-bar/tutorial-registrations-register-search-bar.component';
import {
    TutorialRegistrationsStudentsTableComponent,
    TutorialRegistrationsStudentsTableRemoveActionColumnInfo,
} from 'app/tutorialgroup/manage/tutorial-registrations-students-table/tutorial-registrations-students-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonDirective } from 'primeng/button';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/shared/service/tutorial-group-registered-students.service';

@Component({
    selector: 'jhi-tutorial-registrations-register-modal',
    imports: [
        Dialog,
        TutorialRegistrationsRegisterSearchBarComponent,
        TutorialRegistrationsStudentsTableComponent,
        TranslateDirective,
        ButtonDirective,
        LoadingIndicatorOverlayComponent,
    ],
    templateUrl: './tutorial-registrations-register-modal.component.html',
    styleUrl: './tutorial-registrations-register-modal.component.scss',
})
export class TutorialRegistrationsRegisterModalComponent {
    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);
    private tutorialGroupsService = inject(TutorialGroupsService);
    private tutorialGroupRegisteredStudentsService = inject(TutorialGroupRegisteredStudentsService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    readonly studentsTableRemoveActionColumnInfo: TutorialRegistrationsStudentsTableRemoveActionColumnInfo = {
        headerStringKey: 'entity.action.remove',
        onRemove: (_, student) => {
            this.selectedStudents.update((students) => {
                return students.filter((otherStudent) => otherStudent.id !== student.id);
            });
        },
    };

    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    isOpen = signal(false);
    isLoading = signal(false);
    header = computed<string>(() => this.computeHeader());
    selectedStudents = signal<TutorialGroupRegisteredStudentDTO[]>([]);

    open() {
        this.isOpen.set(true);
    }

    close() {
        this.selectedStudents.set([]);
        this.isOpen.set(false);
    }

    selectStudent(student: TutorialGroupRegisteredStudentDTO) {
        if (this.selectedStudents().every((otherStudent) => otherStudent.id !== student.id)) {
            this.selectedStudents.update((students) => [...students, student]);
        }
    }

    registerAll() {
        this.isLoading.set(true);
        this.tutorialGroupsService
            .registerMultipleStudentsViaLogin(
                this.courseId(),
                this.tutorialGroupId(),
                this.selectedStudents().map((student) => student.login),
            )
            .subscribe({
                next: (_: HttpResponse<void>) => {
                    this.tutorialGroupRegisteredStudentsService.addStudentsToRegisteredStudentsState(this.selectedStudents());
                    this.isLoading.set(false);
                    this.selectedStudents.set([]);
                    this.isOpen.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupRegistrations.registerModal.registerErrorAlert');
                    this.isLoading.set(false);
                },
            });
    }

    private computeHeader(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.registerModal.header');
    }
}
