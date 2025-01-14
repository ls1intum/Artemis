import { Component, inject } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-quiz-scoring-info-modal',
    templateUrl: './quiz-scoring-info-modal.component.html',
    imports: [TranslateDirective, FaIconComponent],
})
export class QuizScoringInfoModalComponent {
    private modalService = inject(NgbModal);

    // Icons
    farQuestionCircle = faQuestionCircle;

    /**
     * Open a large modal with the given content.
     * @param content the content to display
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }
}
