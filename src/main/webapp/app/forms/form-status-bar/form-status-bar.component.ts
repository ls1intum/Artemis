import { AfterViewInit, Component, ElementRef, HostListener, Input, Renderer2, ViewChild } from '@angular/core';
import { updateHeaderHeightScssVariableBasedOnNavbar } from 'app/shared/util/navbar.util';

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

    @ViewChild('statusBar', { static: false }) statusBar: ElementRef;

    constructor(private renderer: Renderer2) {}

    @HostListener('window:resize')
    onResizeAddDistanceFromStatusBarToNavbar() {
        updateHeaderHeightScssVariableBasedOnNavbar();
    }

    ngAfterViewInit() {
        this.onResizeAddDistanceFromStatusBarToNavbar();
    }

    scrollToHeadline(id: string) {
        const element = document.getElementById(id);
        if (element) {
            const navbarHeight = (this.renderer.selectRootElement('jhi-navbar', true) as HTMLElement)?.getBoundingClientRect().height;
            const breadcrumbContainerHeight = (this.renderer.selectRootElement('.breadcrumb-container', true) as HTMLElement)?.getBoundingClientRect().height;
            const statusBarHeight = this.statusBar.nativeElement.getBoundingClientRect().height;

            /** Needs to be applied to the scrollMarginTop to ensure that the scroll to element is not hidden behind header elements */
            const scrollOffsetInPx = navbarHeight + breadcrumbContainerHeight + statusBarHeight;

            element.style.scrollMarginTop = `${scrollOffsetInPx}px`;
            element.scrollIntoView({ behavior: 'smooth', block: 'start', inline: 'start' });
        }
    }
}
