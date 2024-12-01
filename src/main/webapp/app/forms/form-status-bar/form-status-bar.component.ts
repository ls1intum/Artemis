import { AfterViewInit, Component, ElementRef, HostListener, ViewChild, input } from '@angular/core';
import { updateHeaderHeight } from 'app/shared/util/navbar.util';

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
    formStatusSections = input.required<FormSectionStatus[]>();

    @ViewChild('statusBar', { static: false }) statusBar?: ElementRef;

    @HostListener('window:resize')
    onResizeAddDistanceFromStatusBarToNavbar() {
        updateHeaderHeight();
    }

    ngAfterViewInit() {
        this.onResizeAddDistanceFromStatusBarToNavbar();
    }

    scrollToHeadline(id: string) {
        const element = document.getElementById(id);
        if (element) {
            const navbarHeight = (document.querySelector('jhi-navbar') as HTMLElement)?.getBoundingClientRect().height;
            const statusBarHeight = this.statusBar?.nativeElement.getBoundingClientRect().height;

            /** Needs to be applied to the scrollMarginTop to ensure that the scroll to element is not hidden behind header elements */
            const scrollOffsetInPx = navbarHeight + statusBarHeight;

            element.style.scrollMarginTop = `${scrollOffsetInPx}px`;
            element.scrollIntoView({ behavior: 'smooth', block: 'start', inline: 'start' });
        }
    }
}
