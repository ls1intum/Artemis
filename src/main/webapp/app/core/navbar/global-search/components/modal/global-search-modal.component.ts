import { Component, ElementRef, HostListener, OnDestroy, inject, viewChild } from '@angular/core';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { AccountService } from 'app/core/auth/account.service';
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
    private readonly overlay = inject(SearchOverlayService);
    private readonly osDetector = inject(OsDetectorService);
    private readonly accountService = inject(AccountService);

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
        if (this.isToggleShortcut(event)) {
            event.preventDefault();
            this.overlay.toggle();
        }
        if (event.key === 'Escape' && this.overlay.isOpen()) {
            event.preventDefault();
            this.overlay.close();
        }
    }

    private isToggleShortcut(event: KeyboardEvent): boolean {
        return event.key.toLowerCase() === 'k' && this.osDetector.isActionKey(event) && this.accountService.isAuthenticated() && !event.repeat;
    }

    ngOnDestroy() {
        if (this.overlay.isOpen()) {
            this.overlay.close();
        }
    }
}
