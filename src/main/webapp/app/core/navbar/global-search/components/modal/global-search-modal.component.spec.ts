import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Dialog } from 'primeng/dialog';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { GlobalSearchModalComponent } from './global-search-modal.component';
import { SearchOverlayService } from '../../services/search-overlay.service';
import { OsDetectorService } from '../../services/os-detector.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchResult } from 'app/openapi/model/global-search-result';
import { GlobalSearchApi } from 'app/openapi/api/global-search-api';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Router } from '@angular/router';
import { GlobalSearchNavigationViewComponent } from '../views/navigation-view/global-search-navigation-view.component';
import { GlobalSearchActionItemComponent } from '../action-item/global-search-action-item.component';
import { GlobalSearchIrisAnswerComponent } from '../views/iris-answer/global-search-iris-answer.component';

describe('GlobalSearchModalComponent', () => {
    let component: GlobalSearchModalComponent;
    let fixture: ComponentFixture<GlobalSearchModalComponent>;
    let searchOverlayService: SearchOverlayService;

    // JSDOM does not implement scrollIntoView; mock it to prevent TypeError in the navigation-view effect
    const originalScrollIntoView = HTMLElement.prototype.scrollIntoView;

    // JSDOM's CSSStyleDeclaration proxy rejects CSS custom property assignments via index notation
    // (e.g. el.style['--p-dialog-border-radius'] = '…') — Angular's NoneEncapsulationDomRenderer uses
    // that path, and PrimeNG's Dialog applies its design tokens this way when visible=true.
    // Wrapping the style getter redirects custom-property assignments through setProperty() instead.
    const originalStyleDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'style')!;
    beforeAll(() => {
        HTMLElement.prototype.scrollIntoView = vi.fn();
        Object.defineProperty(HTMLElement.prototype, 'style', {
            get() {
                const style = originalStyleDescriptor.get!.call(this) as CSSStyleDeclaration;
                return new Proxy(style, {
                    set(target, prop, value) {
                        if (typeof prop === 'string' && prop.startsWith('--')) {
                            // CSS custom properties must go through setProperty in JSDOM
                            target.setProperty(prop, String(value));
                        } else if (typeof prop === 'string' && /^\d+$/.test(prop)) {
                            // Numeric indices (el.style[0], el.style[1], …) are read-only
                            // in the CSS spec; silently swallow assignments (e.g. from NgStyle
                            // receiving a plain string instead of a style object).
                        } else {
                            (target as unknown as Record<string, unknown>)[prop as string] = value;
                        }
                        return true;
                    },
                });
            },
            configurable: true,
        });
    });

    afterAll(() => {
        HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
        Object.defineProperty(HTMLElement.prototype, 'style', originalStyleDescriptor);
    });

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
        globalSearch: vi.fn(() => of<GlobalSearchResult[]>([])),
    };

    const mockCourseStorageService = {
        getCourse: vi.fn<(courseId: number) => Course | undefined>().mockReturnValue(undefined),
    };

    beforeEach(() => {
        vi.clearAllMocks();
        mockSearchService.globalSearch.mockReturnValue(of<GlobalSearchResult[]>([]));
        TestBed.configureTestingModule({
            imports: [GlobalSearchModalComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: SearchOverlayService, useValue: mockSearchOverlayService },
                { provide: OsDetectorService, useValue: mockOsDetectorService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: GlobalSearchApi, useValue: mockSearchService },
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn().mockReturnValue(true) } },
                { provide: CourseStorageService, useValue: mockCourseStorageService },
            ],
        });

        // GlobalSearchActionItemComponent uses CSS custom-property bindings ([style.--accent]) that
        // JSDOM's CSSStyleDeclaration proxy rejects. Mock it (and GlobalSearchIrisAnswerComponent)
        // inside the navigation view so the modal spec is isolated from their rendering details.
        TestBed.overrideComponent(GlobalSearchNavigationViewComponent, {
            remove: { imports: [GlobalSearchActionItemComponent, GlobalSearchIrisAnswerComponent] },
            add: { imports: [MockComponent(GlobalSearchActionItemComponent), MockComponent(GlobalSearchIrisAnswerComponent)] },
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

    describe('Filter Picker Home Screen', () => {
        /** Presses the OS filter shortcut (Cmd/Ctrl+F) and hands back the event so preventDefault can be asserted. */
        function pressFilterShortcut(repeat = false): KeyboardEvent {
            const event = new KeyboardEvent('keydown', { key: 'f', metaKey: true, repeat });
            vi.spyOn(event, 'preventDefault');
            component.handleKeyboardEvent(event);
            return event;
        }

        beforeEach(() => {
            mockOsDetectorService.isActionKey.mockReturnValue(true);
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
        });

        it('opens the guided picker as the home screen', () => {
            expect((component as any).filterPickerOpen()).toBe(true);
        });

        it('keeps the picker open on repeated Cmd+F instead of toggling back to the searchable-entity list', () => {
            pressFilterShortcut();
            expect((component as any).filterPickerOpen()).toBe(true);

            pressFilterShortcut();
            expect((component as any).filterPickerOpen()).toBe(true);
        });

        it('steps back to the root picker on Cmd+F from a value menu', () => {
            (component as any).onSearchInput('type:');
            expect((component as any).operator()).toBeDefined();

            pressFilterShortcut();

            expect((component as any).operator()).toBeUndefined();
            expect((component as any).filterPickerOpen()).toBe(true);
        });

        it('blocks the browser find bar even where the shortcut is inert', () => {
            const event = pressFilterShortcut();

            expect(event.preventDefault).toHaveBeenCalled();
        });

        it('does not cover the lecture view with the picker, which cannot carry filters', () => {
            (component as any).navigateTo(SearchView.Lecture);
            (component as any).filterPickerOpen.set(false);

            pressFilterShortcut();

            expect((component as any).filterPickerOpen()).toBe(false);
        });

        it('closes the modal on Escape at the root picker, since nothing sits behind the home screen', () => {
            const event = new KeyboardEvent('keydown', { key: 'Escape' });

            component.handleKeyboardEvent(event);

            expect(searchOverlayService.close).toHaveBeenCalled();
        });

        it('returns to the results behind the picker on Escape instead of closing the modal', () => {
            (component as any).hasSearched.set(true);

            component.handleKeyboardEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

            expect((component as any).filterPickerOpen()).toBe(false);
            expect(searchOverlayService.close).not.toHaveBeenCalled();
        });

        it('steps back one level on Escape inside the exclude level', () => {
            (component as any).filter.excludeMode.set(true);
            expect((component as any).menuHeaderKey()).toBe('global.search.chooseExclude');

            component.handleKeyboardEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

            expect((component as any).filterPickerOpen()).toBe(true);
            expect((component as any).menuHeaderKey()).toBe('global.search.addFilter');
            expect(searchOverlayService.close).not.toHaveBeenCalled();
        });

        it('shows the back hint while a level remains, and the close hint at the root', () => {
            expect((component as any).escapeHintKey()).toBe('global.search.toClose');

            (component as any).filter.excludeMode.set(true);

            expect((component as any).escapeHintKey()).toBe('global.search.back');
        });

        it('offers to search anyway, rather than to close, when the typed value is not a filter', () => {
            (component as any).onSearchInput('type:candle');

            expect((component as any).deadEnd()).toBe(true);
            expect((component as any).escapeHintKey()).toBe('global.search.searchAnyway');
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
        const queryResults: GlobalSearchResult[] = [{ id: '1', type: 'exercise', title: 'Test Exercise', badge: 'programming', metadata: {} }];
        const filteredResults: GlobalSearchResult[] = [{ id: '2', type: 'exercise', title: 'Filtered Exercise', badge: 'quiz', metadata: {} }];

        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('does not remove a filter on backspace over the empty input', () => {
            component['tokens'].set([
                { facet: 'type', value: 'exercise' },
                { facet: 'type', value: 'lecture' },
            ]);

            component['onBackspaceRemoveFilter']();

            expect(component['activeFilters']()).toEqual(['exercise', 'lecture']);
            expect(component['tokens']()).toHaveLength(2);
        });

        it('should re-trigger search when filter changes even if query stays the same', () => {
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));

            // Type a query
            component['onSearchInput']('test');
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('test', undefined, undefined, undefined);
            expect(component['results']()).toEqual(queryResults);

            // Now toggle a filter with the same query — should still re-trigger
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));
            component['addFilter'](['exercise']);
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('test', 'exercise', undefined, undefined);
            expect(component['results']()).toEqual(filteredResults);
        });

        it('leaves the guided picker and runs a normal search when typed text matches no filter action', () => {
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));
            component['openFilterPicker']();
            expect(component['filterPickerOpen']()).toBe(true);

            component['onSearchInput']('deep');
            vi.advanceTimersByTime(300);

            expect(component['filterPickerOpen']()).toBe(false);
            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('deep', undefined, undefined, undefined);
        });

        it('returns to the guided picker when the search box is cleared and no filter is left', () => {
            component['openFilterPicker']();
            component['onSearchInput']('deep');
            vi.advanceTimersByTime(300);
            expect(component['filterPickerOpen']()).toBe(false);

            component['onSearchInput']('');

            expect(component['filterPickerOpen']()).toBe(true);
        });

        it('stays on the results when the search box is cleared while a filter is still applied', () => {
            component['tokens'].set([{ facet: 'type', value: 'exercise' }]);
            component['onSearchInput']('deep');
            vi.advanceTimersByTime(300);

            component['onSearchInput']('');

            expect(component['filterPickerOpen']()).toBe(false);
        });

        it('leaves the guided picker as soon as plain text is typed, because the picker no longer narrows', () => {
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));
            component['openFilterPicker']();

            component['onSearchInput']('course');
            vi.advanceTimersByTime(300);

            expect(component['filterPickerOpen']()).toBe(false);
            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('course', undefined, undefined, undefined);
        });

        it('keeps the search text while a filter is composed through the picker', () => {
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));
            component['onSearchInput']('linear regression');
            vi.advanceTimersByTime(300);

            component['toggleFilterMenu']();
            component['onOptionSelected'](component['menuOptions']().findIndex((option) => option.action.kind === 'operator'));
            component['onOptionSelected'](component['menuOptions']().findIndex((option) => option.action.kind === 'value'));
            vi.advanceTimersByTime(300);

            expect(component['searchQuery']()).toBe('linear regression');
            expect(component['tokens']()).toHaveLength(1);
            expect(mockSearchService.globalSearch).toHaveBeenLastCalledWith('linear regression', 'course', undefined, undefined);
        });

        it('switches back to the results on a second Cmd+F once a search sits behind the menu', () => {
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));
            component['onSearchInput']('linear regression');
            vi.advanceTimersByTime(300);

            component['toggleFilterMenu']();
            expect(component['filterMenuOpen']()).toBe(true);

            component['toggleFilterMenu']();

            expect(component['filterMenuOpen']()).toBe(false);
            expect(component['searchQuery']()).toBe('linear regression');
        });

        it('searches the text in front of the operator, then the raw text once the literal row is chosen', () => {
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));

            component['onSearchInput']('nsjkfncs type:candle');
            vi.advanceTimersByTime(300);

            expect(component['deadEnd']()).toBe(true);
            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('nsjkfncs', undefined, undefined, undefined);

            component['onOptionSelected'](0);
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenLastCalledWith('nsjkfncs type:candle', undefined, undefined, undefined);
            expect(component['filterMenuOpen']()).toBe(false);
        });

        it('should set searchError on HTTP failure', () => {
            mockSearchService.globalSearch.mockReturnValue(throwError(() => new Error('Network error')));

            component['onSearchInput']('test');
            vi.advanceTimersByTime(300);

            expect(component['searchError']()).toBe('global.search.searchFailed');
            expect(component['isLoading']()).toBe(false);
        });

        it('should cancel pending search when modal is closed via resetSearch', () => {
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));

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
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));

            // Add exercise filter → triggers search → shows results
            component['addFilter'](['exercise']);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);
            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();

            // Remove exercise filter (no query) → resets to initial state
            component['onChipRemoved'](0);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual([]);
            expect(component['hasSearched']()).toBe(false);
            expect(component['isLoading']()).toBe(false);

            // Re-add exercise filter → must use cached results, no new HTTP call
            component['addFilter'](['exercise']);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);
            // Should still be only 1 call total — the re-add was served from cache
            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();
        });

        it('should not get stuck loading when filter is removed and re-added quickly within debounce window', () => {
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));

            // Add exercise filter and let it complete
            component['addFilter'](['exercise']);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);

            // Remove and immediately re-add (within 300ms debounce)
            component['onChipRemoved'](0);
            // Don't wait for debounce — immediately re-add
            component['addFilter'](['exercise']);
            vi.advanceTimersByTime(300);

            // Must not be stuck loading — should show cached results
            expect(component['isLoading']()).toBe(false);
            expect(component['results']()).toEqual(filteredResults);
        });

        it('should clear placeholder cache on resetSearch so fresh results load next time', () => {
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));

            // Populate cache
            component['addFilter'](['exercise']);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();

            // Reset (simulates closing the modal)
            component['resetSearch']();

            // Re-add filter — cache was cleared, so a new HTTP call should happen
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));
            component['addFilter'](['exercise']);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(queryResults);
            expect(mockSearchService.globalSearch).toHaveBeenCalledTimes(2);
        });

        it('should route onEntityClick through the main pipeline instead of a separate subscription', () => {
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));

            component['onEntityClick']({ id: 'ex', title: 'Exercises', description: '', icon: {} as any, type: 'feature', enabled: true, filterTags: ['exercise'] });
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();
            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('', 'exercise', undefined, undefined);
            expect(component['results']()).toEqual(filteredResults);
        });

        it('should serve cached filter results synchronously without waiting for 300ms debounce', () => {
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));

            // First add: needs debounce + HTTP
            component['addFilter'](['exercise']);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();

            // Remove filter — synchronous branch, no debounce needed
            component['onChipRemoved'](0);
            // Don't advance timers — verify it clears synchronously
            expect(component['results']()).toEqual([]);
            expect(component['isLoading']()).toBe(false);
            expect(component['hasSearched']()).toBe(false);

            // Re-add filter — cached branch should also run synchronously
            component['addFilter'](['exercise']);
            // At time 0 (no timer advancement), results should already appear from cache
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);
            expect(component['hasSearched']()).toBe(true);
            // No additional HTTP call: still only the 1 from the first add
            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();
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
            // The home screen opens the guided picker; these tests exercise results / chip navigation, so close it
            // and re-render so the navigation view (and its result views) mounts.
            (component as any).filterPickerOpen.set(false);
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

        it('should increment selectedIndex by exactly 1 when a DOM keydown event fires (no double-handling)', () => {
            (component as any).selectedIndex.set(-1);

            // Dispatch a real DOM event so that both the @HostListener and any template
            // forwarding paths have a chance to fire — only one should handle it.
            const event = new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true });
            fixture.nativeElement.dispatchEvent(event);

            expect((component as any).selectedIndex()).toBe(0);
        });

        it('re-picks a keyboard-selected chip on Enter (same as clicking it)', () => {
            (component as any).tokens.set([
                { facet: 'type', value: 'exercise' },
                { facet: 'course', value: '5' },
            ]);
            (component as any).selectedChip.set(1);

            component.handleKeyboardEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

            expect((component as any).editingChip()).toBe(1);
            expect((component as any).selectedChip()).toBe(-1);
            expect((component as any).searchQuery()).toBe('course:');
        });
    });

    describe('Context Filters', () => {
        let router: Router;

        beforeEach(() => {
            vi.useFakeTimers();
            router = TestBed.inject(Router);
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should apply course filter when modal opens on a course page', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42/statistics', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(42);
            expect(component['chips']().find((chip) => chip.family === 'course')?.label).toBe('Intro to CS');
        });

        it('should apply course and type filter when modal opens on exercises tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 10, title: 'Algorithms' });
            Object.defineProperty(router, 'url', { get: () => '/courses/10/exercises', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(10);
            expect(component['chips']().find((chip) => chip.family === 'course')?.label).toBe('Algorithms');
            expect(component['activeFilters']()).toEqual(['exercise']);
        });

        it('should apply lecture filter when on lectures tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 5, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/courses/5/lectures', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['activeFilters']()).toEqual(['lecture', 'lecture_unit']);
        });

        it('should apply communication filters when on communication tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 5, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/courses/5/communication', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['activeFilters']()).toEqual(['channel', 'post', 'answer_post']);
        });

        it('should apply exam filter when on exams tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 5, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/courses/5/exams', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['activeFilters']()).toEqual(['exam']);
        });

        it('should apply faq filter when on faq tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 5, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/courses/5/faq', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['activeFilters']()).toEqual(['faq']);
        });

        it('should not apply any filter when on course overview (no specific course)', () => {
            Object.defineProperty(router, 'url', { get: () => '/courses', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBeUndefined();
            expect(component['chips']().some((chip) => chip.family === 'course')).toBe(false);
            expect(component['activeFilters']()).toEqual([]);
        });

        it('should not apply type filter for non-mapped tabs like statistics', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 7, title: 'Math' });
            Object.defineProperty(router, 'url', { get: () => '/courses/7/statistics', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(7);
            expect(component['activeFilters']()).toEqual([]);
        });

        it('should use fallback label when course is not in storage', () => {
            mockCourseStorageService.getCourse.mockReturnValue(undefined);
            Object.defineProperty(router, 'url', { get: () => '/courses/99/exercises', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['chips']().find((chip) => chip.family === 'course')?.label).toBe('global.search.courseFallbackLabel');
        });

        it('should pass courseId to globalSearch API call', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42/exercises', configurable: true });
            mockSearchService.globalSearch.mockReturnValue(of([]));

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            component['onSearchInput']('test');
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('test', 'exercise', [42], undefined);
        });

        it('should pass excludeCourseIds to globalSearch for a negated course token', () => {
            mockSearchService.globalSearch.mockReturnValue(of([]));
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            component['tokens'].set([{ facet: 'course', value: '7', negate: true }]);
            component['onSearchInput']('test');
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('test', undefined, undefined, [7]);
        });

        it('applies context filters on open and leaves them intact on backspace over the empty input', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42/exercises', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(42);
            expect(component['activeFilters']()).toEqual(['exercise']);

            // Backspace over the empty input must not remove filters (removal requires chip navigation).
            component['onBackspaceRemoveFilter']();
            component['onBackspaceRemoveFilter']();

            expect(component['activeFilters']()).toEqual(['exercise']);
            expect(component['courseIdsParam']()[0]).toBe(42);
        });

        it('opens the guided filter picker as the home screen when there is no course context', () => {
            Object.defineProperty(router, 'url', { get: () => '/dashboard', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['tokens']()).toHaveLength(0);
            expect(component['filterPickerOpen']()).toBe(true);
        });

        it('does not open the picker on a course-scoped page (shows the scoped results instead)', () => {
            Object.defineProperty(router, 'url', { get: () => '/courses/42/exercises', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['tokens']().length).toBeGreaterThan(0);
            expect(component['filterPickerOpen']()).toBe(false);
        });

        it('should remove course filter via removeCourseFilter and re-trigger search', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42/statistics', configurable: true });
            mockSearchService.globalSearch.mockReturnValue(of([]));

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(42);

            component['removeCourseFilter']();

            expect(component['courseIdsParam']()[0]).toBeUndefined();
            expect(component['chips']().some((chip) => chip.family === 'course')).toBe(false);
        });

        it('should clear context filters when modal is closed', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42/exercises', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(42);
            expect(component['activeFilters']()).toEqual(['exercise']);

            // Close modal
            mockSearchOverlayService.isOpen.set(false);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBeUndefined();
            expect(component['chips']().some((chip) => chip.family === 'course')).toBe(false);
            expect(component['activeFilters']()).toEqual([]);
        });

        it('should apply course filter when modal opens on instructor course-management page', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 6, title: 'Software Engineering' });
            Object.defineProperty(router, 'url', { get: () => '/course-management/6', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(6);
            expect(component['chips']().find((chip) => chip.family === 'course')?.label).toBe('Software Engineering');
            expect(component['activeFilters']()).toEqual([]);
        });

        it('should apply exercise filter when on instructor exercises tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 6, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/course-management/6/exercises', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(6);
            expect(component['activeFilters']()).toEqual(['exercise']);
        });

        it('should apply lecture filter when on instructor lectures tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 6, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/course-management/6/lectures', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['activeFilters']()).toEqual(['lecture', 'lecture_unit']);
        });

        it('should apply exam filter when on instructor exams tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 6, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/course-management/6/exams', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['activeFilters']()).toEqual(['exam']);
        });

        it('should apply communication filters when on instructor communication tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 6, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/course-management/6/communication', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['activeFilters']()).toEqual(['channel', 'post', 'answer_post']);
        });

        it('should apply faq filter when on instructor faqs tab', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 6, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/course-management/6/faqs', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['activeFilters']()).toEqual(['faq']);
        });

        it('should not apply filter for non-mapped instructor tabs', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 6, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => '/course-management/6/grading', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(6);
            expect(component['activeFilters']()).toEqual([]);
        });
    });
});
