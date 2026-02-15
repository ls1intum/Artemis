import { Component, inject } from '@angular/core';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { OsDetectorService } from '../services/os-detector.service';
@Component({
    selector: 'jhi-global-search-navbar',
    templateUrl: './global-search-navbar.component.html',
    styleUrls: ['./global-search-navbar.component.scss'],
    imports: [FaIconComponent],
})
export class GlobalSearchNavbarComponent {
    protected readonly faSearch = faSearch;
    private osDetector = inject(OsDetectorService);
    protected actionKeyLabel = this.osDetector.actionKeyLabel;
    protected isMac = this.osDetector.isMac;
    openSearch() {
        // TODO: open search modal
    }
}
