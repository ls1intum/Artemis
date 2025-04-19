import { Component, inject, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Router } from '@angular/router';
import { faKeyboard, faPlus } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-programming-create-button',
    imports: [TranslateDirective, FeatureToggleLinkDirective, FaIconComponent],
    templateUrl: './create-programming-button.component.html',
})
export class CreateProgrammingButtonComponent {
    course = input<Course | undefined>();
    translationKey = input<string>('artemisApp.programmingExercise.home.createLabel');
    private router = inject(Router);
    private modalService = inject(NgbModal);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faPlus = faPlus;
    protected readonly faKeyboard = faKeyboard;

    linkToExerciseCreation() {
        this.modalService.dismissAll();
        this.router.navigate(['/course-management', this.course()?.id, 'programming-exercises', 'new']);
    }
}
