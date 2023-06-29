import { Component, Input } from '@angular/core';
import { faCheckCircle, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-checklist-check',
    templateUrl: './checklist-check.component.html',
    styleUrls: ['./checklist-check.component.scss'],
})
export class ChecklistCheckComponent {
    @Input() checkAttribute: boolean | undefined = false;

    // Icons
    faTimes = faTimes;
    faCheckCircle = faCheckCircle;
}
