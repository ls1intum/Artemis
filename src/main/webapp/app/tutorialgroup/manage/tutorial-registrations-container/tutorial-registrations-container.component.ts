import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariableSignal';
import { ActivatedRoute } from '@angular/router';
import { TutorialRegistrationsComponent } from 'app/tutorialgroup/manage/tutorial-registrations/tutorial-registrations.component';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';

@Component({
    selector: 'jhi-tutorial-registrations-container',
    imports: [TutorialRegistrationsComponent],
    templateUrl: './tutorial-registrations-container.component.html',
    styleUrl: './tutorial-registrations-container.component.scss',
})
export class TutorialRegistrationsContainerComponent {
    private destroyRef = inject(DestroyRef);
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupsService = inject(TutorialGroupsService);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    registeredStudents = signal<TutorialGroupRegisteredStudentDTO[] | undefined>(undefined);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (!courseId || !tutorialGroupId) {
                return;
            }

            this.tutorialGroupsService
                .getRegisteredStudentDTOs(courseId, tutorialGroupId)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe((registeredStudents) => {
                    this.registeredStudents.set(registeredStudents);
                });
        });
    }
}
