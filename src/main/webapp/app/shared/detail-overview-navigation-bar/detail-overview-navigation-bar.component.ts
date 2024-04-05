import { AfterViewInit, Component, HostListener, Input } from '@angular/core';

@Component({
    selector: 'jhi-detail-overview-navigation-bar',
    templateUrl: './detail-overview-navigation-bar.component.html',
    styleUrls: ['./detail-overview-navigation-bar.scss'],
})
export class DetailOverviewNavigationBarComponent implements AfterViewInit {
    @Input()
    sectionHeadlines: { id: string; translationKey: string }[];

    headerHeight = 0;

    @HostListener('window:resize')
    onResize() {
        setTimeout(() => (this.headerHeight = (document.querySelector('jhi-navbar') as HTMLElement).offsetHeight));
    }

    ngAfterViewInit() {
        this.onResize();
    }

    scrollToView(id: string) {
        document.getElementById(id)?.scrollIntoView();
    }
}
