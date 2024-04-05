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

    headerHeight? = 0;

    @HostListener('window:resize')
    onResize() {
        setTimeout(() => (this.headerHeight = (document.querySelector('jhi-navbar') as HTMLElement).offsetHeight));
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
