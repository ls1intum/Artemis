import { AfterViewInit, Component, HostListener, Input } from '@angular/core';

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
export class FormStatusBarComponent implements AfterViewInit {
    @Input()
    formStatusSections: FormSectionStatus[];

    @HostListener('window:resize')
    onResize() {
        setTimeout(() => {
            const headerHeight = (document.querySelector('jhi-navbar') as HTMLElement).offsetHeight;
            document.documentElement.style.setProperty('--header-height', `${headerHeight}px`);
        });
    }

    ngAfterViewInit() {
        this.onResize();
    }

    scrollToHeadline(id: string) {
        const element = document.getElementById(id);
        if (element) {
            element.style.scrollMarginTop = 'calc(2rem + 78px)';
            element.scrollIntoView();
        }
    }
}
