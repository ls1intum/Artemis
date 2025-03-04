import { AfterViewInit, Component, HostListener, Input } from '@angular/core';
import { updateHeaderHeight } from 'app/shared/util/navbar.util';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-detail-overview-navigation-bar',
    templateUrl: './detail-overview-navigation-bar.component.html',
    styleUrls: ['./detail-overview-navigation-bar.scss'],
    imports: [ArtemisTranslatePipe],
})
export class DetailOverviewNavigationBarComponent implements AfterViewInit {
    @Input()
    sectionHeadlines: { id: string; translationKey: string }[];

    @HostListener('window:resize')
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
