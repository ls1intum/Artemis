import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-exam-checklist-check',
    templateUrl: './exam-checklist-check.component.html',
})
export class ExamChecklistCheckComponent {
    @Input() checkAttribute = false;
}
