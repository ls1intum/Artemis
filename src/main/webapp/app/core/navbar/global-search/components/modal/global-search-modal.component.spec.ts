import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Dialog } from 'primeng/dialog';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { GlobalSearchModalComponent } from './global-search-modal.component';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchResult, GlobalSearchService } from '../../services/global-search.service';

describe('GlobalSearchModalComponent', () => {
    setupTestBed({ zoneless: true });
    let component: GlobalSearchModalComponent;
    let fixture: ComponentFixture<GlobalSearchModalComponent>;
    let searchOverlayService: SearchOverlayService;

    const mockSearchOverlayService = {
        isOpen: signal(false),
        open: vi.fn(),
        close: vi.fn(),
        toggle: vi.fn(),
    };

    const mockOsDetectorService = {
        isActionKey: vi.fn(),
        actionKeyLabel: vi.fn(() => '⌘'),
        isMac: vi.fn(() => true),
    };

    const mockSearchService = {
        search: vi.fn(() => of([])),
    };

    beforeEach(() => {
        vi.clearAllMocks();
        mockSearchService.search.mockReturnValue(of([]));
        TestBed.configureTestingModule({
            imports: [GlobalSearchModalComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: SearchOverlayService, useValue: mockSearchOverlayService },
                { provide: OsDetectorService, useValue: mockOsDetectorService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: GlobalSearchService, useValue: mockSearchService },
            ],
        });

        fixture = TestBed.createComponent(GlobalSearchModalComponent);
        component = fixture.componentInstance;
        searchOverlayService = TestBed.inject(SearchOverlayService);
        fixture.detectChanges();
    });

    afterEach(() => {
        mockSearchOverlayService.isOpen.set(false);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('Keyboard Shortcuts', () => {
        it('should toggle modal when Cmd+K is pressed on Mac', () => {
            mockOsDetectorService.isActionKey.mockReturnValue(true);
            const event = new KeyboardEvent('keydown', { key: 'k', metaKey: true });
            const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

            component.handleKeyboardEvent(event);

            expect(preventDefaultSpy).toHaveBeenCalled();
            expect(searchOverlayService.toggle).toHaveBeenCalled();
        });

        it('should toggle modal when Ctrl+K is pressed on Windows', () => {
            mockOsDetectorService.isActionKey.mockReturnValue(true);
            const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true });
            const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

            component.handleKeyboardEvent(event);

            expect(preventDefaultSpy).toHaveBeenCalled();
            expect(searchOverlayService.toggle).toHaveBeenCalled();
        });

        it('should handle uppercase K key', () => {
            mockOsDetectorService.isActionKey.mockReturnValue(true);
            const event = new KeyboardEvent('keydown', { key: 'K', metaKey: true });

            component.handleKeyboardEvent(event);

            expect(searchOverlayService.toggle).toHaveBeenCalled();
        });

        it('should close modal when ESC is pressed and modal is open', () => {
            mockSearchOverlayService.isOpen.set(true);
            const event = new KeyboardEvent('keydown', { key: 'Escape' });
            const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

            component.handleKeyboardEvent(event);

            expect(preventDefaultSpy).toHaveBeenCalled();
            expect(searchOverlayService.close).toHaveBeenCalled();
        });

        it('should not close modal when ESC is pressed and modal is closed', () => {
            mockSearchOverlayService.isOpen.set(false);
            const event = new KeyboardEvent('keydown', { key: 'Escape' });

            component.handleKeyboardEvent(event);

            expect(searchOverlayService.close).not.toHaveBeenCalled();
        });

        it('should not toggle modal when K is pressed without modifier key', () => {
            mockOsDetectorService.isActionKey.mockReturnValue(false);
            const event = new KeyboardEvent('keydown', { key: 'k' });

            component.handleKeyboardEvent(event);

            expect(searchOverlayService.toggle).not.toHaveBeenCalled();
        });

        it('should not toggle modal when Cmd+K is held down (repeat event)', () => {
            mockOsDetectorService.isActionKey.mockReturnValue(true);
            const event = new KeyboardEvent('keydown', { key: 'k', metaKey: true, repeat: true });

            component.handleKeyboardEvent(event);

            expect(searchOverlayService.toggle).not.toHaveBeenCalled();
        });

        it('should not toggle modal when Cmd+K is pressed and user is not authenticated', () => {
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isAuthenticated').mockReturnValue(false);
            mockOsDetectorService.isActionKey.mockReturnValue(true);
            const event = new KeyboardEvent('keydown', { key: 'k', metaKey: true });

            component.handleKeyboardEvent(event);

            expect(searchOverlayService.toggle).not.toHaveBeenCalled();
        });
    });

    describe('Modal Rendering', () => {
        it('should show dialog when overlay is open', async () => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
            await fixture.whenStable();

            const dialog = fixture.nativeElement.querySelector('.p-dialog');
            expect(dialog).toBeTruthy();
        });

        it('should not show dialog when overlay is closed', () => {
            mockSearchOverlayService.isOpen.set(false);
            fixture.detectChanges();

            const dialog = fixture.nativeElement.querySelector('.p-dialog');
            expect(dialog).toBeFalsy();
        });

        it('should display search input when modal is open', async () => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
            await fixture.whenStable();

            const searchInput = fixture.nativeElement.querySelector('.search-input');
            expect(searchInput).toBeTruthy();
        });

        it('should display keyboard hints in footer', async () => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
            await fixture.whenStable();

            const footer = fixture.nativeElement.querySelector('.search-footer');
            const hints = fixture.nativeElement.querySelectorAll('.key-hint-small');

            expect(footer).toBeTruthy();
            expect(hints.length).toBeGreaterThan(0);
        });
    });

    describe('Auto-focus', () => {
        it('should focus search input when modal opens', async () => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
            await fixture.whenStable();

            const searchInput = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
            expect(searchInput).toBeTruthy();

            await new Promise((resolve) => setTimeout(resolve, 10));

            expect(document.activeElement).toBe(searchInput);
        });
    });

    describe('Overlay Interaction', () => {
        it('should close overlay when dialog onHide fires', () => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            const dialog = fixture.debugElement.query(By.directive(Dialog)).componentInstance as Dialog;
            dialog.onHide.emit();

            expect(searchOverlayService.close).toHaveBeenCalled();
        });

        it('should close overlay when dialog visibleChange emits false', () => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            const dialog = fixture.debugElement.query(By.directive(Dialog)).componentInstance as Dialog;
            dialog.visibleChange.emit(false);

            expect(searchOverlayService.close).toHaveBeenCalled();
        });

        it('should close overlay on destroy when modal is open', () => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            fixture.destroy();

            expect(searchOverlayService.close).toHaveBeenCalled();
        });
    });

    describe('Icons', () => {
        it('should display search icon', async () => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
            await fixture.whenStable();

            const searchIcon = fixture.nativeElement.querySelector('.search-icon');
            expect(searchIcon).toBeTruthy();
        });

        it('should display arrow icons in keyboard hints', async () => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
            await fixture.whenStable();

            const icons = fixture.nativeElement.querySelectorAll('.key-hint-small fa-icon');
            expect(icons.length).toBeGreaterThanOrEqual(2);
        });
    });

    describe('Search Pipeline', () => {
        const queryResults: GlobalSearchResult[] = [{ id: '1', type: 'exercise', title: 'Test Exercise', badge: 'Programming', metadata: {} }];
        const filteredResults: GlobalSearchResult[] = [{ id: '2', type: 'exercise', title: 'Filtered Exercise', badge: 'Quiz', metadata: {} }];

        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should re-trigger search when filter changes even if query stays the same', () => {
            mockSearchService.search.mockReturnValue(of(queryResults));

            // Type a query
            component['onSearchInput']('test');
            vi.advanceTimersByTime(300);

            expect(mockSearchService.search).toHaveBeenCalledWith('test', { type: undefined });
            expect(component['results']()).toEqual(queryResults);

            // Now toggle a filter with the same query — should still re-trigger
            mockSearchService.search.mockReturnValue(of(filteredResults));
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);

            expect(mockSearchService.search).toHaveBeenCalledWith('test', { type: 'exercise' });
            expect(component['results']()).toEqual(filteredResults);
        });

        it('should set searchError on HTTP failure', () => {
            mockSearchService.search.mockReturnValue(throwError(() => new Error('Network error')));

            component['onSearchInput']('test');
            vi.advanceTimersByTime(300);

            expect(component['searchError']()).toBe('global.search.searchFailed');
            expect(component['isLoading']()).toBe(false);
        });

        it('should cancel pending search when modal is closed via resetSearch', () => {
            mockSearchService.search.mockReturnValue(of(queryResults));

            // Type a query but don't wait for debounce
            component['onSearchInput']('test');

            // Close modal before debounce fires — resetSubject emits immediately via switchMap
            component['resetSearch']();

            vi.advanceTimersByTime(300);

            // Results should remain empty because reset cancelled the pending search
            expect(component['results']()).toEqual([]);
            expect(component['hasSearched']()).toBe(false);
        });

        it('should show base results again when filter is removed and re-added', () => {
            mockSearchService.search.mockReturnValue(of(filteredResults));

            // Add exercise filter → triggers search → shows results
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);

            // Remove exercise filter (no query) → resets to initial state
            component['removeFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual([]);
            expect(component['hasSearched']()).toBe(false);
            expect(component['isLoading']()).toBe(false);

            // Re-add exercise filter → must trigger search again, not stay loading
            mockSearchService.search.mockReturnValue(of(queryResults));
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(queryResults);
            expect(component['isLoading']()).toBe(false);
        });

        it('should route onEntityClick through the main pipeline instead of a separate subscription', () => {
            mockSearchService.search.mockReturnValue(of(filteredResults));

            component['onEntityClick']({ id: 'ex', title: 'Exercises', description: '', icon: {} as any, type: 'feature', enabled: true, filterTag: 'exercise' });
            vi.advanceTimersByTime(300);

            expect(mockSearchService.search).toHaveBeenCalledOnce();
            expect(mockSearchService.search).toHaveBeenCalledWith('', { type: 'exercise', sortBy: 'dueDate', limit: 10 });
            expect(component['results']()).toEqual(filteredResults);
        });
    });
});
