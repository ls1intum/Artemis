import { MenuCourse, buildFilterMenuOptions, toChipView } from 'app/core/navbar/global-search/models/search-menu.util';
import { FilterToken } from 'app/core/navbar/global-search/models/search-token.model';
import { ParsedOperator } from 'app/core/navbar/global-search/models/search-operator.util';

describe('search menu builders', () => {
    const translate = (key: string) => key;
    const typeOp = (query = '', negate = false): ParsedOperator => ({ facet: 'type', negate, query, prefix: negate ? '-type:' : 'type:', start: 0, text: '' });
    const courseOp = (query = ''): ParsedOperator => ({ facet: 'course', negate: false, query, prefix: 'course:', start: 0, text: '' });
    const type = (value: string, negate = false): FilterToken => ({ facet: 'type', value, negate });
    const base = { pickerOpen: false, excludeMode: false, searchQuery: '', tokens: [] as FilterToken[], editingChip: -1, courses: () => [] as MenuCourse[], translate };

    describe('buildFilterMenuOptions - guided picker', () => {
        it('returns nothing when no operator and the picker is closed', () => {
            expect(buildFilterMenuOptions({ ...base, pickerOpen: false })).toEqual([]);
        });

        it('returns the three root actions when the picker is open', () => {
            const options = buildFilterMenuOptions({ ...base, pickerOpen: true });
            expect(options.map((option) => option.id)).toEqual(['type', 'course', 'exclude']);
            // The exclude row steps into a sub-menu rather than appending an operator.
            expect(options.find((option) => option.id === 'exclude')?.action).toEqual({ kind: 'excludeStep' });
        });

        it('lists the exclude actions while the picker is on its exclude level', () => {
            const options = buildFilterMenuOptions({ ...base, pickerOpen: true, excludeMode: true });
            expect(options.map((option) => option.id)).toEqual(['-type', '-course']);
            expect(options.every((option) => option.action.kind === 'operator')).toBe(true);
        });

        it('leaves the root actions alone while the user types, because plain text leaves the picker instead', () => {
            const options = buildFilterMenuOptions({ ...base, pickerOpen: true, searchQuery: 'linear regression' });
            expect(options.map((option) => option.id)).toEqual(['type', 'course', 'exclude']);
        });
    });

    describe('buildFilterMenuOptions - value menu', () => {
        it('lists all six type values for a type operator', () => {
            const options = buildFilterMenuOptions({ ...base, operator: typeOp() });
            expect(options).toHaveLength(6);
            expect(options[0].action).toEqual({ kind: 'value', value: 'course' });
            // Include mode uses the entity descriptions.
            expect(options[0].description).toBe('global.search.entities.coursesDescription');
        });

        it('drops the include-flavoured description in exclude mode (avoids backwards / repetitive text)', () => {
            const options = buildFilterMenuOptions({ ...base, operator: typeOp('', true) });
            expect(options.every((option) => option.description === undefined)).toBe(true);
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

        it('offers the literal search first and the recovery row second when a typed value matches nothing', () => {
            const options = buildFilterMenuOptions({ ...base, operator: typeOp('candle'), searchQuery: 'nsjkfncs type:candle' });
            expect(options.map((option) => option.action.kind)).toEqual(['literal', 'clearValue']);
            expect(options[0].action).toEqual({ kind: 'literal', text: 'nsjkfncs type:candle' });
            expect(options[0].literal).toBe('nsjkfncs type:candle');
            expect(options[1].label).toBe('global.search.showAllTypes');
        });

        it('names the recovery row after the facet, so "type" is made concrete rather than assumed', () => {
            const options = buildFilterMenuOptions({ ...base, operator: courseOp('candle'), searchQuery: 'course:candle' });
            expect(options[1].label).toBe('global.search.showYourCourses');
        });

        it('offers no literal row for an exhausted list, which is an empty menu rather than a wrong word', () => {
            const excludedFive = ['course', 'exercise', 'lecture', 'communication', 'faq'].map((value) => type(value, true));
            expect(buildFilterMenuOptions({ ...base, operator: typeOp('', true), tokens: excludedFive, searchQuery: '-type:' })).toEqual([]);
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
