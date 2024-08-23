import { Component, Input } from '@angular/core';
import { SizeProp } from '@fortawesome/fontawesome-svg-core';
import { faCheckCircle, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-checklist-check',
    templateUrl: './checklist-check.component.html',
    styleUrls: ['./checklist-check.component.scss'],
})
export class ChecklistCheckComponent {
    protected readonly faTimes = faTimes;
    protected readonly faCheckCircle = faCheckCircle;

    @Input() checkAttribute: boolean | undefined = false;
    @Input() iconColor?: string;
    @Input() size?: SizeProp;
}
