import { AfterViewInit, Component, afterEveryRender, inject, input } from '@angular/core';
import { updateHeaderHeight } from 'app/foundation/util/navbar.util';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Router, RouterLink } from '@angular/router';

@Component({
    selector: 'jhi-detail-overview-navigation-bar',
    templateUrl: './detail-overview-navigation-bar.component.html',
    styleUrls: ['./detail-overview-navigation-bar.scss'],
    imports: [ArtemisTranslatePipe, RouterLink],
    host: {
        '(window:resize)': 'onResizeAddDistanceFromStatusBarToNavbar()',
    },
})
export class DetailOverviewNavigationBarComponent implements AfterViewInit {
    private readonly router = inject(Router);
    private renderedUrl: string | undefined;
    private scrolledFragment: string | undefined;

    sectionHeadlines = input<{ id: string; translationKey: string }[]>([]);

    constructor() {
        afterEveryRender(() => {
            const url = this.router.url;
            if (url !== this.renderedUrl) {
                this.renderedUrl = url;
                this.scrolledFragment = undefined;
            }
            const fragment = this.router.parseUrl(url).fragment;
            if (!fragment) {
                return;
            }
            if (fragment !== this.scrolledFragment && this.sectionHeadlines().some((headline) => headline.id === fragment)) {
                const heading = document.getElementById(fragment);
                if (heading) {
                    heading.scrollIntoView();
                    this.scrolledFragment = fragment;
                }
            }
        });
    }

    onSectionClick(event: MouseEvent, id: string) {
        // RouterLink handles modified clicks and new fragments. Repeated clicks on the current fragment still scroll.
        if (event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey && this.router.parseUrl(this.router.url).fragment === id) {
            this.scrollToView(id);
        }
    }

    onResizeAddDistanceFromStatusBarToNavbar() {
        updateHeaderHeight();
    }

    ngAfterViewInit() {
        this.onResizeAddDistanceFromStatusBarToNavbar();
    }

    scrollToView(id: string) {
        document.getElementById(id)?.scrollIntoView();
    }
}
