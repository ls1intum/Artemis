import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariableSignal';
import { ActivatedRoute } from '@angular/router';
import { DeregisterStudentEvent, TutorialRegistrationsComponent } from 'app/tutorialgroup/manage/tutorial-registrations/tutorial-registrations.component';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';

@Component({
    selector: 'jhi-tutorial-registrations-container',
    imports: [TutorialRegistrationsComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-registrations-container.component.html',
})
export class TutorialRegistrationsContainerComponent {
    private destroyRef = inject(DestroyRef);
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupsService = inject(TutorialGroupsService);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    isLoading = signal(false);
    registeredStudents = signal<TutorialGroupRegisteredStudentDTO[]>([]);

    constructor() {
        effect(() => this.fetchRegisteredStudents());
    }

    deregisterStudent(event: DeregisterStudentEvent) {
        this.isLoading.set(true);
        const courseId = event.courseId;
        const tutorialGroupId = event.tutorialGroupId;
        const studentLogin = event.studentLogin;
        this.tutorialGroupsService
            .deregisterStudent(courseId, tutorialGroupId, studentLogin)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                this.registeredStudents.update((registeredStudents) => {
                    return registeredStudents.filter((student) => student.login !== studentLogin);
                });
                this.isLoading.set(false);
                // TODO: catch errors
            });
    }

    fetchRegisteredStudents() {
        const courseId = this.courseId();
        const tutorialGroupId = this.tutorialGroupId();
        if (!courseId || !tutorialGroupId) {
            return;
        }

        this.isLoading.set(true);
        this.tutorialGroupsService
            .getRegisteredStudentDTOs(courseId, tutorialGroupId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((registeredStudents) => {
                this.registeredStudents.set(registeredStudents);
                this.isLoading.set(false);
                // TODO: catch errors
            });
    }
}
