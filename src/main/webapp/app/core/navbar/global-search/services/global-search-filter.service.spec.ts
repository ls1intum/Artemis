import { TestBed } from '@angular/core/testing';
import { type Mock, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { GlobalSearchFilterService } from './global-search-filter.service';
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

    beforeEach(() => {
        vi.clearAllMocks();
        mockCourseStorageService.getCourses.mockReturnValue([]);
        TestBed.configureTestingModule({
            providers: [
                GlobalSearchFilterService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: CourseStorageService, useValue: mockCourseStorageService },
            ],
        });
        service = TestBed.inject(GlobalSearchFilterService);
        applyTokens = vi.fn<(tokens: FilterToken[]) => void>();
        requestFocus = vi.fn<() => void>();
        exitFilterMenu = vi.fn<() => void>();
        refreshSearch = vi.fn<() => void>();
        service.configure({ applyTokens, requestFocus, exitFilterMenu, refreshSearch });
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

    describe('operator + menu header', () => {
        it('parses the typed facet operator and opens the value menu', () => {
            service.searchQuery.set('type:');
            expect(service.operator()?.facet).toBe('type');
            expect(service.filterMenuOpen()).toBe(true);
            expect(service.menuHeaderKey()).toBe('global.search.chooseType');
        });

        it('uses an exclude header when the operator is negated', () => {
            service.searchQuery.set('-type:');
            expect(service.menuHeaderKey()).toBe('global.search.chooseExcludeType');
            service.searchQuery.set('-course:');
            expect(service.menuHeaderKey()).toBe('global.search.chooseExcludeCourse');
        });

        it('shows the "add filter" header for the guided picker (no operator)', () => {
            service.filterPickerOpen.set(true);
            expect(service.menuHeaderKey()).toBe('global.search.addFilter');
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

    describe('addFilter', () => {
        it('commits a type token for a known tag set', () => {
            service.addFilter(['exercise']);
            expect(applyTokens).toHaveBeenCalledWith([{ facet: 'type', value: 'exercise' }]);
        });

        it('does nothing for tags that match no facet', () => {
            service.addFilter(['not-a-real-type' as never]);
            expect(applyTokens).not.toHaveBeenCalled();
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
            expect(service.menuHeaderKey()).toBe('global.search.chooseExclude');
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
            expect(service.menuHeaderKey()).toBe('global.search.addFilter');
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

        it('cancels a directly typed operator and leaves the filter surface on Escape', () => {
            service.searchQuery.set('type:');
            service.editingChip.set(2);

            service.handleMenuKey(keydown('Escape'));

            expect(service.searchQuery()).toBe('');
            expect(service.editingChip()).toBe(-1);
            expect(exitFilterMenu).toHaveBeenCalled();
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
            expect(service.menuHeaderKey()).toBe('global.search.addFilter');
            expect(exitFilterMenu).not.toHaveBeenCalled();
        });

        it('keeps the search text when Escape leaves the filter surface', () => {
            service.searchQuery.set('linear regression type:');

            service.handleMenuKey(keydown('Escape'));

            expect(service.searchQuery()).toBe('linear regression');
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
            expect(service.menuOptions()).toHaveLength(6);
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
            expect(service.menuHeaderKey()).toBe('global.search.addFilter');
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
