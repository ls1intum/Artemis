import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-detail-overview-navigation-bar',
    templateUrl: './detail-overview-navigation-bar.component.html',
    styleUrls: ['./detail-overview-navigation-bar.scss'],
})
export class DetailOverviewNavigationBarComponent {
    @Input()
    sectionHeadlines: { id: string; translationKey: string }[];

    scrollToView(id: string) {
        document.getElementById(id)?.scrollIntoView();
    }
}
