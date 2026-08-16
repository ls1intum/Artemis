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
        service.configure({ applyTokens, requestFocus });
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

        it('steps into the exclude sub-menu (setQuery) without closing the picker or adding a token', () => {
            service.filterPickerOpen.set(true);
            const index = service.menuOptions().findIndex((option) => option.action.kind === 'setQuery');
            expect(index).toBeGreaterThanOrEqual(0);

            service.onOptionSelected(index);

            expect(service.searchQuery()).toBe('-');
            expect(service.filterPickerOpen()).toBe(true);
            expect(service.menuHeaderKey()).toBe('global.search.chooseExclude');
            expect(service.menuOptions().map((option) => option.id)).toEqual(['-type', '-course']);
            expect(applyTokens).not.toHaveBeenCalled();
            expect(requestFocus).toHaveBeenCalled();
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

        it('returns to the exclude sub-menu from an exclude value menu', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('-type:');

            service.back();

            expect(service.searchQuery()).toBe('-');
            expect(service.menuOptions().map((option) => option.id)).toEqual(['-type', '-course']);
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
    });

    describe('guided picker', () => {
        it('opens the picker and requests focus', () => {
            service.openFilterPicker();
            expect(service.filterPickerOpen()).toBe(true);
            expect(requestFocus).toHaveBeenCalled();
        });

        it('does not open the picker while a value menu operator is active', () => {
            service.searchQuery.set('type:');
            service.openFilterPicker();
            expect(service.filterPickerOpen()).toBe(false);
        });

        it('toggles the picker closed when already open', () => {
            service.filterPickerOpen.set(true);
            service.toggleFilterPicker();
            expect(service.filterPickerOpen()).toBe(false);
            expect(requestFocus).toHaveBeenCalled();
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

        it('cancels the operator and refocuses on Escape', () => {
            service.searchQuery.set('type:');
            service.editingChip.set(2);

            service.handleMenuKey(keydown('Escape'));

            expect(service.searchQuery()).toBe('');
            expect(service.editingChip()).toBe(-1);
            expect(requestFocus).toHaveBeenCalled();
        });

        it('steps back to the root picker (not close) on Escape from the exclude sub-menu', () => {
            service.filterPickerOpen.set(true);
            service.searchQuery.set('-');

            service.handleMenuKey(keydown('Escape'));

            expect(service.searchQuery()).toBe('');
            expect(service.filterPickerOpen()).toBe(true);
            expect(service.menuHeaderKey()).toBe('global.search.addFilter');
            expect(requestFocus).toHaveBeenCalled();
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
