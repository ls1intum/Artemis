import { Component, computed, inject, input, signal } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { TutorialRegistrationsRegisterSearchBarComponent } from 'app/tutorialgroup/manage/tutorial-registrations-register-search-bar/tutorial-registrations-register-search-bar.component';
import {
    TutorialRegistrationsStudentsTableComponent,
    TutorialRegistrationsStudentsTableRemoveActionColumnInfo,
} from 'app/tutorialgroup/manage/tutorial-registrations-students-table/tutorial-registrations-students-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonDirective } from 'primeng/button';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/manage/service/tutorial-group-registered-students.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

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
    private tutorialGroupApiService = inject(TutorialGroupApiService);
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
    selectedStudents = signal<TutorialGroupStudent[]>([]);
    registerAllButtonDisabled = computed(() => this.selectedStudents().length === 0 || this.isLoading());

    open() {
        this.isOpen.set(true);
    }

    close() {
        this.selectedStudents.set([]);
        this.isOpen.set(false);
    }

    selectStudent(student: TutorialGroupStudent) {
        if (this.selectedStudents().every((otherStudent) => otherStudent.id !== student.id)) {
            this.selectedStudents.update((students) => [...students, student]);
        }
    }

    registerAll() {
        this.isLoading.set(true);
        this.tutorialGroupApiService
            .batchRegisterStudents(
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
