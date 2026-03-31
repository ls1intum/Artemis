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
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

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
        search: vi.fn(() => of<GlobalSearchResult[]>([])),
    };

    beforeEach(() => {
        vi.clearAllMocks();
        mockSearchService.search.mockReturnValue(of<GlobalSearchResult[]>([]));
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
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn().mockReturnValue(true) } },
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

        it('should show cached results when filter is removed and re-added without making another HTTP call', () => {
            mockSearchService.search.mockReturnValue(of(filteredResults));

            // Add exercise filter → triggers search → shows results
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);
            expect(mockSearchService.search).toHaveBeenCalledOnce();

            // Remove exercise filter (no query) → resets to initial state
            component['removeFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual([]);
            expect(component['hasSearched']()).toBe(false);
            expect(component['isLoading']()).toBe(false);

            // Re-add exercise filter → must use cached results, no new HTTP call
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);
            // Should still be only 1 call total — the re-add was served from cache
            expect(mockSearchService.search).toHaveBeenCalledOnce();
        });

        it('should not get stuck loading when filter is removed and re-added quickly within debounce window', () => {
            mockSearchService.search.mockReturnValue(of(filteredResults));

            // Add exercise filter and let it complete
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);

            // Remove and immediately re-add (within 300ms debounce)
            component['removeFilter']('exercise');
            // Don't wait for debounce — immediately re-add
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);

            // Must not be stuck loading — should show cached results
            expect(component['isLoading']()).toBe(false);
            expect(component['results']()).toEqual(filteredResults);
        });

        it('should clear placeholder cache on resetSearch so fresh results load next time', () => {
            mockSearchService.search.mockReturnValue(of(filteredResults));

            // Populate cache
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(mockSearchService.search).toHaveBeenCalledOnce();

            // Reset (simulates closing the modal)
            component['resetSearch']();

            // Re-add filter — cache was cleared, so a new HTTP call should happen
            mockSearchService.search.mockReturnValue(of(queryResults));
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(queryResults);
            expect(mockSearchService.search).toHaveBeenCalledTimes(2);
        });

        it('should route onEntityClick through the main pipeline instead of a separate subscription', () => {
            mockSearchService.search.mockReturnValue(of(filteredResults));

            component['onEntityClick']({ id: 'ex', title: 'Exercises', description: '', icon: {} as any, type: 'feature', enabled: true, filterTag: 'exercise' });
            vi.advanceTimersByTime(300);

            expect(mockSearchService.search).toHaveBeenCalledOnce();
            expect(mockSearchService.search).toHaveBeenCalledWith('', { type: 'exercise', sortBy: 'dueDate', limit: 10 });
            expect(component['results']()).toEqual(filteredResults);
        });

        it('should serve cached filter results synchronously without waiting for 300ms debounce', () => {
            mockSearchService.search.mockReturnValue(of(filteredResults));

            // First add: needs debounce + HTTP
            component['addFilter']('exercise');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(mockSearchService.search).toHaveBeenCalledOnce();

            // Remove filter — synchronous branch, no debounce needed
            component['removeFilter']('exercise');
            // Don't advance timers — verify it clears synchronously
            expect(component['results']()).toEqual([]);
            expect(component['isLoading']()).toBe(false);
            expect(component['hasSearched']()).toBe(false);

            // Re-add filter — cached branch should also run synchronously
            component['addFilter']('exercise');
            // At time 0 (no timer advancement), results should already appear from cache
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);
            expect(component['hasSearched']()).toBe(true);
            // No additional HTTP call: still only the 1 from the first add
            expect(mockSearchService.search).toHaveBeenCalledOnce();
        });
    });

    describe('View Navigation', () => {
        it('should navigate back to Navigation view on Escape when in Lecture view', () => {
            (component as any).currentView.set(SearchView.Lecture);
            mockSearchOverlayService.isOpen.set(true);

            const event = new KeyboardEvent('keydown', { key: 'Escape' });
            component.handleKeyboardEvent(event);

            expect((component as any).currentView()).toBe(SearchView.Navigation);
            expect(searchOverlayService.close).not.toHaveBeenCalled();
        });

        it('should close when Escape is pressed from Navigation view', () => {
            (component as any).currentView.set(SearchView.Navigation);
            mockSearchOverlayService.isOpen.set(true);

            const event = new KeyboardEvent('keydown', { key: 'Escape' });
            component.handleKeyboardEvent(event);

            expect(searchOverlayService.close).toHaveBeenCalled();
        });

        it('should reset selectedIndex when navigating to a new view', () => {
            (component as any).selectedIndex.set(2);

            (component as any).navigateTo(SearchView.Lecture);

            expect((component as any).selectedIndex()).toBe(-1);
        });
    });

    describe('Arrow Key Navigation', () => {
        beforeEach(() => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
        });

        it('should increment selectedIndex on ArrowDown', () => {
            (component as any).selectedIndex.set(-1);

            const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });
            component.handleKeyboardEvent(event);

            expect((component as any).selectedIndex()).toBe(0);
        });

        it('should not exceed maxIndex on ArrowDown', () => {
            const maxIdx = (component as any).maxIndex();
            (component as any).selectedIndex.set(maxIdx);

            const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });
            component.handleKeyboardEvent(event);

            expect((component as any).selectedIndex()).toBe(maxIdx);
        });

        it('should decrement selectedIndex on ArrowUp', () => {
            (component as any).selectedIndex.set(0);

            const event = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            component.handleKeyboardEvent(event);

            expect((component as any).selectedIndex()).toBe(-1);
        });

        it('should not decrement selectedIndex below -1', () => {
            (component as any).selectedIndex.set(-1);

            const event = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            component.handleKeyboardEvent(event);

            expect((component as any).selectedIndex()).toBe(-1);
        });

        it('should call preventDefault on arrow keys', () => {
            const downEvent = new KeyboardEvent('keydown', { key: 'ArrowDown' });
            const downPreventDefaultSpy = vi.spyOn(downEvent, 'preventDefault');
            component.handleKeyboardEvent(downEvent);
            expect(downPreventDefaultSpy).toHaveBeenCalled();

            const upEvent = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            const upPreventDefaultSpy = vi.spyOn(upEvent, 'preventDefault');
            component.handleKeyboardEvent(upEvent);
            expect(upPreventDefaultSpy).toHaveBeenCalled();
        });

        it('should not change selectedIndex when modal is closed', () => {
            mockSearchOverlayService.isOpen.set(false);
            (component as any).selectedIndex.set(-1);

            const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });
            component.handleKeyboardEvent(event);

            expect((component as any).selectedIndex()).toBe(-1);
        });
    });

    describe('Iris navigation', () => {
        it('should set irisSourceView to current view when navigating to Iris for the first time', () => {
            (component as any).currentView.set(SearchView.Lecture);

            (component as any).navigateTo(SearchView.Iris);

            expect((component as any).irisSourceView()).toBe(SearchView.Lecture);
            expect((component as any).currentView()).toBe(SearchView.Iris);
        });

        it('should do nothing when navigateTo(Iris) is called while already on Iris', () => {
            (component as any).currentView.set(SearchView.Iris);
            (component as any).irisSourceView.set(SearchView.Lecture);

            (component as any).navigateTo(SearchView.Iris);

            // irisSourceView must not be overwritten
            expect((component as any).irisSourceView()).toBe(SearchView.Lecture);
            expect((component as any).currentView()).toBe(SearchView.Iris);
        });

        it('should update irisSourceView and reset selectedIndex via updateIrisSource', () => {
            (component as any).selectedIndex.set(3);

            (component as any).updateIrisSource(SearchView.Lecture);

            expect((component as any).irisSourceView()).toBe(SearchView.Lecture);
            expect((component as any).selectedIndex()).toBe(-1);
        });
    });

    describe('Split panel navigation', () => {
        beforeEach(() => {
            mockSearchOverlayService.isOpen.set(true);
            (component as any).currentView.set(SearchView.Iris);
            fixture.detectChanges();
            // Set selectedIndex after detectChanges so the scroll effect sees the rendered items
            (component as any).selectedIndex.set(0);
        });

        it('should switch to right panel on ArrowRight when on Iris left panel with a selection', () => {
            (component as any).activeSplitPanel.set('left');
            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });

            component.handleKeyboardEvent(event);

            expect((component as any).activeSplitPanel()).toBe('right');
            expect((component as any).selectedIndex()).toBe(0);
        });

        it('should switch to left panel on ArrowLeft when on Iris right panel', () => {
            (component as any).activeSplitPanel.set('right');
            const event = new KeyboardEvent('keydown', { key: 'ArrowLeft' });

            component.handleKeyboardEvent(event);

            expect((component as any).activeSplitPanel()).toBe('left');
            expect((component as any).selectedIndex()).toBe(0);
        });

        it('should not switch panel on ArrowRight when selectedIndex is -1', () => {
            (component as any).activeSplitPanel.set('left');
            (component as any).selectedIndex.set(-1);
            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });

            component.handleKeyboardEvent(event);

            expect((component as any).activeSplitPanel()).toBe('left');
        });

        it('should not switch panel on ArrowRight when not on Iris view', () => {
            (component as any).currentView.set(SearchView.Lecture);
            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });

            component.handleKeyboardEvent(event);

            expect((component as any).activeSplitPanel()).toBe('left');
        });
    });

    describe('Input keydown handler', () => {
        it('should prevent ArrowRight default when on Iris left panel with a selection', () => {
            (component as any).currentView.set(SearchView.Iris);
            (component as any).activeSplitPanel.set('left');
            (component as any).selectedIndex.set(0);
            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
            const preventSpy = vi.spyOn(event, 'preventDefault');

            (component as any).onInputKeydown(event);

            expect(preventSpy).toHaveBeenCalled();
        });

        it('should prevent ArrowLeft default when on Iris right panel with a selection', () => {
            (component as any).currentView.set(SearchView.Iris);
            (component as any).activeSplitPanel.set('right');
            (component as any).selectedIndex.set(0);
            const event = new KeyboardEvent('keydown', { key: 'ArrowLeft' });
            const preventSpy = vi.spyOn(event, 'preventDefault');

            (component as any).onInputKeydown(event);

            expect(preventSpy).toHaveBeenCalled();
        });

        it('should not prevent default when not on Iris view', () => {
            (component as any).currentView.set(SearchView.Lecture);
            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
            const preventSpy = vi.spyOn(event, 'preventDefault');

            (component as any).onInputKeydown(event);

            expect(preventSpy).not.toHaveBeenCalled();
        });

        it('should not prevent default when selectedIndex is -1 (cursor navigation mode)', () => {
            (component as any).currentView.set(SearchView.Iris);
            (component as any).selectedIndex.set(-1);
            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
            const preventSpy = vi.spyOn(event, 'preventDefault');

            (component as any).onInputKeydown(event);

            expect(preventSpy).not.toHaveBeenCalled();
        });
    });
});
