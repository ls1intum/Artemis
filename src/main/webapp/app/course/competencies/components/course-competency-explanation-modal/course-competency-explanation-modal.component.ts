import { Component, inject } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-course-competency-explanation-modal',
    standalone: true,
    imports: [CompetencyGraphComponent, TranslateDirective, FontAwesomeModule],
    templateUrl: './course-competency-explanation-modal.component.html',
})
export class CourseCompetencyExplanationModalComponent {
    protected readonly closeIcon = faXmark;

    protected readonly DOCUMENTATION_LINK = 'https://docs.artemis.cit.tum.de/user/adaptive-learning/';

    private readonly activeModal = inject(NgbActiveModal);

    protected closeModal(): void {
        this.activeModal.close();
    }
}
