import { Component, inject, input, output } from '@angular/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    template: '',
})
export abstract class ExerciseManageButtonComponent {
    protected router = inject(Router);
    // NgbModal is kept for dismissAll functionality used by ExerciseCreateButtonComponent
    protected modalService = inject(NgbModal);
    protected dialogService = inject(DialogService);

    /** Emitted before navigation occurs, allowing parent components to perform cleanup (e.g., closing popovers) */
    beforeNavigate = output<void>();

    course = input.required<Course>();
    exerciseType = input.required<ExerciseType>();
    featureToggle = input<FeatureToggle | undefined>();
}
