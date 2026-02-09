import { AfterViewInit, Component, ElementRef, HostListener, ViewChild, input } from '@angular/core';
import { updateHeaderHeight } from 'app/shared/util/navbar.util';
import { ChecklistCheckComponent } from 'app/shared/components/checklist-check/checklist-check.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export type FormSectionStatus = {
    title: string;
    valid: boolean;
    empty?: boolean;
};

@Component({
    selector: 'jhi-form-status-bar',
    templateUrl: './form-status-bar.component.html',
    styleUrl: './form-status-bar.component.scss',
    imports: [ChecklistCheckComponent, ArtemisTranslatePipe],
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
        const headlineElement = document.getElementById(id);
        if (!headlineElement) return;

        // In this view the page scrolls inside a dedicated container (not via window scrolling).
        const scrollContainerElement = document.getElementById('course-body-container');
        if (!scrollContainerElement) {
            // Fallback for future layout changes where the main scroll container might not exist.
            headlineElement.scrollIntoView({ behavior: 'smooth', block: 'start', inline: 'nearest' });
            return;
        }

        const navbarElement = document.querySelector('jhi-navbar') as HTMLElement | null;
        const navbarHeightPx = navbarElement?.getBoundingClientRect().height ?? 0;
        const statusBarHeightPx = this.statusBar?.nativeElement?.getBoundingClientRect().height ?? 0;

        // Total vertical offset so the headline is not hidden behind status bar.
        const headerOverlapOffsetPx = navbarHeightPx + statusBarHeightPx;

        // Convert viewport coordinates into "scroll container content coordinates" to compute an exact scrollTop.
        const scrollContainerViewportTopPx = scrollContainerElement.getBoundingClientRect().top;
        const headlineViewportTopPx = headlineElement.getBoundingClientRect().top;

        // Current vertical scroll position inside the scroll container.
        const scrollContainerScrollTopPx = scrollContainerElement.scrollTop;

        // Headline's top position within the scroll container's scrollable content.
        const headlineTopInScrollContainerPx = headlineViewportTopPx - scrollContainerViewportTopPx + scrollContainerScrollTopPx;

        scrollContainerElement.scrollTo({
            top: Math.max(0, headlineTopInScrollContainerPx - headerOverlapOffsetPx),
            behavior: 'smooth',
        });
    }
}
