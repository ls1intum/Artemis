import { Injectable, Signal, WritableSignal, computed, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { FilterChipView, FilterMenuOption } from '../models/search-menu.model';
import { buildFilterMenuOptions, toChipView } from '../models/search-menu.util';
import { SearchEntityType } from '../models/searchable-entity.model';
import { FilterToken, TypeFacetValue } from '../models/search-token.model';
import { TYPE_FACETS, TYPE_FACET_ORDER } from '../models/facet-catalog';
import { addOrToggleToken, excludedCourseIds, expandTypeTokens, removeTokenAt, selectedCourseIds } from '../models/search-token.util';
import { appendOperator, parseOperator, stripOperator } from '../models/search-operator.util';

/**
 * Side-effect callbacks the host component wires into the filter store so the store can stay free of the
 * search pipeline and DOM: {@link FilterSideEffects.applyTokens} commits a new token set through the
 * component's search pipeline (loading skeleton + debounced request), {@link FilterSideEffects.requestFocus}
 * returns focus to the search input, and {@link FilterSideEffects.exitFilterMenu} leaves the filter surface
 * altogether once there is no level left to step back to.
 */
export interface FilterSideEffects {
    applyTokens: (tokens: FilterToken[]) => void;
    requestFocus: () => void;
    /**
     * Leaves the filter surface entirely. Only the host knows what sits behind the menu, so it decides
     * whether to reveal the pane underneath or close the overlay when the menu was the home screen.
     */
    exitFilterMenu: () => void;
    /** Re-runs the search for the current text without changing the filters (used when a literal search is accepted). */
    refreshSearch: () => void;
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
    /** Whether the guided picker is showing its exclude level. Held as state, not as a "-" typed into the input. */
    readonly excludeMode: WritableSignal<boolean> = signal(false);
    /**
     * Input text the user accepted as a literal search from the dead-end row. While the input still starts
     * with it, the trailing `facet:` is read as ordinary text rather than as an operator, so typing on past
     * the accepted point does not reopen the menu.
     */
    private readonly literalFrom: WritableSignal<string | undefined> = signal(undefined);

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
    /** True while the input still holds a `facet:` the user explicitly chose to search for verbatim. */
    readonly literalAccepted: Signal<boolean> = computed(() => {
        const from = this.literalFrom();
        return from !== undefined && this.searchQuery().startsWith(from);
    });
    // The `facet:` operator being typed (drives the value menu + operator colouring), if any. Suppressed
    // once the user has accepted the text as a literal search, which is what closes the menu on that choice.
    readonly operator = computed(() => (this.literalAccepted() ? undefined : parseOperator(this.searchQuery())));
    /**
     * The text sent to the server: everything before a trailing operator, or the whole input. Because it is
     * independent of the operator, the results for the query keep updating behind an open filter menu.
     */
    readonly searchText: Signal<string> = computed(() => {
        const op = this.operator();
        return (op ? op.text : this.searchQuery()).trim();
    });
    // Options for the value menu, derived from the current operator + its query.
    readonly menuOptions: Signal<FilterMenuOption[]> = computed(() =>
        buildFilterMenuOptions({
            operator: this.operator(),
            pickerOpen: this.filterPickerOpen(),
            excludeMode: this.excludeMode(),
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
    /**
     * A typed value that matches no known value: the menu collapses to the single literal-search row. This is
     * not the same as an exhausted list (every value already applied), which keeps its way back.
     */
    readonly deadEnd: Signal<boolean> = computed(() => this.menuOptions().some((option) => option.action.kind === 'literal'));
    /** Message replacing the menu header at a dead end, naming what the user typed. */
    readonly deadEndMessage: Signal<{ key: string; value: string } | undefined> = computed(() => {
        const op = this.operator();
        if (!op || !this.deadEnd()) {
            return undefined;
        }
        // "not one of your courses" rather than "not a course": a course by that name may well exist and
        // simply not be visible to this user, and the flatter phrasing would be a small lie.
        return { key: op.facet === 'type' ? 'global.search.notAType' : 'global.search.notYourCourse', value: op.query.trim() };
    });
    /**
     * Whether the menu can step back a level: from a value menu to the level it was opened from, or from the
     * exclude level to the root. Deliberately not conditional on the picker having been opened: the root picker
     * is the home screen, so it is always a valid place for a value menu to return to, including one the user
     * typed by hand. Without that, Escape on a hand-typed operator with nothing behind it closed the whole
     * palette mid-composition. A dead end has no level behind it, so it offers the literal search instead.
     */
    readonly canGoBack: Signal<boolean> = computed(() => (!!this.operator() || this.excludeMode()) && !this.deadEnd());

    private sideEffects: FilterSideEffects = {
        applyTokens: () => {},
        requestFocus: () => {},
        exitFilterMenu: () => {},
        refreshSearch: () => {},
    };

    /** Wires the host component's search-pipeline, focus, and menu-exit side-effects. Called once, from the component constructor. */
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
        this.excludeMode.set(false);
        this.literalFrom.set(undefined);
    }

    /** Applies the chosen menu option: a picker action injects an operator; a value adds / replaces a filter. */
    onOptionSelected(index: number): void {
        const option = this.menuOptions()[index];
        if (!option) {
            return;
        }
        if (option.action.kind === 'excludeStep') {
            // Step into the exclude level without touching the input; the picker stays open.
            this.excludeMode.set(true);
            this.menuActiveIndex.set(0);
            this.sideEffects.requestFocus();
            return;
        }
        if (option.action.kind === 'operator') {
            // Guided picker: append the operator prefix and open the value menu. The picker stays "open" through
            // the flow so the value menu can offer a step back to the previous level.
            this.appendPrefix(option.action.prefix);
            return;
        }
        if (option.action.kind === 'literal') {
            this.acceptLiteral();
            return;
        }
        if (option.action.kind === 'clearValue') {
            // Keep the facet, drop the value that matched nothing, so the full list comes back.
            const active = this.operator();
            if (active) {
                this.searchQuery.set(this.searchQuery().slice(0, active.start + active.prefix.length));
            }
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
        // Only the operator leaves the input; whatever the user was searching for stays exactly as typed.
        this.searchQuery.set(stripOperator(this.searchQuery()));
        this.filterPickerOpen.set(false);
        this.excludeMode.set(false);
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

    /** Steps one level back: an exclude value menu returns to the exclude chooser, everything else to the root. */
    back(): void {
        const op = this.operator();
        this.searchQuery.set(stripOperator(this.searchQuery()));
        this.excludeMode.set(!!op?.negate);
        this.filterPickerOpen.set(true);
        this.editingChip.set(-1);
        this.menuActiveIndex.set(0);
        this.sideEffects.requestFocus();
    }

    /**
     * Abandons the operator reading of the input and searches for the raw text instead. Chosen from the
     * dead-end row, or by pressing Escape there, where "cancel" cannot mean deleting text the user typed.
     */
    private acceptLiteral(): void {
        this.literalFrom.set(this.searchQuery());
        this.filterPickerOpen.set(false);
        this.excludeMode.set(false);
        this.editingChip.set(-1);
        this.menuActiveIndex.set(0);
        this.sideEffects.requestFocus();
        this.sideEffects.refreshSearch();
    }

    /**
     * Clears the way for a level change without destroying anything the user typed. A half-typed operator is
     * text they can retype from the menu, so it goes; a value that matches nothing is not an operator at all,
     * so it is kept and reinterpreted as literal search text rather than silently deleted.
     */
    private dropOperatorWithoutLosingText(): void {
        if (this.deadEnd()) {
            this.literalFrom.set(this.searchQuery());
            this.sideEffects.refreshSearch();
            return;
        }
        if (!this.literalAccepted()) {
            this.searchQuery.set(stripOperator(this.searchQuery()));
        }
    }

    /** Appends an operator prefix to the input, keeping the search text in front of it. */
    private appendPrefix(prefix: string): void {
        this.literalFrom.set(undefined);
        this.searchQuery.set(appendOperator(this.searchQuery(), prefix));
        this.excludeMode.set(false);
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
                if (this.deadEnd()) {
                    // Not a filter, so there is no level to step back to, and cancel must not eat text the user
                    // typed as a search term. Escape means the same as the literal row here.
                    this.acceptLiteral();
                    break;
                }
                if (this.canGoBack()) {
                    // In a value menu or the exclude level: step back one level instead of leaving the menu.
                    this.back();
                    break;
                }
                // Nothing left to step back to: drop the operator and the picker, then let the host leave the
                // filter surface. It never closes the menu onto an empty pane, because the picker is the home
                // screen and nothing may render behind it there.
                this.leaveFilterMenu();
                this.sideEffects.exitFilterMenu();
                break;
        }
    }

    /**
     * Shows the guided filter picker at its root (used by the Filter button and Cmd/Ctrl+F). From a value menu
     * it steps back to the root; at the root it only returns focus to the input. The search text is never
     * touched, which is the whole point: composing a filter must not cost the user what they were looking for.
     */
    openFilterPicker(): void {
        this.editingChip.set(-1);
        this.selectedChip.set(-1);
        this.dropOperatorWithoutLosingText();
        this.excludeMode.set(false);
        this.filterPickerOpen.set(true);
        this.menuActiveIndex.set(0);
        this.sideEffects.requestFocus();
    }

    /** Closes the filter surface, dropping a half-typed operator but keeping the search text. */
    leaveFilterMenu(): void {
        this.dropOperatorWithoutLosingText();
        this.filterPickerOpen.set(false);
        this.excludeMode.set(false);
        this.editingChip.set(-1);
        this.menuActiveIndex.set(0);
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
        this.appendPrefix(token.negate ? `-${token.facet}:` : `${token.facet}:`);
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
        lectures: ['lecture', 'lecture_unit'],
        exams: ['exam'],
        communication: ['channel', 'post', 'answer_post'],
        faq: ['faq'],
        faqs: ['faq'],
    };
}
