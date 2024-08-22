import { Component, Input } from '@angular/core';

export type FormSectionStatus = {
    title: string;
    valid: boolean;
    empty?: boolean;
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
        const element = document.getElementById(id);

        if (element) {
            const headerHeight = (document.querySelector('jhi-navbar') as HTMLElement).offsetHeight;

            const isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
            const offset = isSafari ? 8 : 4;

            element.style.scrollMarginTop = `calc(${offset}rem + ${headerHeight}px)`;
            element.scrollIntoView({ behavior: 'smooth', block: 'start', inline: 'start' });
        }
    }
}
