import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { type Mock, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Observable, Subject, of } from 'rxjs';
import { GlobalSearchFilterService } from './global-search-filter.service';
import { IrisSearchAvailabilityService } from './iris-search-availability.service';
import { SearchCourseOptionsService } from './search-course-options.service';
import { MenuCourse } from '../models/search-menu.util';
import { FilterToken } from '../models/search-token.model';

describe('GlobalSearchFilterService', () => {
    let service: GlobalSearchFilterService;
    let applyTokens: Mock<(tokens: FilterToken[]) => void>;
    let requestFocus: Mock<() => void>;
    let exitFilterMenu: Mock<() => void>;
    let refreshSearch: Mock<() => void>;

    const mockCourseStorageService = {
        getCourse: vi.fn<(id: number) => Course | undefined>().mockReturnValue(undefined),
        getCourses: vi.fn<() => Course[]>().mockReturnValue([]),
    };

    // The store only reads whether content search is available, so the spec stubs the flag instead of the
    // profile and account services behind it.
    const contentSearchAvailable = signal(true);
    const mockAvailabilityService = { contentSearchAvailable: contentSearchAvailable.asReadonly() };

    const courseGeneration = signal(0);
    const mockCourseOptionsService = {
        getCourses: vi.fn<() => Observable<MenuCourse[]>>().mockReturnValue(of([])),
        generation: courseGeneration.asReadonly(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
        mockCourseStorageService.getCourses.mockReturnValue([]);
        mockCourseOptionsService.getCourses.mockReturnValue(of([]));
        courseGeneration.set(0);
        contentSearchAvailable.set(true);
        TestBed.configureTestingModule({
            providers: [
                GlobalSearchFilterService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: CourseStorageService, useValue: mockCourseStorageService },
                { provide: SearchCourseOptionsService, useValue: mockCourseOptionsService },
                { provide: IrisSearchAvailabilityService, useValue: mockAvailabilityService },
            ],
        });
        service = TestBed.inject(GlobalSearchFilterService);
        applyTokens = vi.fn<(tokens: FilterToken[]) => void>();
        requestFocus = vi.fn<() => void>();
        exitFilterMenu = vi.fn<() => void>();
        refreshSearch = vi.fn<() => void>();
        service.configure({ applyTokens, requestFocus, exitFilterMenu, refreshSearch });
    });

    describe('course value menu', () => {
        it('offers the courses the server knows, not just the ones the current page loaded', () => {
            // The whole bug: off the student dashboard the course store holds at most the course the page opened,
            // so the menu showed a subset of the user's courses or nothing at all.
            mockCourseStorageService.getCourses.mockReturnValue([]);
            mockCourseOptionsService.getCourses.mockReturnValue(of([{ id: 7, title: 'Databases' }]));

            service.searchQuery.set('course:');
            TestBed.tick();

            expect(mockCourseOptionsService.getCourses).toHaveBeenCalled();
            expect(service.menuOptions().map((option) => option.label)).toEqual(['Databases']);
        });

        it('names a chip from the server list, not only from the courses this page loaded', () => {
            // The menu offers courses from the fetched list, so picking one by name there and then seeing
            // the chip render the numeric fallback was a visible contradiction.
            mockCourseStorageService.getCourse.mockReturnValue(undefined);
            mockCourseStorageService.getCourses.mockReturnValue([]);
            mockCourseOptionsService.getCourses.mockReturnValue(of([{ id: 16, title: 'Databases' }]));

            service.searchQuery.set('course:');
            TestBed.tick();
            service.tokens.set([{ facet: 'course', value: '16' }]);
            TestBed.tick();

            expect(service.chips().map((chip) => chip.label)).toEqual(['Databases']);
        });

        it('accepts the exact title of a course only the server list knows', () => {
            // The menu offers it, so typing its title verbatim must not be treated as an unknown value.
            mockCourseStorageService.getCourses.mockReturnValue([]);
            mockCourseOptionsService.getCourses.mockReturnValue(of([{ id: 16, title: 'Databases' }]));

            service.searchQuery.set('course:');
            TestBed.tick();
            service.searchQuery.set('course:Databases');
            TestBed.tick();

            expect(service.operatorValueValid()).toBe(true);
        });

        it('does not read the course list until a course menu is opened', () => {
            service.searchQuery.set('type:');
            TestBed.tick();

            expect(mockCourseOptionsService.getCourses).not.toHaveBeenCalled();
        });

        it('reads the course list once, however often the menu is reopened', () => {
            mockCourseOptionsService.getCourses.mockReturnValue(of([{ id: 7, title: 'Databases' }]));

            service.searchQuery.set('course:');
            TestBed.tick();
            service.searchQuery.set('');
            TestBed.tick();
            service.searchQuery.set('course:');
            TestBed.tick();

            expect(mockCourseOptionsService.getCourses).toHaveBeenCalledOnce();
        });

        it('lists the courses by title, so the capped menu is not an arbitrary slice', () => {
            mockCourseOptionsService.getCourses.mockReturnValue(
                of([
                    { id: 3, title: 'Zoology' },
                    { id: 1, title: 'Algorithms' },
                    { id: 2, title: 'Machine Learning' },
                ]),
            );

            service.searchQuery.set('course:');
            TestBed.tick();

            expect(service.menuOptions().map((option) => option.label)).toEqual(['Algorithms', 'Machine Learning', 'Zoology']);
        });

        it('falls back to the loaded courses while the read is still in flight', () => {
            // An empty response must not make the menu emptier than it was before the request was made.
            mockCourseStorageService.getCourses.mockReturnValue([{ id: 4, title: 'Stored Course' } as Course]);
            mockCourseOptionsService.getCourses.mockReturnValue(of([]));
            courseGeneration.set(0);

            service.searchQuery.set('course:');
            TestBed.tick();

            expect(service.menuOptions().map((option) => option.label)).toEqual(['Stored Course']);
        });
    });

    describe('a change of signed-in user', () => {
        it("drops the previous user's courses and reads the list again", () => {
            mockCourseOptionsService.getCourses.mockReturnValue(of([{ id: 7, title: "A's course" }]));
            service.searchQuery.set('course:');
            TestBed.tick();
            expect(service.menuOptions().map((option) => option.label)).toEqual(["A's course"]);

            mockCourseOptionsService.getCourses.mockReturnValue(of([{ id: 9, title: "B's course" }]));
            courseGeneration.set(1);
            service.searchQuery.set('');
            TestBed.tick();
            service.searchQuery.set('course:');
            TestBed.tick();

            expect(service.menuOptions().map((option) => option.label)).toEqual(["B's course"]);
        });

        it('ignores a response that arrives after the user changed', () => {
            // The request was made for the previous user; letting it land would undo the reset and put their
            // courses back in front of whoever signed in next.
            const late = new Subject<MenuCourse[]>();
            mockCourseOptionsService.getCourses.mockReturnValue(late.asObservable());
            service.searchQuery.set('course:');
            TestBed.tick();

            // The user changes, so the service drops its cache and the next read is a fresh request.
            const fresh = new Subject<MenuCourse[]>();
            mockCourseOptionsService.getCourses.mockReturnValue(fresh.asObservable());
            courseGeneration.set(1);
            TestBed.tick();

            // Only now does the previous user's request answer.
            late.next([{ id: 7, title: "A's course" }]);
            TestBed.tick();

            expect(service.menuOptions()).toHaveLength(0);
        });
    });

    describe('empty menu reasons', () => {
        it('says the user has no courses rather than showing a bare no-matches row', () => {
            service.searchQuery.set('course:');
            TestBed.tick();

            expect(service.menuOptions()).toHaveLength(0);
            expect(service.emptyMenuReasonKey()).toBe('global.search.noCoursesToFilter');
        });

        it('distinguishes "already filtered" from "you have none"', () => {
            // The default state on a course page: the one course the store holds is the one already filtered to,
            // so it is hidden as applied and the menu empties for a completely different reason.
            mockCourseOptionsService.getCourses.mockReturnValue(of([{ id: 7, title: 'Databases' }]));
            service.tokens.set([{ facet: 'course', value: '7' }]);
            service.searchQuery.set('course:');
            TestBed.tick();

            expect(service.menuOptions()).toHaveLength(0);
            expect(service.emptyMenuReasonKey()).toBe('global.search.allCoursesFiltered');
        });

        it('explains the refusal to exclude the last remaining type', () => {
            service.tokens.set([
                { facet: 'type', value: 'course', negate: true },
                { facet: 'type', value: 'exercise', negate: true },
                { facet: 'type', value: 'lecture', negate: true },
                { facet: 'type', value: 'communication', negate: true },
                { facet: 'type', value: 'faq', negate: true },
            ]);
            service.searchQuery.set('-type:');
            TestBed.tick();

            expect(service.menuOptions()).toHaveLength(0);
            expect(service.emptyMenuReasonKey()).toBe('global.search.cannotExcludeEveryType');
        });

        it('has no reason to give while the menu still has options', () => {
            service.searchQuery.set('type:');
            TestBed.tick();

            expect(service.menuOptions().length).toBeGreaterThan(0);
            expect(service.emptyMenuReasonKey()).toBeUndefined();
        });
    });

    describe('derived query params', () => {
        it('derives type, course, and exclude-course params from tokens', () => {
            service.tokens.set([
                { facet: 'type', value: 'exercise' },
                { facet: 'course', value: '10' },
                { facet: 'course', value: '20', negate: true },
            ]);

            expect(service.typesParam()).toBe('exercise');
            expect(service.courseIdsParam()).toEqual([10]);
            expect(service.excludeCourseIdsParam()).toEqual([20]);
        });

        it('exposes the included type facets as activeFilters server types', () => {
            service.tokens.set([{ facet: 'type', value: 'exercise' }]);
            expect(service.activeFilters()).toEqual(['exercise']);
        });
    });

    describe('operator + menu state', () => {
        it('parses the typed facet operator and opens the value menu', () => {
            service.searchQuery.set('type:');
            expect(service.operator()?.facet).toBe('type');
            expect(service.filterMenuOpen()).toBe(true);
        });

        it('reads a negated operator as an exclusion', () => {
            service.searchQuery.set('-type:');
            expect(service.operator()?.negate).toBe(true);
            service.searchQuery.set('-course:');
            expect(service.operator()).toEqual(expect.objectContaining({ facet: 'course', negate: true }));
        });

        it('opens the menu for the guided picker even with no operator', () => {
            service.filterPickerOpen.set(true);
            expect(service.operator()).toBeUndefined();
            expect(service.filterMenuOpen()).toBe(true);
        });

        it('reads the operator as the trailing token and keeps the text in front of it as the search term', () => {
            service.searchQuery.set('linear regression type:lec');

            expect(service.operator()?.facet).toBe('type');
            expect(service.operator()?.query).toBe('lec');
            expect(service.searchText()).toBe('linear regression');
        });

        it('searches the whole input when no operator is present', () => {
            service.searchQuery.set('linear regression');
            expect(service.operator()).toBeUndefined();
            expect(service.searchText()).toBe('linear regression');
        });
    });

    describe('onOptionSelected', () => {
        it('injects the operator prefix and keeps the picker open (for back navigation) on a picker action', () => {
            service.filterPickerOpen.set(true);
            const index = service.menuOptions().findIndex((option) => option.action.kind === 'operator');
            expect(index).toBeGreaterThanOrEqual(0);

            service.onOptionSelected(index);

            expect(service.searchQuery()).toBe('type:');
            expect(service.filterPickerOpen()).toBe(true);
            expect(service.canGoBack()).toBe(true);
            expect(requestFocus).toHaveBeenCalled();
            expect(applyTokens).not.toHaveBeenCalled();
        });

        it('steps into the exclude level without touching the input or adding a token', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('linear regression');
            const index = service.menuOptions().findIndex((option) => option.action.kind === 'excludeStep');
            expect(index).toBeGreaterThanOrEqual(0);

            service.onOptionSelected(index);

            expect(service.searchQuery()).toBe('linear regression');
            expect(service.filterPickerOpen()).toBe(true);
            expect(service.excludeMode()).toBe(true);
            expect(service.menuOptions().map((option) => option.id)).toEqual(['-type', '-course']);
            expect(applyTokens).not.toHaveBeenCalled();
            expect(requestFocus).toHaveBeenCalled();
        });

        it('appends the operator after the search text instead of replacing it', () => {
            service.searchQuery.set('linear regression');
            service.filterPickerOpen.set(true);
            const index = service.menuOptions().findIndex((option) => option.action.kind === 'operator');

            service.onOptionSelected(index);

            expect(service.searchQuery()).toBe('linear regression type:');
            expect(service.searchText()).toBe('linear regression');
        });

        it('strips only the operator when a value is chosen, leaving the search text as typed', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('linear regression type:');
            const index = service.menuOptions().findIndex((option) => option.action.kind === 'value');
            const chosen = service.menuOptions()[index];
            const value = chosen.action.kind === 'value' ? chosen.action.value : '';

            service.onOptionSelected(index);

            expect(applyTokens).toHaveBeenCalledWith([{ facet: 'type', value, negate: false }]);
            expect(service.searchQuery()).toBe('linear regression');
            expect(service.filterPickerOpen()).toBe(false);
        });

        it('adds a value token, clears the query, closes the picker, and refocuses', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('type:');
            const index = service.menuOptions().findIndex((option) => option.action.kind === 'value');
            const chosen = service.menuOptions()[index];
            const value = chosen.action.kind === 'value' ? chosen.action.value : '';

            service.onOptionSelected(index);

            expect(applyTokens).toHaveBeenCalledWith([{ facet: 'type', value, negate: false }]);
            expect(service.searchQuery()).toBe('');
            expect(service.filterPickerOpen()).toBe(false);
            expect(requestFocus).toHaveBeenCalled();
        });

        it('replaces a chip in place when editing it', () => {
            service.tokens.set([{ facet: 'type', value: 'lecture' }]);
            service.editingChip.set(0);
            service.searchQuery.set('type:');
            const index = service.menuOptions().findIndex((option) => option.action.kind === 'value');
            const chosen = service.menuOptions()[index];
            const value = chosen.action.kind === 'value' ? chosen.action.value : '';

            service.onOptionSelected(index);

            expect(applyTokens).toHaveBeenCalledWith([{ facet: 'type', value, negate: false }]);
            expect(service.editingChip()).toBe(-1);
        });
    });

    describe('back', () => {
        it('returns to the root picker from an include value menu', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('type:');
            expect(service.canGoBack()).toBe(true);

            service.back();

            expect(service.searchQuery()).toBe('');
            expect(service.menuOptions().map((option) => option.id)).toEqual(['type', 'course', 'exclude']);
        });

        it('returns to the exclude level from an exclude value menu', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('-type:');

            service.back();

            expect(service.searchQuery()).toBe('');
            expect(service.excludeMode()).toBe(true);
            expect(service.menuOptions().map((option) => option.id)).toEqual(['-type', '-course']);
        });

        it('keeps the search text when stepping back out of a value menu', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('linear regression type:lec');

            service.back();

            expect(service.searchQuery()).toBe('linear regression');
        });

        it('is unavailable at the root picker', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('');
            expect(service.canGoBack()).toBe(false);
        });
    });

    describe('chip mutations', () => {
        it('removes a chip by index and clears the keyboard selection', () => {
            const tokens: FilterToken[] = [
                { facet: 'type', value: 'exercise' },
                { facet: 'course', value: '10' },
            ];
            service.tokens.set(tokens);
            service.selectedChip.set(1);

            service.onChipRemoved(0);

            expect(applyTokens).toHaveBeenCalledWith([{ facet: 'course', value: '10' }]);
            expect(service.selectedChip()).toBe(-1);
        });

        it('does not remove a filter on backspace over the empty input (removal needs chip navigation)', () => {
            service.tokens.set([
                { facet: 'type', value: 'exercise' },
                { facet: 'type', value: 'lecture' },
            ]);

            service.onBackspaceRemoveFilter();

            expect(applyTokens).not.toHaveBeenCalled();
            expect(service.tokens()).toHaveLength(2);
        });

        it('starts re-picking a chip: opens its facet operator and marks it as edited', () => {
            service.tokens.set([{ facet: 'course', value: '10', negate: true }]);

            service.onChipSelected(0);

            expect(service.editingChip()).toBe(0);
            expect(service.searchQuery()).toBe('-course:');
            expect(requestFocus).toHaveBeenCalled();
        });

        it('re-picks a chip without disturbing the search text', () => {
            service.tokens.set([{ facet: 'type', value: 'lecture' }]);
            service.searchQuery.set('linear regression');

            service.onChipSelected(0);

            expect(service.searchQuery()).toBe('linear regression type:');
            expect(service.searchText()).toBe('linear regression');
        });
    });

    describe('guided picker', () => {
        it('opens the picker and requests focus', () => {
            service.openFilterPicker();
            expect(service.filterPickerOpen()).toBe(true);
            expect(requestFocus).toHaveBeenCalled();
        });

        it('steps back to the root picker when requested from an open value menu', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('type:');

            service.openFilterPicker();

            expect(service.operator()).toBeUndefined();
            expect(service.filterPickerOpen()).toBe(true);
            expect(service.menuOptions().map((option) => option.id)).toEqual(['type', 'course', 'exclude']);
        });

        it('stays open when requested again at the root: it is the home screen, not a toggle', () => {
            service.filterPickerOpen.set(true);

            service.openFilterPicker();

            expect(service.filterPickerOpen()).toBe(true);
            expect(requestFocus).toHaveBeenCalled();
        });

        it('cancels a chip re-pick when the picker is requested', () => {
            service.editingChip.set(2);

            service.openFilterPicker();

            expect(service.editingChip()).toBe(-1);
        });
    });

    describe('handleMenuKey', () => {
        function keydown(key: string): KeyboardEvent {
            const event = new KeyboardEvent('keydown', { key });
            vi.spyOn(event, 'preventDefault');
            return event;
        }

        it('moves the active index down and up within bounds', () => {
            service.searchQuery.set('type:');
            const count = service.menuOptions().length;
            expect(count).toBeGreaterThan(1);

            service.handleMenuKey(keydown('ArrowDown'));
            expect(service.menuActiveIndex()).toBe(1);

            service.handleMenuKey(keydown('ArrowUp'));
            expect(service.menuActiveIndex()).toBe(0);
        });

        it('selects the active option on Enter', () => {
            service.searchQuery.set('type:');
            service.menuActiveIndex.set(service.menuOptions().findIndex((option) => option.action.kind === 'value'));

            service.handleMenuKey(keydown('Enter'));

            expect(applyTokens).toHaveBeenCalled();
        });

        it('steps a directly typed operator back to the filter root, not out of the menu', () => {
            // The root picker is the home screen, so it is always a valid parent, even for an operator the
            // user typed without ever opening the picker.
            service.searchQuery.set('type:');
            service.editingChip.set(2);

            service.handleMenuKey(keydown('Escape'));

            expect(service.searchQuery()).toBe('');
            expect(service.editingChip()).toBe(-1);
            expect(service.filterPickerOpen()).toBe(true);
            expect(exitFilterMenu).not.toHaveBeenCalled();
        });

        it('hands the exit to the host on Escape at the root picker rather than dropping the menu itself', () => {
            service.filterPickerOpen.set(true);

            service.handleMenuKey(keydown('Escape'));

            expect(service.filterPickerOpen()).toBe(false);
            expect(exitFilterMenu).toHaveBeenCalled();
        });

        it('steps back to the root picker (not out of the menu) on Escape from the exclude level', () => {
            service.filterPickerOpen.set(true);
            service.excludeMode.set(true);

            service.handleMenuKey(keydown('Escape'));

            expect(service.excludeMode()).toBe(false);
            expect(service.filterPickerOpen()).toBe(true);
            expect(service.menuOptions().map((option) => option.id)).toEqual(['type', 'course', 'exclude']);
            expect(exitFilterMenu).not.toHaveBeenCalled();
        });

        it('keeps the search text when Escape steps back out of a value menu', () => {
            service.searchQuery.set('linear regression type:');

            service.handleMenuKey(keydown('Escape'));

            expect(service.searchQuery()).toBe('linear regression');
            expect(service.filterPickerOpen()).toBe(true);
        });

        it('leaves the filter surface only once there is no level left to step back to', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('linear regression');

            service.handleMenuKey(keydown('Escape'));

            expect(service.searchQuery()).toBe('linear regression');
            expect(service.filterPickerOpen()).toBe(false);
            expect(exitFilterMenu).toHaveBeenCalled();
        });
    });

    describe('dead end (a typed value that is not a filter)', () => {
        function keydown(key: string): KeyboardEvent {
            const event = new KeyboardEvent('keydown', { key });
            vi.spyOn(event, 'preventDefault');
            return event;
        }

        it('collapses to the literal row and names what the user typed', () => {
            service.searchQuery.set('nsjkfncs type:candle');

            expect(service.deadEnd()).toBe(true);
            expect(service.deadEndMessage()).toEqual({ key: 'global.search.notAType', value: 'candle' });
            expect(service.canGoBack()).toBe(false);
        });

        it('says a course is not one of yours rather than claiming it does not exist', () => {
            service.searchQuery.set('course:candle');

            expect(service.deadEndMessage()).toEqual({ key: 'global.search.notYourCourse', value: 'candle' });
        });

        it('drops only the unmatched value when the recovery row is chosen, bringing the full list back', () => {
            service.searchQuery.set('deep learning type:sdvdsc');
            const index = service.menuOptions().findIndex((option) => option.action.kind === 'clearValue');

            service.onOptionSelected(index);

            expect(service.searchQuery()).toBe('deep learning type:');
            expect(service.deadEnd()).toBe(false);
            expect(service.menuOptions()).toHaveLength(7);
            expect(service.searchText()).toBe('deep learning');
        });

        it('searches the raw text verbatim when the literal row is chosen', () => {
            service.searchQuery.set('nsjkfncs type:candle');

            service.onOptionSelected(0);

            expect(service.searchQuery()).toBe('nsjkfncs type:candle');
            expect(service.searchText()).toBe('nsjkfncs type:candle');
            expect(service.filterMenuOpen()).toBe(false);
            expect(refreshSearch).toHaveBeenCalled();
        });

        it('does not eat the typed text when Escape is pressed at a dead end', () => {
            service.searchQuery.set('nsjkfncs type:candle');

            service.handleMenuKey(keydown('Escape'));

            expect(service.searchQuery()).toBe('nsjkfncs type:candle');
            expect(service.filterMenuOpen()).toBe(false);
            expect(exitFilterMenu).not.toHaveBeenCalled();
        });

        it('keeps typing past an accepted literal out of the menu', () => {
            service.searchQuery.set('nsjkfncs type:candle');
            service.onOptionSelected(0);

            service.searchQuery.set('nsjkfncs type:candles');

            expect(service.filterMenuOpen()).toBe(false);
        });

        it('reopens the menu when the input is edited back in front of the accepted literal', () => {
            service.searchQuery.set('nsjkfncs type:candle');
            service.onOptionSelected(0);

            service.searchQuery.set('nsjkfncs type:c');

            expect(service.filterMenuOpen()).toBe(true);
            expect(service.deadEnd()).toBe(false);
        });

        it('keeps an accepted literal intact when the filter picker is opened again', () => {
            service.searchQuery.set('nsjkfncs type:candle');
            service.onOptionSelected(0);

            service.openFilterPicker();

            expect(service.searchQuery()).toBe('nsjkfncs type:candle');
            expect(service.deadEnd()).toBe(false);
            expect(service.menuOptions().map((option) => option.id)).toEqual(['type', 'course', 'exclude']);
        });
    });

    describe('deriveContextTokens', () => {
        it('returns a course token for a student course URL', () => {
            expect(service.deriveContextTokens('/courses/42')).toEqual([{ facet: 'course', value: '42' }]);
        });

        it('adds a type token for a known tab segment', () => {
            expect(service.deriveContextTokens('/course-management/7/exercises')).toEqual([
                { facet: 'course', value: '7' },
                { facet: 'type', value: 'exercise' },
            ]);
        });

        it('returns undefined for a non course-scoped URL', () => {
            expect(service.deriveContextTokens('/dashboard')).toBeUndefined();
        });
    });

    describe('editing a chip', () => {
        it('drops the chips the new value rules out, so a metadata chip cannot survive next to content search', () => {
            // Two metadata type chips; the first is re-picked as slides and videos, which reads a different
            // collection. Leaving the second behind would show a chip the search ignores.
            service.searchQuery.set('');
            applyTokens.mockClear();
            service.tokens.set([
                { facet: 'type', value: 'lecture' },
                { facet: 'type', value: 'exam' },
            ]);

            service.onChipSelected(0);
            const slidesIndex = service.menuOptions().findIndex((option) => option.id === 'lecture_content');
            service.onOptionSelected(slidesIndex);

            expect(applyTokens).toHaveBeenLastCalledWith([{ facet: 'type', value: 'lecture_content', negate: false }]);
        });

        it('keeps the chip in place when the new value rules nothing out', () => {
            service.searchQuery.set('');
            applyTokens.mockClear();
            service.tokens.set([
                { facet: 'type', value: 'lecture' },
                { facet: 'type', value: 'exam' },
            ]);

            service.onChipSelected(0);
            const faqIndex = service.menuOptions().findIndex((option) => option.id === 'faq');
            service.onOptionSelected(faqIndex);

            expect(applyTokens).toHaveBeenLastCalledWith([
                { facet: 'type', value: 'faq', negate: false },
                { facet: 'type', value: 'exam' },
            ]);
        });
    });

    describe('reset', () => {
        it('clears all filter composition state', () => {
            service.tokens.set([{ facet: 'type', value: 'exercise' }]);
            service.searchQuery.set('type:');
            service.filterPickerOpen.set(true);
            service.editingChip.set(1);
            service.selectedChip.set(0);
            service.menuActiveIndex.set(3);

            service.reset();

            expect(service.tokens()).toEqual([]);
            expect(service.searchQuery()).toBe('');
            expect(service.filterPickerOpen()).toBe(false);
            expect(service.editingChip()).toBe(-1);
            expect(service.selectedChip()).toBe(-1);
            expect(service.menuActiveIndex()).toBe(0);
        });
    });
});
