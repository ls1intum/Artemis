import { AfterViewInit, Component, HostListener, Input } from '@angular/core';
import { updateHeaderHeightScssVariableBasedOnNavbar } from 'app/shared/util/navbar.util';

export type FormSectionStatus = {
    title: string;
    valid: boolean;
    empty?: boolean;
};

const SAFARI_USER_AGENT_REGEX: RegExp = /^((?!chrome|android).)*safari/i;
const SAFARI_HEADLINE_OFFSET = 8;
const CHROME_HEADLINE_OFFSET = 4;

@Component({
    selector: 'jhi-form-status-bar',
    templateUrl: './form-status-bar.component.html',
    styleUrl: './form-status-bar.component.scss',
})
export class FormStatusBarComponent implements AfterViewInit {
    @Input()
    formStatusSections: FormSectionStatus[];

    @HostListener('window:resize')
    onResizeAddDistanceFromStatusBarToNavbar() {
        updateHeaderHeightScssVariableBasedOnNavbar();
    }

    ngAfterViewInit() {
        this.onResizeAddDistanceFromStatusBarToNavbar();
    }

    private isSafari() {
        return SAFARI_USER_AGENT_REGEX.test(navigator.userAgent);
    }

    scrollToHeadline(id: string) {
        const element = document.getElementById(id);
        if (element) {
            const headerHeight = (document.querySelector('jhi-navbar') as HTMLElement)?.offsetHeight;

            const offset = this.isSafari() ? SAFARI_HEADLINE_OFFSET : CHROME_HEADLINE_OFFSET;

            element.style.scrollMarginTop = `calc(${offset}rem + ${headerHeight}px)`;
            element.scrollIntoView({ behavior: 'smooth', block: 'start', inline: 'start' });
        }
    }
}
