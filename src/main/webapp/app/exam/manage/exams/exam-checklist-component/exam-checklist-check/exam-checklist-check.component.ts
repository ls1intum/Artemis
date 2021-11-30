import { Component, Input } from '@angular/core';
import { faCheckCircle, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exam-checklist-check',
    templateUrl: './exam-checklist-check.component.html',
})
export class ExamChecklistCheckComponent {
    @Input() checkAttribute: boolean | undefined = false;

    // Icons
    faTimes = faTimes;
    faCheckCircle = faCheckCircle;
}
