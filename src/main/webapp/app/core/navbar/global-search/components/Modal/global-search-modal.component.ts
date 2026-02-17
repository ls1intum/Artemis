import { Component, ElementRef, HostListener, effect, inject, viewChild } from '@angular/core';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowDown, faArrowUp, faSearch } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-global-search-modal',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './global-search-modal.component.html',
    styleUrls: ['./global-search-modal.component.scss'],
})
export class GlobalSearchModalComponent {
    protected overlay = inject(SearchOverlayService);
    protected osDetector = inject(OsDetectorService);

    protected readonly faSearch = faSearch;
    protected readonly faArrowUp = faArrowUp;
    protected readonly faArrowDown = faArrowDown;

    // Get reference to the search input element
    searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

    constructor() {
        // Watch for when the modal opens and focus the input
        effect(() => {
            if (this.overlay.isOpen() && this.searchInput()) {
                // Focus the input after the current execution completes
                setTimeout(() => {
                    this.searchInput()?.nativeElement.focus();
                }, 0);
            }
        });
    }

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
