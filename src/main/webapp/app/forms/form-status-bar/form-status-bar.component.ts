import { Component, Input } from '@angular/core';

export type FormSectionStatus = {
    title: string;
    valid: boolean;
};

@Component({
    selector: 'jhi-form-status-bar',
    templateUrl: './form-status-bar.component.html',
    styleUrl: './form-status-bar.component.scss',
})
export class FormStatusBarComponent {
    @Input()
    formStatusSections: FormSectionStatus[];

    scrollToHeadline(id: string) {
        document.getElementById(id)?.scrollIntoView();
    }
}
