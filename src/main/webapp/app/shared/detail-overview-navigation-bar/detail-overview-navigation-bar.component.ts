import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-detail-overview-navigation-bar',
    templateUrl: './detail-overview-navigation-bar.component.html',
    styleUrls: ['./detail-overview-navigation-bar.scss'],
})
export class DetailOverviewNavigationBarComponent implements OnInit {
    @Input()
    sectionHeadlines: Record<string, string>; // id, translation key

    sectionHeadlinesArray: { id: string; translationKey: string }[] = [];

    ngOnInit() {
        this.sectionHeadlinesArray = Object.entries(this.sectionHeadlines).map(([id, translationKey]) => {
            return { id: id, translationKey: translationKey };
        });
    }

    scrollToView(id: string) {
        document.getElementById(id)?.scrollIntoView();
    }
}
