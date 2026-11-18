import { Component, inject, input, output } from '@angular/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Router } from '@angular/router';
import { TumAetUiDialogService } from '@tumaet/ui-angular';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';

@Component({
    template: '',
})
export abstract class ExerciseManageButtonComponent {
    protected router = inject(Router);
    protected dialogService = inject(TumAetUiDialogService);

    /** Emitted before navigation occurs, allowing parent components to perform cleanup (e.g., closing popovers) */
    beforeNavigate = output<void>();

    course = input.required<Course>();
    exerciseType = input.required<ExerciseType>();
    featureToggle = input<FeatureToggle | undefined>();
}
