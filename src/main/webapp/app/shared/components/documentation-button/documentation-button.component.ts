import { Component, Input } from '@angular/core';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';

export enum DocumentationType {
    Course = 'courses/customizable/',
}

@Component({
    selector: 'jhi-documentation-button',
    styleUrls: ['./documentation-button.component.scss'],
    template: ` <button type="button" class="text-primary documentation-button" (click)="openDocumentation()"><fa-icon [icon]="faCircleInfo"></fa-icon></button> `,
})
export class DocumentationButtonComponent {
    baseUrl = 'https://docs.artemis.ase.in.tum.de/user/';

    @Input() type: DocumentationType;

    faCircleInfo = faCircleInfo;

    openDocumentation() {
        window.open(this.baseUrl + this.type, '_blank');
    }
}
