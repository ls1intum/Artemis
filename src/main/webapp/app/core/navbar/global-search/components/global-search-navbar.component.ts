import { Component, inject } from '@angular/core';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { OsDetectorService } from '../services/os-detector.service';
import { SearchOverlayService } from '../services/search-overlay.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-global-search-navbar',
    standalone: true,
    templateUrl: './global-search-navbar.component.html',
    styleUrls: ['./global-search-navbar.component.scss'],
    imports: [FaIconComponent, ArtemisTranslatePipe],
})
export class GlobalSearchNavbarComponent {
    protected readonly faSearch = faSearch;
    private osDetector = inject(OsDetectorService);
    private searchOverlay = inject(SearchOverlayService);
    protected actionKeyLabel = this.osDetector.actionKeyLabel;
    protected isMac = this.osDetector.isMac;

    openSearch() {
        this.searchOverlay.open();
    }
}
