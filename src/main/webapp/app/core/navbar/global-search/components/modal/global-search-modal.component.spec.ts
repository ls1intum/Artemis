import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Dialog } from 'primeng/dialog';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of, throwError } from 'rxjs';
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
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Router } from '@angular/router';
import { GlobalSearchNavigationViewComponent } from '../views/navigation-view/global-search-navigation-view.component';
import { GlobalSearchIrisAnswerComponent } from '../views/iris-answer/global-search-iris-answer.component';
import { LectureSearchService } from '../../services/lecture-search.service';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { HttpErrorResponse } from '@angular/common/http';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { User } from 'app/account/user/user.model';

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

    const noTabs = {
        lectures: false,
        exams: false,
        competencies: false,
        tutorialGroups: false,
        iris: false,
        faq: false,
        learningPaths: false,
        communication: false,
        training: false,
    };
    const mockCourseManagementService = { getCourseAvailableTabs: vi.fn(() => of(noTabs)) };

    const mockSearchService = {
        globalSearch: vi.fn(() => of<GlobalSearchResult[]>([])),
    };

    const courses = [{ id: 1, title: 'Deep Learning' } as Course, { id: 2, title: 'Computer Vision' } as Course];
    const mockCourseStorageService = {
        getCourse: vi.fn<(courseId: number) => Course | undefined>((id) => courses.find((course) => course.id === id)),
        getCourses: vi.fn<() => Course[]>(() => courses),
    };

    beforeEach(() => {
        vi.clearAllMocks();
        // clearAllMocks resets call history but not implementations, so a mockReturnValue set by one
        // test would otherwise leak into every later one and make results order-dependent.
        mockCourseStorageService.getCourse.mockImplementation((id) => courses.find((course) => course.id === id));
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
                { provide: CourseManagementService, useValue: mockCourseManagementService },
            ],
        });

        // Mock GlobalSearchIrisAnswerComponent inside the navigation view so the modal spec is isolated
        // from its rendering details.
        TestBed.overrideComponent(GlobalSearchNavigationViewComponent, {
            remove: { imports: [GlobalSearchIrisAnswerComponent] },
            add: { imports: [MockComponent(GlobalSearchIrisAnswerComponent)] },
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

        it('returns home when the last chip is removed, rather than stranding an abandoned operator', () => {
            // Composing `course:` and then removing the only chip used to leave the palette on that operator's
            // value menu: no filters, no search text, and the results pane unreachable behind the menu.
            component['applyTokens']([{ facet: 'type', value: 'course' }]);
            component['onSearchInput']('course:');
            expect((component as any).filterMenuOpen()).toBe(true);

            component['onChipRemoved'](0);

            expect(component['searchQuery']()).toBe('');
            expect((component as any).operator()).toBeUndefined();
            expect((component as any).filterPickerOpen()).toBe(true);
        });

        it('keeps a hand-typed operator open while the user is still typing it', () => {
            // The counterpart of the case above: mid-composition the raw `type:` is content, so the value menu
            // must survive. Reading both gestures the same way is what broke one or the other.
            component['onSearchInput']('type:');

            expect(component['searchQuery']()).toBe('type:');
            expect((component as any).operator()?.facet).toBe('type');
            expect((component as any).menuOptions().length).toBeGreaterThan(0);
        });

        it('keeps the search text when a chip is removed, so only a bare operator sends you home', () => {
            component['applyTokens']([{ facet: 'type', value: 'course' }]);
            component['onSearchInput']('databases course:');

            component['onChipRemoved'](0);

            // There is still something being searched for, so the palette stays where it is.
            expect(component['searchQuery']()).toBe('databases course:');
            expect((component as any).operator()?.facet).toBe('course');
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
            expect((component as any).menuHeaderKey()).toBe('global.search.backToFilters');

            component.handleKeyboardEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

            expect((component as any).filterPickerOpen()).toBe(true);
            expect((component as any).backDestination()).toBeUndefined();
            expect(searchOverlayService.close).not.toHaveBeenCalled();
        });

        it('disables the filter trigger on the filter home screen, where it and the shortcut are both dead', () => {
            expect((component as any).filterTriggerDisabled()).toBe(true);
        });

        it('stays disabled at the filter root even with results behind, because the button only ever goes to filters', () => {
            (component as any).hasSearched.set(true);

            expect((component as any).backDestination()).toBe('results');
            expect((component as any).filterTriggerDisabled()).toBe(true);
        });

        it('goes live one level deep, where the button still has a root to return to', () => {
            (component as any).filter.excludeMode.set(true);
            expect((component as any).filterTriggerDisabled()).toBe(false);

            (component as any).filter.excludeMode.set(false);
            (component as any).onSearchInput('type:');
            expect((component as any).filterTriggerDisabled()).toBe(false);
        });

        it('returns a hand-typed value list to the filter root rather than closing the palette', () => {
            // Reachable by replacing a search with an operator: the results clear, so nothing sits behind the
            // menu, and Escape used to fall through to closing the whole thing mid-composition.
            (component as any).onSearchInput('abc');
            (component as any).filterPickerOpen.set(false);
            (component as any).hasSearched.set(false);
            (component as any).onSearchInput('type:');

            component.handleKeyboardEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

            expect(searchOverlayService.close).not.toHaveBeenCalled();
            expect((component as any).filterPickerOpen()).toBe(true);
            expect((component as any).searchQuery()).toBe('');
        });

        it('never deletes an unresolvable value: Cmd+F at a dead end keeps the text and searches it', () => {
            (component as any).hasSearched.set(true);
            (component as any).onSearchInput('nsjkfncs type:candle');
            expect((component as any).deadEnd()).toBe(true);

            (component as any).toggleFilterMenu();

            expect((component as any).searchQuery()).toBe('nsjkfncs type:candle');
            expect((component as any).searchText()).toBe('nsjkfncs type:candle');
            expect((component as any).filterMenuOpen()).toBe(false);
        });

        it('never deletes an unresolvable value: the Filter button at a dead end keeps the text', () => {
            (component as any).onSearchInput('nsjkfncs type:candle');

            (component as any).openFilterPicker();

            expect((component as any).searchQuery()).toBe('nsjkfncs type:candle');
            expect((component as any).filterPickerOpen()).toBe(true);
            expect((component as any).deadEnd()).toBe(false);
        });

        it('drops a keyboard chip selection when the picker takes over', () => {
            (component as any).tokens.set([{ facet: 'type', value: 'lecture' }]);
            (component as any).selectedChip.set(0);

            (component as any).openFilterPicker();

            expect((component as any).selectedChip()).toBe(-1);
        });

        it('offers no way back on a fresh palette, because nothing sits behind the menu', () => {
            expect((component as any).backDestination()).toBeUndefined();
            expect((component as any).menuHeaderKey()).toBe('global.search.addFilter');
            expect((component as any).escapeHintKey()).toBe('global.search.toClose');
        });

        it('names the destination the back control leads to, one level deep', () => {
            (component as any).filter.excludeMode.set(true);

            expect((component as any).backDestination()).toBe('filters');
            expect((component as any).menuHeaderKey()).toBe('global.search.backToFilters');
            expect((component as any).escapeHintKey()).toBe('global.search.toFilters');
        });

        it('returns a negated value list to the exclude chooser it was opened from, not to the root', () => {
            (component as any).onSearchInput('-type:');

            expect((component as any).backDestination()).toBe('exclude');
            expect((component as any).menuHeaderKey()).toBe('global.search.backToExcludeOptions');
            expect((component as any).escapeHintKey()).toBe('global.search.toExcludeOptions');

            (component as any).onFilterBack();

            expect((component as any).filter.excludeMode()).toBe(true);
            expect((component as any).backDestination()).toBe('filters');
        });

        it('returns an include value list to the root, which is where it was opened from', () => {
            (component as any).onSearchInput('type:');

            expect((component as any).backDestination()).toBe('filters');
            expect((component as any).menuHeaderKey()).toBe('global.search.backToFilters');
        });

        it('offers the results as the destination once a search sits behind the menu', () => {
            (component as any).hasSearched.set(true);

            expect((component as any).backDestination()).toBe('results');
            expect((component as any).menuHeaderKey()).toBe('global.search.backToResults');
            expect((component as any).escapeHintKey()).toBe('global.search.toResults');
        });

        it('leaves the filter menu when the back control points at the results', () => {
            (component as any).hasSearched.set(true);

            (component as any).onFilterBack();

            expect((component as any).filterMenuOpen()).toBe(false);
            expect(searchOverlayService.close).not.toHaveBeenCalled();
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

    describe('Slides and videos filter', () => {
        const contentHit = {
            course: { id: 42, name: 'Intro to CS' },
            lecture: { id: 7, name: 'Backpropagation' },
            lectureUnit: { id: 9, name: 'Slide 12', link: '/courses/42/lectures/7', pageNumber: 12, sourceType: 'SLIDE', queryParams: { page: 12 } },
            snippet: 'the chain rule applied backwards',
        } as LectureSearchResult;
        const contentToken = { facet: 'type' as const, value: 'lecture_content' };

        let lectureSearch: LectureSearchService;

        beforeEach(() => {
            vi.useFakeTimers();
            lectureSearch = TestBed.inject(LectureSearchService);
            // Content search needs both the Iris module (stubbed in the providers) and the user's AI opt-in.
            TestBed.inject(AccountService).userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('offers the slide and video hits of a metadata search as a row', () => {
            const search = vi.spyOn(lectureSearch, 'search').mockReturnValue(of([contentHit]));

            component['onSearchInput']('backpropagation');
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalled();
            expect(search).toHaveBeenCalledWith('backpropagation', 10, undefined, undefined, true);
            expect(component['contentPreview']()).toHaveLength(1);
        });

        it('keeps the selected result selected when the row appears above it', () => {
            const preview = new Subject<LectureSearchResult[]>();
            vi.spyOn(lectureSearch, 'search').mockReturnValue(preview);
            mockSearchService.globalSearch.mockReturnValue(of([{ id: '1', type: 'exercise', title: 'Backpropagation' } as GlobalSearchResult]));

            component['onSearchInput']('backpropagation');
            vi.advanceTimersByTime(300);
            component['selectedIndex'].set(0);
            preview.next([contentHit]);

            expect(component['selectedIndex']()).toBe(1);
        });

        it('hides the hits of the previous query until the new ones arrive', () => {
            const preview = new Subject<LectureSearchResult[]>();
            vi.spyOn(lectureSearch, 'search').mockReturnValue(preview);

            component['onSearchInput']('backpropagation');
            vi.advanceTimersByTime(300);
            preview.next([contentHit]);
            expect(component['contentPreview']()).toHaveLength(1);

            component['onSearchInput']('gradient descent');

            expect(component['contentPreview']()).toHaveLength(0);
        });

        it('asks for no slide and video hits without the AI opt-in', () => {
            TestBed.inject(AccountService).userIdentity.set({} as User);
            const search = vi.spyOn(lectureSearch, 'search').mockReturnValue(of([contentHit]));

            component['onSearchInput']('backpropagation');
            vi.advanceTimersByTime(300);

            expect(search).not.toHaveBeenCalled();
            expect(component['contentPreview']()).toHaveLength(0);
        });

        it('switches to the slides-and-videos search from the row and keeps the course chips', () => {
            vi.spyOn(lectureSearch, 'search').mockReturnValue(of([contentHit]));
            component['applyTokens']([
                { facet: 'course', value: '42' },
                { facet: 'type', value: 'exercise' },
            ]);

            component['showContentHits']();

            expect(component['tokens']()).toEqual([{ facet: 'course', value: '42' }, contentToken]);
        });

        it('searches the Iris collections instead of the metadata index', () => {
            const search = vi.spyOn(lectureSearch, 'search').mockReturnValue(of([contentHit]));
            component['applyTokens']([contentToken]);

            component['onSearchInput']('backpropagation');
            vi.advanceTimersByTime(300);

            expect(search).toHaveBeenCalledWith('backpropagation', 10, undefined, undefined);
            expect(mockSearchService.globalSearch).not.toHaveBeenCalled();
            expect(component['results']()[0].type).toBe('lecture_content');
        });

        it('scopes the content search to the courses the chips select', () => {
            const search = vi.spyOn(lectureSearch, 'search').mockReturnValue(of([]));
            component['applyTokens']([contentToken, { facet: 'course', value: '42' }]);

            component['onSearchInput']('backpropagation');
            vi.advanceTimersByTime(300);

            expect(search).toHaveBeenCalledWith('backpropagation', 10, [42], undefined);
        });

        it('hides the courses an exclusion chip names, which content search would otherwise still read', () => {
            const search = vi.spyOn(lectureSearch, 'search').mockReturnValue(of([]));
            component['applyTokens']([contentToken, { facet: 'course', value: '42', negate: true }]);

            component['onSearchInput']('backpropagation');
            vi.advanceTimersByTime(300);

            expect(search).toHaveBeenCalledWith('backpropagation', 10, undefined, [42]);
        });

        it('reports a failed content search rather than falling back to the metadata search', () => {
            vi.spyOn(lectureSearch, 'search').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            component['applyTokens']([contentToken]);

            component['onSearchInput']('backpropagation');
            vi.advanceTimersByTime(300);

            expect(component['searchError']()).toBe('global.search.contentSearchUnavailable');
            expect(mockSearchService.globalSearch).not.toHaveBeenCalled();
        });

        it('waits for a search term, which content search cannot run without', () => {
            const search = vi.spyOn(lectureSearch, 'search').mockReturnValue(of([]));
            component['applyTokens']([contentToken]);

            vi.advanceTimersByTime(300);

            expect(search).not.toHaveBeenCalled();
            expect(component['contentSearchActive']()).toBe(true);
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

        it('only selects the last filter on backspace over the empty input, without removing it', () => {
            component['tokens'].set([
                { facet: 'type', value: 'exercise' },
                { facet: 'type', value: 'lecture' },
            ]);

            component['onBackspaceAtStart'](new KeyboardEvent('keydown', { key: 'Backspace' }));

            expect(component['activeFilters']()).toEqual(['exercise', 'lecture', 'lecture_unit']);
            expect(component['tokens']()).toHaveLength(2);
            expect(component['selectedChip']()).toBe(1);
        });

        it('should re-trigger search when filter changes even if query stays the same', () => {
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));

            // Type a query
            component['onSearchInput']('test');
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('test', undefined, undefined, undefined, undefined, 10);
            expect(component['results']()).toEqual(queryResults);

            // Now toggle a filter with the same query — should still re-trigger
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));
            component['applyTokens']([{ facet: 'type', value: 'exercise' }]);
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('test', 'exercise', undefined, undefined, undefined, 10);
            expect(component['results']()).toEqual(filteredResults);
        });

        it('leaves the guided picker and runs a normal search when typed text matches no filter action', () => {
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));
            component['openFilterPicker']();
            expect(component['filterPickerOpen']()).toBe(true);

            component['onSearchInput']('deep');
            vi.advanceTimersByTime(300);

            expect(component['filterPickerOpen']()).toBe(false);
            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('deep', undefined, undefined, undefined, undefined, 10);
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
            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('course', undefined, undefined, undefined, undefined, 10);
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
            expect(mockSearchService.globalSearch).toHaveBeenLastCalledWith('linear regression', 'course', undefined, undefined, undefined, 10);
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
            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('nsjkfncs', undefined, undefined, undefined, undefined, 10);

            component['onOptionSelected'](0);
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenLastCalledWith('nsjkfncs type:candle', undefined, undefined, undefined, undefined, 10);
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
            component['applyTokens']([{ facet: 'type', value: 'exercise' }]);
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
            component['applyTokens']([{ facet: 'type', value: 'exercise' }]);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);
            // Should still be only 1 call total — the re-add was served from cache
            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();
        });

        it('should not serve one type exclusion from another exclusion cached results', () => {
            // Exclusions are sent to the server, so two different ones must not share a cache entry.
            const examExcludedResults: GlobalSearchResult[] = [{ id: '9', type: 'lecture', title: 'A Lecture', badge: '', metadata: {} }];
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));

            component['applyTokens']([{ facet: 'type', value: 'exercise', negate: true }]);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();

            mockSearchService.globalSearch.mockReturnValue(of(examExcludedResults));
            component['applyTokens']([{ facet: 'type', value: 'exam', negate: true }]);
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledTimes(2);
            expect(component['results']()).toEqual(examExcludedResults);
        });

        it('should not get stuck loading when filter is removed and re-added quickly within debounce window', () => {
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));

            // Add exercise filter and let it complete
            component['applyTokens']([{ facet: 'type', value: 'exercise' }]);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);

            // Remove and immediately re-add (within 300ms debounce)
            component['onChipRemoved'](0);
            // Don't wait for debounce — immediately re-add
            component['applyTokens']([{ facet: 'type', value: 'exercise' }]);
            vi.advanceTimersByTime(300);

            // Must not be stuck loading — should show cached results
            expect(component['isLoading']()).toBe(false);
            expect(component['results']()).toEqual(filteredResults);
        });

        it('should clear placeholder cache on resetSearch so fresh results load next time', () => {
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));

            // Populate cache
            component['applyTokens']([{ facet: 'type', value: 'exercise' }]);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(filteredResults);
            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();

            // Reset (simulates closing the modal)
            component['resetSearch']();

            // Re-add filter — cache was cleared, so a new HTTP call should happen
            mockSearchService.globalSearch.mockReturnValue(of(queryResults));
            component['applyTokens']([{ facet: 'type', value: 'exercise' }]);
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual(queryResults);
            expect(mockSearchService.globalSearch).toHaveBeenCalledTimes(2);
        });

        it('should serve cached filter results synchronously without waiting for 300ms debounce', () => {
            mockSearchService.globalSearch.mockReturnValue(of(filteredResults));

            // First add: needs debounce + HTTP
            component['applyTokens']([{ facet: 'type', value: 'exercise' }]);
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
            component['applyTokens']([{ facet: 'type', value: 'exercise' }]);
            // At time 0 (no timer advancement), results should already appear from cache
            expect(component['results']()).toEqual(filteredResults);
            expect(component['isLoading']()).toBe(false);
            expect(component['hasSearched']()).toBe(true);
            // No additional HTTP call: still only the 1 from the first add
            expect(mockSearchService.globalSearch).toHaveBeenCalledOnce();
        });
    });

    describe('End-to-end journeys', () => {
        const results: GlobalSearchResult[] = [{ id: '1', type: 'lecture', title: 'Linear Regression', metadata: {} }];

        beforeEach(() => {
            vi.useFakeTimers();
            mockSearchService.globalSearch.mockReturnValue(of(results));
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        /** Chooses the first row whose action is of the given kind, failing loudly if the menu has no such row. */
        function choose(kind: 'operator' | 'value' | 'excludeStep' | 'literal' | 'clearValue', match?: string) {
            const index = component['menuOptions']().findIndex((option) => {
                if (option.action.kind !== kind) {
                    return false;
                }
                if (match === undefined) {
                    return true;
                }
                return option.action.kind === 'operator' ? option.action.prefix === match : option.action.kind === 'value' && option.action.value === match;
            });
            expect(index).toBeGreaterThanOrEqual(0);
            component['onOptionSelected'](index);
        }

        it('adds two courses and a type to a live search without ever retyping the query', () => {
            component['onSearchInput']('linear regression');
            vi.advanceTimersByTime(300);

            for (const course of ['1', '2']) {
                component['toggleFilterMenu']();
                choose('operator', 'course:');
                choose('value', course);
                vi.advanceTimersByTime(300);
            }
            component['toggleFilterMenu']();
            choose('operator', 'type:');
            choose('value', 'lecture');
            vi.advanceTimersByTime(300);

            expect(component['searchQuery']()).toBe('linear regression');
            expect(component['tokens']()).toHaveLength(3);
            expect(mockSearchService.globalSearch).toHaveBeenLastCalledWith('linear regression', 'lecture,lecture_unit', undefined, [1, 2], undefined, 10);
        });

        it('walks the exclude branch and offers the right way back at every level', () => {
            component['openFilterPicker']();
            expect(component['menuHeaderKey']()).toBe('global.search.addFilter');

            choose('excludeStep');
            expect(component['menuHeaderKey']()).toBe('global.search.backToFilters');

            choose('operator', '-type:');
            expect(component['menuHeaderKey']()).toBe('global.search.backToExcludeOptions');

            component['onFilterBack']();
            expect(component['filter'].excludeMode()).toBe(true);
            expect(component['menuHeaderKey']()).toBe('global.search.backToFilters');

            choose('operator', '-type:');
            choose('value', 'exam');
            vi.advanceTimersByTime(300);

            expect(component['tokens']()).toEqual([{ facet: 'type', value: 'exam', negate: true }]);
            expect(component['filterMenuOpen']()).toBe(false);
        });

        it('recovers from a mistyped value to the full list, keeping the query', () => {
            component['onSearchInput']('linear regression type:zzz');
            expect(component['deadEnd']()).toBe(true);

            choose('clearValue');

            expect(component['searchQuery']()).toBe('linear regression type:');
            expect(component['deadEnd']()).toBe(false);

            choose('value', 'lecture');
            vi.advanceTimersByTime(300);

            expect(component['searchQuery']()).toBe('linear regression');
            expect(component['tokens']()).toEqual([{ facet: 'type', value: 'lecture', negate: false }]);
        });

        it('treats a whitespace-only box as empty and returns to the picker', () => {
            component['onSearchInput']('abc');
            vi.advanceTimersByTime(300);

            component['onSearchInput']('   ');

            expect(component['searchText']()).toBe('');
            expect(component['filterPickerOpen']()).toBe(true);
        });

        it('re-picks a chip in place, leaving the query and the other chips alone', () => {
            component['onSearchInput']('linear regression');
            vi.advanceTimersByTime(300);
            component['tokens'].set([
                { facet: 'course', value: '1' },
                { facet: 'type', value: 'lecture' },
            ]);

            component['onChipSelected'](1);
            choose('value', 'exam');
            vi.advanceTimersByTime(300);

            expect(component['tokens']()).toEqual([
                { facet: 'course', value: '1' },
                { facet: 'type', value: 'exam', negate: false },
            ]);
            expect(component['searchQuery']()).toBe('linear regression');
        });
    });

    describe('Escape', () => {
        it('should close the overlay when Escape is pressed with nothing to step back from', () => {
            mockSearchOverlayService.isOpen.set(true);

            const event = new KeyboardEvent('keydown', { key: 'Escape' });
            component.handleKeyboardEvent(event);

            expect(searchOverlayService.close).toHaveBeenCalled();
        });
    });

    describe('Arrow Key Navigation', () => {
        beforeEach(() => {
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
            // The home screen opens the guided picker; these tests exercise results / chip navigation, so close it
            // and give the pane some results to walk, since an empty navigation view has nothing selectable.
            (component as any).filterPickerOpen.set(false);
            (component as any).hasSearched.set(true);
            (component as any).results.set([
                { id: '1', type: 'exercise', title: 'One', metadata: {} },
                { id: '2', type: 'exercise', title: 'Two', metadata: {} },
                { id: '3', type: 'exercise', title: 'Three', metadata: {} },
            ] as GlobalSearchResult[]);
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

        it('gives the focus back to the element the palette was opened from when it closes without navigating', () => {
            mockSearchOverlayService.isOpen.set(false);
            TestBed.tick();
            const trigger = document.createElement('button');
            document.body.appendChild(trigger);
            trigger.focus();
            mockSearchOverlayService.isOpen.set(true);
            TestBed.tick();
            (fixture.nativeElement.querySelector('.search-input') as HTMLInputElement | null)?.focus();

            (component as any).onDialogHide();

            expect(mockSearchOverlayService.close).toHaveBeenCalled();
            expect(document.activeElement).toBe(trigger);
            trigger.remove();
        });

        it('points the input at the selected result for screen readers', () => {
            vi.useFakeTimers();
            mockSearchService.globalSearch.mockReturnValue(
                of([{ id: '1', type: 'exercise', title: 'A' } as GlobalSearchResult, { id: '2', type: 'lecture', title: 'B' } as GlobalSearchResult]),
            );
            component['onSearchInput']('query');
            vi.advanceTimersByTime(300);
            component['selectedIndex'].set(1);
            fixture.detectChanges();

            const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
            const activeId = input.getAttribute('aria-activedescendant')!;
            const option = fixture.nativeElement.querySelector(`#${activeId}`) as HTMLElement;
            expect(input.getAttribute('aria-expanded')).toBe('true');
            expect(option.getAttribute('role')).toBe('option');
            expect(option.textContent).toContain('B');
            expect(option.closest('[role="listbox"]')?.id).toBe(input.getAttribute('aria-controls'));
            vi.useRealTimers();
        });

        it('points the input at no result while skeletons replace the rows', () => {
            (component as any).results.set([{ id: '1', type: 'exercise', title: 'A' }]);
            (component as any).isLoading.set(true);
            (component as any).selectedIndex.set(0);
            fixture.detectChanges();

            const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
            expect(input.getAttribute('aria-activedescendant')).toBeNull();
            expect(fixture.nativeElement.querySelector('[role="listbox"]#global-search-results')).toBeNull();
        });

        it('puts letters typed before the input has focus into the search box', () => {
            vi.useFakeTimers();
            (document.activeElement as HTMLElement | null)?.blur();

            component.handleKeyboardEvent(new KeyboardEvent('keydown', { key: 'g', cancelable: true }));
            component.handleKeyboardEvent(new KeyboardEvent('keydown', { key: 'i', cancelable: true }));
            component.handleKeyboardEvent(new KeyboardEvent('keydown', { key: 't', cancelable: true }));

            expect((component as any).searchQuery()).toBe('git');
            vi.useRealTimers();
        });

        it('leaves letters alone once the input has focus', () => {
            const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
            input.focus();
            const event = new KeyboardEvent('keydown', { key: 'g', cancelable: true });

            component.handleKeyboardEvent(event);

            expect(event.defaultPrevented).toBe(false);
            expect((component as any).searchQuery()).toBe('');
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
            (component as any).onBackspaceAtStart(new KeyboardEvent('keydown', { key: 'Backspace', cancelable: true }));

            component.handleKeyboardEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

            expect((component as any).editingChip()).toBe(1);
            expect((component as any).selectedChip()).toBe(-1);
            expect((component as any).searchQuery()).toBe('course:');
        });

        describe('Backspace at the start of the input', () => {
            const searchInput = () => fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
            const pressBackspace = (repeat = false) => {
                const event = new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true, cancelable: true, repeat });
                searchInput().dispatchEvent(event);
                return event;
            };

            beforeEach(() => {
                (component as any).tokens.set([
                    { facet: 'type', value: 'exercise' },
                    { facet: 'course', value: '5' },
                ]);
                fixture.detectChanges();
                // The user types into the focused input, so the keys come from where the chip gets highlighted.
                searchInput().focus();
            });

            it('selects the last chip on the first press and removes it on the second', () => {
                (component as any).selectedIndex.set(1);

                pressBackspace();

                expect((component as any).tokens()).toHaveLength(2);
                expect((component as any).selectedChip()).toBe(1);
                expect((component as any).selectedIndex()).toBe(-1);

                pressBackspace();

                expect((component as any).tokens()).toEqual([{ facet: 'type', value: 'exercise' }]);
                expect((component as any).selectedChip()).toBe(0);
            });

            it('selects and then removes every chip while the key is held down, as in Gmail', () => {
                pressBackspace();
                expect((component as any).selectedChip()).toBe(1);
                expect((component as any).tokens()).toHaveLength(2);

                pressBackspace(true);
                expect((component as any).tokens()).toEqual([{ facet: 'type', value: 'exercise' }]);
                expect((component as any).selectedChip()).toBe(0);

                pressBackspace(true);
                expect((component as any).tokens()).toEqual([]);
                expect((component as any).selectedChip()).toBe(-1);
            });

            it('deletes selected text instead of the selected chip', () => {
                const input = searchInput();
                input.value = 'query';
                input.setSelectionRange(0, 0);
                pressBackspace();
                expect((component as any).selectedChip()).toBe(1);

                input.setSelectionRange(0, input.value.length);
                const event = pressBackspace();

                expect(event.defaultPrevented).toBe(false);
                expect((component as any).tokens()).toHaveLength(2);
                expect((component as any).selectedChip()).toBe(-1);
            });

            it('edits the text instead of removing the chip once the cursor moved', () => {
                const input = searchInput();
                input.value = 'query';
                input.setSelectionRange(0, 0);
                pressBackspace();
                expect((component as any).selectedChip()).toBe(1);

                input.setSelectionRange(3, 3);
                const event = pressBackspace();

                expect(event.defaultPrevented).toBe(false);
                expect((component as any).tokens()).toHaveLength(2);
                expect((component as any).selectedChip()).toBe(-1);
            });

            it('leaves the chip alone when the key comes from another element in the modal', () => {
                pressBackspace();
                expect((component as any).selectedChip()).toBe(1);
                const elsewhere = document.createElement('button');
                fixture.nativeElement.appendChild(elsewhere);
                elsewhere.focus();

                const event = new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true, cancelable: true });
                elsewhere.dispatchEvent(event);

                expect(event.defaultPrevented).toBe(false);
                expect((component as any).tokens()).toHaveLength(2);
                expect((component as any).selectedChip()).toBe(-1);
                elsewhere.remove();
            });

            it('lets ArrowLeft move the cursor once it moved away from the highlighted chip', () => {
                const input = searchInput();
                input.value = 'query';
                input.setSelectionRange(0, 0);
                pressBackspace();
                input.setSelectionRange(5, 5);

                const event = new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true, cancelable: true });
                input.dispatchEvent(event);

                expect(event.defaultPrevented).toBe(false);
                expect((component as any).selectedChip()).toBe(-1);
            });

            it('leaves a modified arrow to the text, so word and line jumps keep working', () => {
                const input = searchInput();
                input.value = 'query';
                input.setSelectionRange(0, 0);
                pressBackspace();

                const event = new KeyboardEvent('keydown', { key: 'ArrowRight', metaKey: true, bubbles: true, cancelable: true });
                input.dispatchEvent(event);

                expect(event.defaultPrevented).toBe(false);
                expect((component as any).selectedChip()).toBe(-1);
            });

            it('ends the chip highlight on a click anywhere', () => {
                pressBackspace();
                expect((component as any).selectedChip()).toBe(1);

                document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));

                expect((component as any).selectedChip()).toBe(-1);
            });

            it('lets Enter activate the Filter button after Tab moved focus away from the highlighted chip', () => {
                pressBackspace();
                expect((component as any).selectedChip()).toBe(1);
                const filterButton = fixture.nativeElement.querySelector('.filter-trigger') as HTMLButtonElement;
                // Tab moves focus; jsdom does not move it for a dispatched Tab key, so focus the next control directly.
                filterButton.focus();

                const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
                filterButton.dispatchEvent(event);

                expect(event.defaultPrevented).toBe(false);
                expect((component as any).editingChip()).toBe(-1);
                expect((component as any).searchQuery()).toBe('');
            });

            it('goes from the input straight to the results on ArrowDown, past the chips', () => {
                (component as any).results.set([{ id: '1', type: 'exercise', title: 'A' }]);
                fixture.detectChanges();

                searchInput().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));

                expect((component as any).selectedChip()).toBe(-1);
                expect((component as any).selectedIndex()).toBe(0);
            });

            it('still removes a chip highlighted with Backspace on Delete', () => {
                pressBackspace();
                expect((component as any).selectedChip()).toBe(1);

                searchInput().dispatchEvent(new KeyboardEvent('keydown', { key: 'Delete', bubbles: true, cancelable: true }));

                expect((component as any).tokens()).toEqual([{ facet: 'type', value: 'exercise' }]);
            });
        });
    });

    describe('Show more results', () => {
        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('offers more results when the list was cut at the limit and loads the longer list', () => {
            const fullPage = Array.from({ length: 10 }, (_, index) => ({ id: String(index), type: 'exercise', title: `Exercise ${index}` }) as GlobalSearchResult);
            mockSearchService.globalSearch.mockReturnValue(of(fullPage));

            component['onSearchInput']('sorting');
            vi.advanceTimersByTime(300);

            expect(component['canShowMore']()).toBe(true);

            component['showMoreResults']();
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenLastCalledWith('sorting', undefined, undefined, undefined, undefined, 25);
            expect(component['canShowMore']()).toBe(false);
        });

        it('does not offer more results when the list was shorter than the limit', () => {
            mockSearchService.globalSearch.mockReturnValue(of([{ id: '1', type: 'exercise', title: 'Sorting' } as GlobalSearchResult]));

            component['onSearchInput']('sorting');
            vi.advanceTimersByTime(300);

            expect(component['canShowMore']()).toBe(false);
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

        it.each(['exercises', 'lectures', 'communication', 'exams', 'faq'])('should apply only the course filter when modal opens on the %s tab', (tab) => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 10, title: 'Algorithms' });
            Object.defineProperty(router, 'url', { get: () => `/courses/10/${tab}`, configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(10);
            expect(component['chips']().find((chip) => chip.family === 'course')?.label).toBe('Algorithms');
            expect(component['activeFilters']()).toEqual([]);
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

            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('test', undefined, undefined, [42], undefined, 10);
        });

        it('offers a matching page of the one course in scope before the results', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42', configurable: true });
            mockSearchService.globalSearch.mockReturnValue(of([{ id: '1', type: 'exercise', title: 'Grade calculator' } as GlobalSearchResult]));
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            component['onSearchInput']('grades');
            vi.advanceTimersByTime(300);

            expect(component['results']().map((result) => result.type)).toEqual(['course_page', 'exercise']);
            expect(component['results']()[0].metadata?.['link']).toBe('/courses/42/statistics');
        });

        it('offers a guarded course page only when the course has that tab', () => {
            mockCourseManagementService.getCourseAvailableTabs.mockReturnValue(of({ ...noTabs, lectures: true }));
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42', configurable: true });
            mockSearchService.globalSearch.mockReturnValue(of([]));
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            component['onSearchInput']('vorlesungen');
            vi.advanceTimersByTime(300);
            expect(component['results']().map((result) => result.metadata?.['link'])).toEqual(['/courses/42/lectures']);

            component['onSearchInput']('faq');
            vi.advanceTimersByTime(300);
            expect(component['results']()).toEqual([]);
        });

        it('adds the guarded pages when the course tabs arrive after the search results', () => {
            const tabs = new Subject<typeof noTabs>();
            mockCourseManagementService.getCourseAvailableTabs.mockReturnValue(tabs);
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42', configurable: true });
            mockSearchService.globalSearch.mockReturnValue(
                of([{ id: '1', type: 'exercise', title: 'Vorlesungen planen' } as GlobalSearchResult, { id: '1', type: 'lecture', title: 'Vorlesung 1' } as GlobalSearchResult]),
            );
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            component['onSearchInput']('vorlesungen');
            vi.advanceTimersByTime(300);
            expect(component['results']().map((result) => result.type)).toEqual(['exercise', 'lecture']);
            // The lecture shares its ID with the exercise before it; the selection must stay on the lecture.
            component['selectedIndex'].set(1);

            tabs.next({ ...noTabs, lectures: true });

            expect(component['results']().map((result) => result.type)).toEqual(['course_page', 'exercise', 'lecture']);
            expect(component['selectedIndex']()).toBe(2);
        });

        it('reads the course tabs again when the palette is opened again', () => {
            mockCourseManagementService.getCourseAvailableTabs.mockClear();
            mockCourseManagementService.getCourseAvailableTabs.mockReturnValue(of(noTabs));
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42', configurable: true });
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            mockSearchOverlayService.isOpen.set(false);
            fixture.detectChanges();
            mockCourseManagementService.getCourseAvailableTabs.mockReturnValue(of({ ...noTabs, exams: true }));
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();
            mockSearchService.globalSearch.mockReturnValue(of([]));
            component['onSearchInput']('klausuren');
            vi.advanceTimersByTime(300);

            expect(mockCourseManagementService.getCourseAvailableTabs).toHaveBeenCalledTimes(2);
            expect(component['results']().map((result) => result.metadata?.['link'])).toEqual(['/courses/42/exams']);
        });

        it('offers no course page without a single course in scope', () => {
            mockSearchService.globalSearch.mockReturnValue(of([]));
            Object.defineProperty(router, 'url', { get: () => '/dashboard', configurable: true });
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            component['onSearchInput']('grades');
            vi.advanceTimersByTime(300);

            expect(component['results']()).toEqual([]);
        });

        it('should pass excludeCourseIds to globalSearch for a negated course token', () => {
            mockSearchService.globalSearch.mockReturnValue(of([]));
            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            component['tokens'].set([{ facet: 'course', value: '7', negate: true }]);
            component['onSearchInput']('test');
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('test', undefined, undefined, undefined, [7], 10);
        });

        it('sends a type exclusion under excludeTypes rather than as a complement', () => {
            // A complement computed here would reach the server as "exam, lecture, ... " with exercise merely
            // absent, which is indistinguishable from asking only for exams. The server expands exams to their
            // exercises in that case, so excluding Exercises would have returned exercises.
            component['applyTokens']([{ facet: 'type', value: 'exercise', negate: true }]);
            component['onSearchInput']('test');
            vi.advanceTimersByTime(300);

            expect(mockSearchService.globalSearch).toHaveBeenCalledWith('test', undefined, 'exercise', undefined, undefined, 10);
        });

        it('applies context filters on open and leaves them intact on backspace over the empty input', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42/exercises', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(42);

            // Backspace over the empty input only selects a filter; removing it takes a second press.
            component['onBackspaceAtStart'](new KeyboardEvent('keydown', { key: 'Backspace' }));

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

        it('should clear context filters when modal is closed', () => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 42, title: 'Intro to CS' });
            Object.defineProperty(router, 'url', { get: () => '/courses/42/exercises', configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(42);

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

        it.each(['exercises', 'lectures', 'exams', 'communication', 'faqs'])('should apply only the course filter on the instructor %s tab', (tab) => {
            mockCourseStorageService.getCourse.mockReturnValue({ id: 6, title: 'SE' });
            Object.defineProperty(router, 'url', { get: () => `/course-management/6/${tab}`, configurable: true });

            mockSearchOverlayService.isOpen.set(true);
            fixture.detectChanges();

            expect(component['courseIdsParam']()[0]).toBe(6);
            expect(component['activeFilters']()).toEqual([]);
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
