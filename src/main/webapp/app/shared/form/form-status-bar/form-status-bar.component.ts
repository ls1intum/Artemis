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
        const target = document.getElementById(id);
        if (!target) return;

        const container = document.getElementById('course-body-container');
        if (!container) {
            // Fallback for future layout changes
            target.scrollIntoView({ behavior: 'smooth', block: 'start', inline: 'nearest' });
            return;
        }

        const navbarEl = document.querySelector('jhi-navbar') as HTMLElement | null;
        const navbarHeight = navbarEl?.getBoundingClientRect().height ?? 0;
        const statusBarHeight = this.statusBar?.nativeElement?.getBoundingClientRect().height ?? 0;

        // Total offset so that the target headline is not hidden behind the navbar/status bar.
        const offset = navbarHeight + statusBarHeight;

        // Compute the target position within the container:
        const containerRect = container.getBoundingClientRect();
        const targetRect = target.getBoundingClientRect();

        const currentScrollTop = container.scrollTop;
        const targetTopWithinContainer = targetRect.top - containerRect.top + currentScrollTop;

        container.scrollTo({
            top: Math.max(0, targetTopWithinContainer - offset),
            behavior: 'smooth',
        });
    }
}
