import { Component, Input } from '@angular/core';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-documentation-button',
    styleUrls: ['./documentation-button.component.scss'],
    template: ` <button type="button" class="text-primary documentation-button" (click)="openDocumentation()"><fa-icon [icon]="faCircleInfo"></fa-icon></button> `,
})
export class DocumentationButtonComponent {
    faCircleInfo = faCircleInfo;

    openDocumentation() {
        console.log('OPEN');
    }
}
