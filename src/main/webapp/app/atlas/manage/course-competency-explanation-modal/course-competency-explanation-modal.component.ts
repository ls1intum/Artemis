import { Component, inject } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-course-competency-explanation-modal',
    imports: [TranslateDirective, FontAwesomeModule],
    templateUrl: './course-competency-explanation-modal.component.html',
    styleUrl: './course-competency-explanation-modal.component.scss',
})
export class CourseCompetencyExplanationModalComponent {
    protected readonly closeIcon = faXmark;

    protected readonly DOCUMENTATION_LINK = 'https://docs.artemis.tum.de/instructor/adaptive-learning';

    private readonly activeModal = inject(NgbActiveModal);

    protected closeModal(): void {
        this.activeModal.close();
    }
}
