import { Component, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SearchOverlayService } from '../services/search-overlay.service';
import { OsDetectorService } from '../services/os-detector.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSearch } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-global-search-modal',
    standalone: true,
    imports: [CommonModule, FaIconComponent],
    templateUrl: './global-search-modal.component.html',
    styleUrls: ['./global-search-modal.component.scss'],
})
export class GlobalSearchModalComponent {
    overlay = inject(SearchOverlayService);
    osDetector = inject(OsDetectorService);

    protected readonly faSearch = faSearch;

    // Global Keyboard Listener
    @HostListener('window:keydown', ['$event'])
    handleKeyboardEvent(event: KeyboardEvent) {
        // Check for Cmd+K (Mac) or Ctrl+K (Windows)
        if (event.key.toLowerCase() === 'k' && this.osDetector.isActionKey(event)) {
            event.preventDefault(); // Stop browser default
            this.overlay.toggle();
        }

        // Check for Escape to close
        if (event.key === 'Escape' && this.overlay.isOpen()) {
            event.preventDefault();
            this.overlay.close();
        }
    }

    handleSearch(query: string) {
        // TODO: Implement search functionality
        // Router navigate or API call here
        this.overlay.close();
    }
}
