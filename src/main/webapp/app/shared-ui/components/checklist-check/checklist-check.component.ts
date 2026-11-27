import { Component, input } from '@angular/core';
import { SizeProp } from '@fortawesome/fontawesome-svg-core';
import { faCheckCircle } from '@fortawesome/free-solid-svg-icons/faCheckCircle';
import { faTimes } from '@fortawesome/free-solid-svg-icons/faTimes';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-checklist-check',
    templateUrl: './checklist-check.component.html',
    imports: [FaIconComponent],
})
export class ChecklistCheckComponent {
    protected readonly faTimes = faTimes;
    protected readonly faCheckCircle = faCheckCircle;

    checkAttribute = input<boolean | undefined>(false);
    iconColor = input<string | undefined>(undefined);
    size = input<SizeProp | undefined>(undefined);
}
