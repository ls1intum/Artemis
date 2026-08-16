import { Injectable, Signal, WritableSignal, computed, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { FilterChipView, FilterMenuOption } from '../models/search-menu.model';
import { buildFilterMenuOptions, toChipView } from '../models/search-menu.util';
import { SearchEntityType } from '../models/searchable-entity.model';
import { FilterToken, TypeFacetValue } from '../models/search-token.model';
import { TYPE_FACETS, TYPE_FACET_ORDER } from '../models/facet-catalog';
import { addOrToggleToken, excludedCourseIds, expandTypeTokens, removeTokenAt, selectedCourseIds } from '../models/search-token.util';
import { parseOperator } from '../models/search-operator.util';

/**
 * Side-effect callbacks the host component wires into the filter store so the store can stay free of the
 * search pipeline and DOM: {@link FilterSideEffects.applyTokens} commits a new token set through the
 * component's search pipeline (loading skeleton + debounced request), and {@link FilterSideEffects.requestFocus}
 * returns focus to the search input.
 */
export interface FilterSideEffects {
    applyTokens: (tokens: FilterToken[]) => void;
    requestFocus: () => void;
}

/**
 * Owns the global-search filter composition: the active filter tokens (chips), the typed `facet:` value menu,
 * the guided picker, and the chip-edit flow. Extracted from {@link GlobalSearchModalComponent} so the component
 * stays a thin view over the search pipeline, keyboard navigation, results, and overlay lifecycle.
 * <p>
 * The store never runs a search or touches the DOM itself; it calls back into the host via {@link FilterSideEffects}
 * at the exact points the component previously inlined those effects, so behaviour is unchanged. Provided at the
 * component level (one store per modal instance).
 */
@Injectable()
export class GlobalSearchFilterService {
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly translateService = inject(TranslateService);

    /** The current text in the search input (may be a `facet:` operator being typed). */
    readonly searchQuery: WritableSignal<string> = signal('');
    /** Single source of truth for the active filters. Each token is one chip. */
    readonly tokens: WritableSignal<FilterToken[]> = signal<FilterToken[]>([]);
    /** Keyboard-selected chip index (-1 = none selected). */
    readonly selectedChip: WritableSignal<number> = signal(-1);
    /** Highlighted option in the value menu. */
    readonly menuActiveIndex: WritableSignal<number> = signal(0);
    /** Guided filter picker (Cmd/Ctrl+F): open when the user requests the facet chooser without typing an operator. */
    readonly filterPickerOpen: WritableSignal<boolean> = signal(false);
    /** Index of the chip currently being re-picked (tapped to change its value), or -1 when not editing. */
    readonly editingChip: WritableSignal<number> = signal(-1);

    // Query params derived from the tokens and sent to the server.
    readonly typesParam: Signal<string | undefined> = computed(() => expandTypeTokens(this.tokens()));
    readonly courseIdsParam: Signal<number[]> = computed(() => selectedCourseIds(this.tokens()));
    readonly excludeCourseIdsParam: Signal<number[]> = computed(() => excludedCourseIds(this.tokens()));
    // Server types for the currently included type facets (drives the navigation view's active cards).
    readonly activeFilters: Signal<SearchEntityType[]> = computed(() => [
        ...new Set(
            this.tokens()
                .filter((token) => token.facet === 'type' && !token.negate)
                .flatMap((token) => TYPE_FACETS[token.value as TypeFacetValue]?.serverTypes ?? []),
        ),
    ]);
    // Fully-resolved chip view-models handed to the search-input for rendering.
    readonly chips: Signal<FilterChipView[]> = computed(() =>
        this.tokens().map((token, index) =>
            toChipView(
                token,
                index,
                this.selectedChip(),
                (key, params) => this.translateService.instant(key, params),
                (id) => this.courseStorageService.getCourse(id)?.title,
            ),
        ),
    );
    // The `facet:` operator being typed (drives the value menu + operator colouring), if any.
    readonly operator = computed(() => parseOperator(this.searchQuery()));
    // Options for the value menu, derived from the current operator + its query.
    readonly menuOptions: Signal<FilterMenuOption[]> = computed(() =>
        buildFilterMenuOptions({
            operator: this.operator(),
            pickerOpen: this.filterPickerOpen(),
            searchQuery: this.searchQuery(),
            tokens: this.tokens(),
            editingChip: this.editingChip(),
            courses: () => this.courseStorageService.getCourses(),
            translate: (key, params) => this.translateService.instant(key, params),
        }),
    );
    // True when the typed operator value exactly matches a known type / course, so the value text is
    // coloured as a confirmed filter (e.g. `type:course` turns blue once "course" is complete).
    readonly operatorValueValid: Signal<boolean> = computed(() => {
        const op = this.operator();
        const value = op?.query.trim().toLowerCase();
        if (!op || !value) {
            return false;
        }
        if (op.facet === 'type') {
            return TYPE_FACET_ORDER.some((facetValue) => facetValue === value || this.translateService.instant(TYPE_FACETS[facetValue].labelKey).toLowerCase() === value);
        }
        return this.courseStorageService.getCourses().some((course) => (course.title ?? '').toLowerCase() === value);
    });
    // Whether the menu panel (value menu or filter picker) should be shown.
    readonly filterMenuOpen: Signal<boolean> = computed(() => !!this.operator() || this.filterPickerOpen());
    // i18n key for the menu header: "Filter by" for the picker, else "Choose type" / "Choose course".
    readonly menuHeaderKey: Signal<string> = computed(() => {
        const op = this.operator();
        if (!op) {
            // Root picker vs the exclude sub-menu (entered with a leading "-").
            return this.filterPickerOpen() && this.searchQuery().trim().startsWith('-') ? 'global.search.chooseExclude' : 'global.search.addFilter';
        }
        if (op.negate) {
            return op.facet === 'course' ? 'global.search.chooseExcludeCourse' : 'global.search.chooseExcludeType';
        }
        return op.facet === 'course' ? 'global.search.chooseCourse' : 'global.search.chooseType';
    });

    /** Whether the guided picker can step back a level (i.e. we are in a value menu or the exclude sub-menu). */
    readonly canGoBack: Signal<boolean> = computed(() => this.filterPickerOpen() && this.searchQuery().trim().length > 0);

    private sideEffects: FilterSideEffects = {
        applyTokens: () => {},
        requestFocus: () => {},
    };

    /** Wires the host component's search-pipeline + focus side-effects. Called once, from the component constructor. */
    configure(sideEffects: FilterSideEffects): void {
        this.sideEffects = sideEffects;
    }

    /** Resets all filter composition state (called when the modal closes). Does not run a search. */
    reset(): void {
        this.searchQuery.set('');
        this.tokens.set([]);
        this.selectedChip.set(-1);
        this.filterPickerOpen.set(false);
        this.editingChip.set(-1);
        this.menuActiveIndex.set(0);
    }

    /** Applies the chosen menu option: a picker action injects an operator; a value adds / replaces a filter. */
    onOptionSelected(index: number): void {
        const option = this.menuOptions()[index];
        if (!option) {
            return;
        }
        if (option.action.kind === 'setQuery') {
            // Step into a sub-menu (e.g. the exclude chooser) without forming an operator; keep the picker open.
            this.searchQuery.set(option.action.query);
            this.menuActiveIndex.set(0);
            this.sideEffects.requestFocus();
            return;
        }
        if (option.action.kind === 'operator') {
            // Guided picker: inject the operator prefix and open the value menu. The picker stays "open" through the
            // flow so the value menu can offer a step back to the previous level.
            this.searchQuery.set(option.action.prefix);
            this.menuActiveIndex.set(0);
            this.sideEffects.requestFocus();
            return;
        }
        const op = this.operator();
        if (!op) {
            return;
        }
        const newToken: FilterToken = { facet: op.facet, value: option.action.value, negate: op.negate };
        const editing = this.editingChip();
        this.editingChip.set(-1);
        this.searchQuery.set('');
        // A value completes the flow: close the picker so the menu does not reopen on the now-empty query.
        this.filterPickerOpen.set(false);
        this.sideEffects.requestFocus();
        if (editing >= 0) {
            // Re-picking a chip: replace it in place so it keeps its position (the edit menu never offers a
            // value already applied elsewhere, so this can't create a duplicate).
            this.sideEffects.applyTokens(this.tokens().map((token, index) => (index === editing ? newToken : token)));
            return;
        }
        this.sideEffects.applyTokens(addOrToggleToken(this.tokens(), newToken));
    }

    onOptionHovered(index: number): void {
        this.menuActiveIndex.set(index);
    }

    /** Steps one level back in the guided picker: an exclude value menu returns to the exclude chooser, everything else to the root. */
    back(): void {
        const op = this.operator();
        this.searchQuery.set(op?.negate ? '-' : '');
        this.editingChip.set(-1);
        this.menuActiveIndex.set(0);
        this.sideEffects.requestFocus();
    }

    /** Handles keyboard navigation while the value menu is open. */
    handleMenuKey(event: KeyboardEvent): void {
        const count = this.menuOptions().length;
        switch (event.key) {
            case 'ArrowDown':
                event.preventDefault();
                if (count) {
                    this.menuActiveIndex.update((i) => Math.min(i + 1, count - 1));
                }
                break;
            case 'ArrowUp':
                event.preventDefault();
                if (count) {
                    this.menuActiveIndex.update((i) => Math.max(i - 1, 0));
                }
                break;
            case 'Enter':
            case 'Tab':
                event.preventDefault();
                if (count) {
                    this.onOptionSelected(this.menuActiveIndex());
                }
                break;
            case 'Escape':
                event.preventDefault();
                if (this.canGoBack()) {
                    // In a value menu or the exclude sub-menu: step back one level instead of closing the whole thing.
                    this.back();
                    break;
                }
                // At the root picker (or a typed value menu): cancel the picker / chip edit and clear the operator.
                this.filterPickerOpen.set(false);
                this.editingChip.set(-1);
                this.searchQuery.set('');
                this.sideEffects.requestFocus();
                break;
        }
    }

    /** Toggles the guided filter picker (used by the Filter button and Cmd/Ctrl+F): open closes, closed opens. */
    toggleFilterPicker(): void {
        if (this.filterPickerOpen()) {
            this.filterPickerOpen.set(false);
            this.sideEffects.requestFocus();
            return;
        }
        this.openFilterPicker();
    }

    /** Opens the guided filter picker (facet chooser). No-op while a value menu is already open. */
    openFilterPicker(): void {
        if (this.operator()) {
            return;
        }
        this.editingChip.set(-1);
        this.searchQuery.set('');
        this.filterPickerOpen.set(true);
        this.menuActiveIndex.set(0);
        this.sideEffects.requestFocus();
    }

    /** Taps a chip to re-pick its value: reopens that facet's menu; choosing a value replaces the chip. */
    onChipSelected(index: number): void {
        const token = this.tokens()[index];
        if (!token) {
            return;
        }
        this.selectedChip.set(-1);
        this.filterPickerOpen.set(false);
        this.editingChip.set(index);
        this.searchQuery.set(token.negate ? `-${token.facet}:` : `${token.facet}:`);
        this.sideEffects.requestFocus();
    }

    /** Removes the chip at the given index (its remove button was clicked). */
    onChipRemoved(index: number): void {
        this.selectedChip.set(-1);
        this.sideEffects.applyTokens(removeTokenAt(this.tokens(), index));
    }

    /**
     * Backspace on the empty input intentionally does nothing: a filter must not disappear from an accidental
     * keystroke. To remove a chip, click its × button, or arrow-navigate to it (which selects it) and press
     * Delete / Backspace — that path is handled by the modal's chip-navigation keydown handler.
     */
    onBackspaceRemoveFilter(): void {
        // no-op by design (see doc comment)
    }

    /**
     * Adds (or toggles off) the `type` token matching the given tags. Multi-select: clicking a card
     * keeps prior chips. The FacetCatalog keeps parity with present-day results.
     */
    addFilter(filterTypes: SearchEntityType[]): void {
        const facetValue = this.typeFacetForTags(filterTypes);
        if (facetValue) {
            this.sideEffects.applyTokens(addOrToggleToken(this.tokens(), { facet: 'type', value: facetValue }));
        }
    }

    /** Returns the token set with every `course` token removed (used when a view cannot carry a course filter). */
    tokensWithoutCourseFilter(): FilterToken[] {
        return this.tokens().filter((token) => token.facet !== 'course');
    }

    /**
     * Derives the course + type tokens implied by a course-scoped URL (student `/courses/:id` or instructor
     * `/course-management/:id`, optionally with a tab like `exercises`). Returns undefined when the URL is not
     * course-scoped, so the caller leaves the filters untouched.
     */
    deriveContextTokens(url: string): FilterToken[] | undefined {
        const match = url.match(GlobalSearchFilterService.COURSE_URL_PATTERN);
        if (!match) {
            return undefined;
        }
        const courseId = Number(match[1]);
        const tabSegment = match[2];
        const newTokens: FilterToken[] = [{ facet: 'course', value: String(courseId) }];
        if (tabSegment) {
            const filterTags = GlobalSearchFilterService.ROUTE_TO_FILTER_TAG[tabSegment];
            const facetValue = filterTags ? this.typeFacetForTags(filterTags) : undefined;
            if (facetValue) {
                newTokens.push({ facet: 'type', value: facetValue });
            }
        }
        return newTokens;
    }

    /**
     * Resolves the `type` facet value whose server types match the given tags exactly
     * (e.g. `['channel', 'post', 'answer_post']` -> `communication`).
     */
    private typeFacetForTags(tags: SearchEntityType[]): TypeFacetValue | undefined {
        const target = new Set(tags);
        return (Object.keys(TYPE_FACETS) as TypeFacetValue[]).find((value) => {
            const serverTypes = TYPE_FACETS[value].serverTypes;
            return serverTypes.length === target.size && serverTypes.every((type) => target.has(type));
        });
    }

    /**
     * Matches course URLs in both student view (`/courses/:courseId`) and instructor view (`/course-management/:courseId`),
     * optionally capturing a tab segment (e.g. `exercises`, `lectures`).
     */
    private static readonly COURSE_URL_PATTERN = /\/(?:courses|course-management)\/(\d+)(?:\/([^/?#]+))?/;

    /**
     * Maps route segments (e.g. 'exercises') to search filter tags (e.g. ['exercise']).
     * Includes both student view segments (e.g. 'faq') and instructor view segments (e.g. 'faqs').
     */
    private static readonly ROUTE_TO_FILTER_TAG: Record<string, SearchEntityType[]> = {
        exercises: ['exercise'],
        lectures: ['lecture'],
        exams: ['exam'],
        communication: ['channel', 'post', 'answer_post'],
        faq: ['faq'],
        faqs: ['faq'],
    };
}
