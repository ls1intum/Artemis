import { faBan, faGraduationCap, faLayerGroup } from '@fortawesome/free-solid-svg-icons';
import { FilterToken, TypeFacetValue } from './search-token.model';
import { ParsedOperator } from './search-operator.util';
import { TYPE_FACETS, TYPE_FACET_ORDER } from './facet-catalog';
import { FilterChipView, FilterMenuOption } from './search-menu.model';

/** Translate function passed in from the component so these builders stay pure and framework-free. */
type Translate = (key: string, params?: object) => string;

/** Minimal course shape the value menu needs (from `CourseStorageService`). */
export interface MenuCourse {
    id?: number;
    title?: string;
}

/** Maximum number of course options shown in the `course:` value menu. */
const MAX_COURSE_OPTIONS = 8;

/**
 * Builds the filter-menu options for the current state: the guided picker's filter actions (when no
 * operator is typed but the picker is open) or the value list for the active `facet:` operator.
 * Pure: never mutates its inputs and has no framework dependencies.
 */
export function buildFilterMenuOptions(params: {
    operator?: ParsedOperator;
    pickerOpen: boolean;
    /** Raw input text, used to narrow the picker's filter actions. */
    searchQuery: string;
    tokens: FilterToken[];
    /** Index of the chip being re-picked, or -1; excluded from "already applied" so its value stays selectable. */
    editingChip: number;
    /** Lazily provides the accessible courses; only invoked for a `course:` menu, never for the picker or type menu. */
    courses: () => MenuCourse[];
    translate: Translate;
}): FilterMenuOption[] {
    const { operator, pickerOpen, searchQuery, tokens, editingChip, courses, translate } = params;
    if (!operator) {
        if (!pickerOpen) {
            return [];
        }
        const pickerQuery = searchQuery.trim().toLowerCase();
        // A leading "-" steps into the exclude sub-menu (choose a type or a course to hide); the picker stays open.
        if (pickerQuery.startsWith('-')) {
            return excludeActions(translate).filter((action) => matchesQuery(action, pickerQuery.slice(1).trim()));
        }
        return rootActions(translate).filter((action) => matchesQuery(action, pickerQuery));
    }
    const query = operator.query.trim().toLowerCase();
    // Values already applied in the same include/exclude state are hidden, so the menu only ever adds a
    // filter; the chip being re-picked is excluded so its own value stays selectable.
    const applied = new Set(
        tokens.filter((token, index) => index !== editingChip && token.facet === operator.facet && !!token.negate === !!operator.negate).map((token) => token.value),
    );
    return operator.facet === 'type' ? typeOptions(operator, applied, query, translate) : courseOptions(courses(), applied, query, translate);
}

/** Resolves a token into its display chip (label, icon, colour family, selection). Pure. */
export function toChipView(token: FilterToken, index: number, selectedChip: number, translate: Translate, courseTitle: (id: number) => string | undefined): FilterChipView {
    const selected = index === selectedChip;
    const key = `${token.facet}:${token.value}:${!!token.negate}`;
    if (token.facet === 'course') {
        const courseId = Number(token.value);
        const label = courseTitle(courseId) ?? translate('global.search.courseFallbackLabel', { id: courseId });
        return {
            key,
            label,
            facetLabel: translate('global.search.facets.course'),
            icon: token.negate ? faBan : faGraduationCap,
            family: 'course',
            negate: !!token.negate,
            selected,
        };
    }
    const meta = TYPE_FACETS[token.value as TypeFacetValue];
    return {
        key,
        label: meta ? translate(meta.labelKey) : token.value,
        facetLabel: translate('global.search.facets.type'),
        icon: token.negate ? faBan : (meta?.icon ?? faGraduationCap),
        family: 'type',
        negate: !!token.negate,
        selected,
    };
}

/** Top-level guided-picker rows: include by type, include by course, or step into the exclude sub-menu. */
function rootActions(translate: Translate): FilterMenuOption[] {
    return [
        {
            id: 'type',
            label: translate('global.search.filterAction.type'),
            description: translate('global.search.filterAction.typeDescription'),
            icon: faLayerGroup,
            hint: 'type:',
            action: { kind: 'operator', prefix: 'type:' },
        },
        {
            id: 'course',
            label: translate('global.search.filterAction.course'),
            description: translate('global.search.filterAction.courseDescription'),
            icon: faGraduationCap,
            hint: 'course:',
            action: { kind: 'operator', prefix: 'course:' },
        },
        {
            id: 'exclude',
            label: translate('global.search.filterAction.exclude'),
            description: translate('global.search.filterAction.excludeDescription'),
            icon: faBan,
            // The "{filter}" placeholder is rendered faded — it shows the exclude command shape (−<facet>:).
            hint: '−{filter}:',
            action: { kind: 'setQuery', query: '-' },
        },
    ];
}

/** The exclude sub-menu: pick whether to hide a type or a course (reached via the exclude row or by typing "-"). */
function excludeActions(translate: Translate): FilterMenuOption[] {
    return [
        {
            id: '-type',
            label: translate('global.search.filterAction.excludeTypeRow'),
            description: translate('global.search.filterAction.excludeTypeDescription'),
            icon: faLayerGroup,
            hint: '−type:',
            action: { kind: 'operator', prefix: '-type:' },
        },
        {
            id: '-course',
            label: translate('global.search.filterAction.excludeCourseRow'),
            description: translate('global.search.filterAction.excludeCourseDescription'),
            icon: faGraduationCap,
            hint: '−course:',
            action: { kind: 'operator', prefix: '-course:' },
        },
    ];
}

function typeOptions(operator: ParsedOperator, applied: Set<string>, query: string, translate: Translate): FilterMenuOption[] {
    const remaining = TYPE_FACET_ORDER.filter((value) => !applied.has(value));
    // In exclude mode, never offer the last remaining type: excluding every type is nonsensical
    // (the server would fall through to uncategorised results such as lecture slides).
    const selectable = operator.negate && remaining.length <= 1 ? [] : remaining;
    return selectable
        .map((value): FilterMenuOption => ({
            id: value,
            label: translate(TYPE_FACETS[value].labelKey),
            // In exclude mode the include-flavoured entity description ("Search courses…") reads backwards and
            // repeating a single "will be hidden" line six times is noise, so drop it — the red icons and the
            // "Exclude type" header already say what these rows do.
            description: operator.negate ? undefined : translate(TYPE_FACETS[value].descriptionKey),
            icon: TYPE_FACETS[value].icon,
            action: { kind: 'value', value },
        }))
        .filter((option) => !query || option.label.toLowerCase().includes(query) || option.id.includes(query));
}

function courseOptions(courses: MenuCourse[], applied: Set<string>, query: string, translate: Translate): FilterMenuOption[] {
    return courses
        .filter((course) => course.id !== undefined && !applied.has(String(course.id)) && (!query || (course.title ?? '').toLowerCase().includes(query)))
        .slice(0, MAX_COURSE_OPTIONS)
        .map((course): FilterMenuOption => ({
            id: String(course.id),
            label: course.title ?? translate('global.search.courseFallbackLabel', { id: course.id }),
            icon: faGraduationCap,
            action: { kind: 'value', value: String(course.id) },
        }));
}

function matchesQuery(option: FilterMenuOption, query: string): boolean {
    return !query || option.label.toLowerCase().includes(query) || (option.hint ?? '').toLowerCase().includes(query);
}
