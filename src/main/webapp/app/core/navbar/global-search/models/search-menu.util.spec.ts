import { MenuCourse, buildFilterMenuOptions, toChipView } from 'app/core/navbar/global-search/models/search-menu.util';
import { FilterToken } from 'app/core/navbar/global-search/models/search-token.model';
import { ParsedOperator } from 'app/core/navbar/global-search/models/search-operator.util';

describe('search menu builders', () => {
    const translate = (key: string) => key;
    const typeOp = (query = '', negate = false): ParsedOperator => ({ facet: 'type', negate, query, prefix: negate ? '-type:' : 'type:' });
    const courseOp = (query = ''): ParsedOperator => ({ facet: 'course', negate: false, query, prefix: 'course:' });
    const type = (value: string, negate = false): FilterToken => ({ facet: 'type', value, negate });
    const base = { pickerOpen: false, searchQuery: '', tokens: [] as FilterToken[], editingChip: -1, courses: () => [] as MenuCourse[], translate };

    describe('buildFilterMenuOptions - guided picker', () => {
        it('returns nothing when no operator and the picker is closed', () => {
            expect(buildFilterMenuOptions({ ...base, pickerOpen: false })).toEqual([]);
        });

        it('returns all four filter actions when the picker is open', () => {
            const options = buildFilterMenuOptions({ ...base, pickerOpen: true });
            expect(options.map((option) => option.id)).toEqual(['type', '-type', 'course', '-course']);
            expect(options.every((option) => option.action.kind === 'operator')).toBe(true);
        });

        it('narrows to the exclusions when the query is "-"', () => {
            const options = buildFilterMenuOptions({ ...base, pickerOpen: true, searchQuery: '-' });
            expect(options.map((option) => option.id)).toEqual(['-type', '-course']);
        });

        it('narrows to the course actions when the query is "cou"', () => {
            const options = buildFilterMenuOptions({ ...base, pickerOpen: true, searchQuery: 'cou' });
            expect(options.map((option) => option.id)).toEqual(['course', '-course']);
        });
    });

    describe('buildFilterMenuOptions - value menu', () => {
        it('lists all six type values for a type operator', () => {
            const options = buildFilterMenuOptions({ ...base, operator: typeOp() });
            expect(options).toHaveLength(6);
            expect(options[0].action).toEqual({ kind: 'value', value: 'course' });
        });

        it('hides an already-applied type value', () => {
            const options = buildFilterMenuOptions({ ...base, operator: typeOp(), tokens: [type('exam')] });
            expect(options.map((option) => option.id)).not.toContain('exam');
        });

        it('keeps the edited chip value selectable', () => {
            const options = buildFilterMenuOptions({ ...base, operator: typeOp(), tokens: [type('exam')], editingChip: 0 });
            expect(options.map((option) => option.id)).toContain('exam');
        });

        it('never offers the last remaining type in exclude mode', () => {
            const excludedFive = ['course', 'exercise', 'lecture', 'communication', 'faq'].map((value) => type(value, true));
            expect(buildFilterMenuOptions({ ...base, operator: typeOp('', true), tokens: excludedFive })).toEqual([]);
        });

        it('filters course options by title and caps the list at eight', () => {
            const courseList: MenuCourse[] = Array.from({ length: 12 }, (_, i) => ({ id: i + 1, title: `Course ${i + 1}` }));
            const courses = () => courseList;
            expect(buildFilterMenuOptions({ ...base, operator: courseOp(), courses })).toHaveLength(8);
            const filtered = buildFilterMenuOptions({ ...base, operator: courseOp('course 3'), courses });
            expect(filtered.map((option) => option.action)).toEqual([{ kind: 'value', value: '3' }]);
        });
    });

    describe('toChipView', () => {
        it('builds a type chip', () => {
            const chip = toChipView(type('exam'), 0, -1, translate, () => undefined);
            expect(chip.family).toBe('type');
            expect(chip.negate).toBe(false);
            expect(chip.selected).toBe(false);
            expect(chip.facetLabel).toBe('global.search.facets.type');
        });

        it('builds a selected, negated course chip with a resolved title', () => {
            const chip = toChipView({ facet: 'course', value: '5', negate: true }, 1, 1, translate, (id) => `Course ${id}`);
            expect(chip.family).toBe('course');
            expect(chip.negate).toBe(true);
            expect(chip.label).toBe('Course 5');
            expect(chip.selected).toBe(true);
        });
    });
});
