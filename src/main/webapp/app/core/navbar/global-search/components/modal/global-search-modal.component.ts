import { Component, ElementRef, HostListener, OnDestroy, inject, viewChild } from '@angular/core';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowDown, faArrowUp, faSearch } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DialogModule } from 'primeng/dialog';

@Component({
    selector: 'jhi-global-search-modal',
    standalone: true,
    imports: [DialogModule, FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './global-search-modal.component.html',
    styleUrls: ['./global-search-modal.component.scss'],
})
export class GlobalSearchModalComponent implements OnDestroy {
    protected overlay = inject(SearchOverlayService);
    protected osDetector = inject(OsDetectorService);

    protected readonly faSearch = faSearch;
    protected readonly faArrowUp = faArrowUp;
    protected readonly faArrowDown = faArrowDown;

    protected searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

    protected focusInput() {
        setTimeout(() => {
            this.searchInput()?.nativeElement.focus();
        }, 0);
    }

    @HostListener('window:keydown', ['$event'])
    handleKeyboardEvent(event: KeyboardEvent) {
        if (event.key.toLowerCase() === 'k' && this.osDetector.isActionKey(event)) {
            event.preventDefault();
            this.overlay.toggle();
        }
        if (event.key === 'Escape' && this.overlay.isOpen()) {
            event.preventDefault();
            this.overlay.close();
        }
    }

    ngOnDestroy() {
        if (this.overlay.isOpen()) {
            this.overlay.close();
        }
    }

    protected handleSearch(query: string) {
        // TODO: Implement search functionality
        this.overlay.close();
    }
}
