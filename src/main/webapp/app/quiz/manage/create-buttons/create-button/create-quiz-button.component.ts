import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { faCheckDouble, faPlus } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-quiz-create-button',
    imports: [TranslateDirective, FaIconComponent],
    templateUrl: './create-quiz-button.component.html',
})
export class CreateQuizButtonComponent {
    course = input<Course | undefined>();
    translationKey = input<string>('artemisApp.quizExercise.home.createLabel');
    private router = inject(Router);
    private modalService = inject(NgbModal);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faPlus = faPlus;
    protected readonly faCheckDouble = faCheckDouble;

    linkToExerciseCreation() {
        this.modalService.dismissAll();
        this.router.navigate(['/course-management', this.course()?.id, 'quiz-exercises', 'new']);
    }
}
