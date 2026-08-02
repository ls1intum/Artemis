import { Component, inject } from '@angular/core';
import { faBan, faCheck, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-iris-in-class-quiz-start-warning',
    templateUrl: './iris-in-class-quiz-start-warning.component.html',
    styleUrls: ['../../../../exercise/exercise-update-warning/exercise-update-warning.component.scss'],
    imports: [TranslateDirective, FaIconComponent],
})
export class IrisInClassQuizStartWarningComponent {
    private readonly activeModal = inject(NgbActiveModal);

    readonly confirmed = new Subject<void>();

    protected readonly faBan = faBan;
    protected readonly faCheck = faCheck;
    protected readonly faExclamationTriangle = faExclamationTriangle;

    cancel(): void {
        this.activeModal.close();
    }

    startQuiz(): void {
        this.confirmed.next();
        this.activeModal.close();
    }
}
