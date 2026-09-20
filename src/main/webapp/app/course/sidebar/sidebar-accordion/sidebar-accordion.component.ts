import { Component, OnDestroy, OnInit, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { faChevronRight, faFile } from '@fortawesome/free-solid-svg-icons';
import { Params } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { NgClass, TitleCasePipe } from '@angular/common';
import { SidebarCardDirective } from '../directive/sidebar-card.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { SearchFilterPipe } from 'app/foundation/pipes/search-filter.pipe';
import { AccordionGroups, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarItemShowAlways, SidebarTypes } from 'app/foundation/types/sidebar';
import { WeekGroup, WeekGroupingUtil } from 'app/foundation/util/week-grouping.util';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { Subject, takeUntil } from 'rxjs';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

// CollapseState is an intersection with Record<...> group unions, so an empty seed cannot be expressed as a type
// annotation or `satisfies`; the working copy is populated before use. Assert through a named variable so the ban on
// object-literal assertions is respected.
const emptyCollapseState: Record<string, boolean> = {};
const EMPTY_COLLAPSE_STATE = emptyCollapseState as CollapseState;

/**
 * Param key the variant-group detail route declares (`group/:groupId` in `courses.route.ts`); every other detail
 * route names a plain entity (`:exerciseId`, `:lectureId`, …). Group and exercise ids come from independent
 * sequences and overlap freely, so the key is the only thing that tells the two kinds of id apart.
 */
const VARIANT_GROUP_ROUTE_PARAM = 'groupId';

/** True for the single card standing for a variant group; only such a card carries members in `groupedItems`. */
function isVariantGroupCard(item: SidebarCardElement): boolean {
    return !!item.groupedItems?.length;
}

@Component({
    selector: 'jhi-sidebar-accordion',
    templateUrl: './sidebar-accordion.component.html',
    styleUrls: ['./sidebar-accordion.component.scss'],
    imports: [FaIconComponent, NgbCollapse, NgClass, SidebarCardDirective, TitleCasePipe, ArtemisTranslatePipe, ArtemisDatePipe, SearchFilterPipe],
})
export class SidebarAccordionComponent implements OnInit, OnDestroy {
    protected readonly Object = Object;
    private metisConversationService = inject(MetisConversationService);
    private localStorageService = inject(LocalStorageService);
    private ngUnsubscribe = new Subject<void>();

    readonly onUpdateSidebar = output<void>();
    readonly searchValue = input<string>('');
    readonly routeParams = input<Params>();
    readonly groupedData = input.required<AccordionGroups>();
    readonly sidebarType = input<SidebarTypes>();
    readonly storageId = input<string>('');
    readonly courseId = input<number>();
    readonly itemSelected = input<boolean>();
    readonly showLeadingIcon = input<boolean>(false);
    readonly channelTypeIcon = input<ChannelTypeIcons>();
    sidebarItemAlwaysShow = input.required<SidebarItemShowAlways>();
    readonly collapseState = input.required<CollapseState>();
    readonly isFilterActive = input<boolean>(false);

    /** Working copy of the collapse state. Seeded by reference from the {@link collapseState} input so in-place
     *  property mutations remain visible to the parent, but can be replaced when a stored state is restored. */
    readonly collapseStateInternal = signal<CollapseState>(EMPTY_COLLAPSE_STATE);

    /**
     * The entity the detail route currently shows: its id together with the param that named it. The param is kept
     * because the id alone does not say which kind of entity it belongs to (see {@link VARIANT_GROUP_ROUTE_PARAM}).
     */
    private readonly selectedRouteEntity = computed<{ paramKey: string; id: number } | undefined>(() => {
        const params = this.routeParams();
        const paramKey = params && Object.keys(params)[0];
        if (!paramKey) {
            return undefined;
        }
        const id = Number(params[paramKey]);
        return Number.isNaN(id) ? undefined : { paramKey, id };
    });

    /**
     * Id of the entity the detail route shows, forwarded to the cards so the group card holding it stays highlighted
     * while the member it groups has no card of its own. A group route contributes nothing: its own card is
     * highlighted by `routerLinkActive`, and forwarding the group id would instead mark whichever group happens to
     * hold a member exercise with the same id.
     */
    readonly selectedItemId = computed<number | undefined>(() => {
        const selected = this.selectedRouteEntity();
        return selected && selected.paramKey !== VARIANT_GROUP_ROUTE_PARAM ? selected.id : undefined;
    });

    /** Key of the time category holding the selected entity, if any. */
    private readonly groupKeyWithSelectedItem = computed<string | undefined>(() => {
        const selected = this.selectedRouteEntity();
        const groupedData = this.groupedData();
        if (!selected || !groupedData) {
            return undefined;
        }
        // Match only cards of the kind the route named, so an id shared by a group and an exercise cannot open the
        // wrong category. A group route matches the group card itself; every other route matches a plain card or one
        // of a group's members (the direct URL / refresh case, where a variant is open without a card).
        const matchesSelected = (item: SidebarCardElement): boolean =>
            selected.paramKey === VARIANT_GROUP_ROUTE_PARAM
                ? isVariantGroupCard(item) && item.id === selected.id
                : (!isVariantGroupCard(item) && item.id === selected.id) || !!item.groupedItems?.some((variant) => variant.id === selected.id);
        return Object.entries(groupedData).find(([, group]) => group.entityData.some(matchesSelected))?.[0];
    });

    readonly faChevronRight = faChevronRight;
    readonly faFile = faFile;
    readonly totalUnreadMessagesPerGroup = signal<{ [key: string]: number }>({});

    constructor() {
        // Seed the working collapse state from the input.
        effect(() => {
            this.collapseStateInternal.set(this.collapseState());
        });
        // Replaces ngOnChanges: react to search/filter changes. Only the trigger inputs are tracked; the body
        // reads and writes collapseStateInternal, so it must run untracked to avoid re-triggering this effect
        // (expandAll/setStoredCollapseState write a new collapse-state object on every run, which would otherwise
        // create an infinite reactive loop).
        effect(() => {
            const shouldExpandAll = !!this.searchValue() || this.isFilterActive();
            // Track the storage-key inputs too: mirroring the former ngOnChanges, a change of course/storage key must
            // reload the stored collapse state for that key (setStoredCollapseState reads them inside untracked()).
            this.courseId();
            this.storageId();
            untracked(() => {
                if (shouldExpandAll) {
                    this.expandAll();
                } else {
                    this.setStoredCollapseState();
                }
            });
        });
        // The selected entity changes after init as well: `routeParams` follows every NavigationEnd, and the grouped
        // data can arrive later than the first render. Registered last so it runs after the two effects above, both
        // of which replace the whole collapse state and would otherwise leave the opened category collapsed. Only the
        // category key is tracked, so a manual collapse survives until the route moves to a different category.
        effect(() => {
            this.groupKeyWithSelectedItem();
            untracked(() => this.expandGroupWithSelectedItem());
        });
    }

    ngOnInit() {
        this.setStoredCollapseState();
        this.metisConversationService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((c) => {
            setTimeout(() => {
                this.calculateUnreadMessagesOfGroup();
            }, 0);
        });
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe(() => {
            setTimeout(() => {
                this.calculateUnreadMessagesOfGroup();
            }, 0);
        });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    setStoredCollapseState() {
        const storedCollapseState: CollapseState | undefined = this.localStorageService.retrieve<CollapseState>(
            'sidebar.accordion.collapseState.' + this.storageId() + '.byCourse.' + this.courseId(),
        );
        if (storedCollapseState) this.collapseStateInternal.set(storedCollapseState);
    }

    expandAll() {
        const collapseState = deepClone(this.collapseStateInternal());
        Object.keys(collapseState).forEach((key) => {
            collapseState[key] = false;
        });
        this.collapseStateInternal.set(collapseState);
    }

    expandGroupWithSelectedItem() {
        const groupKey = this.groupKeyWithSelectedItem();
        if (groupKey) {
            this.collapseStateInternal.set(cloneWith(this.collapseStateInternal(), { [groupKey]: false }));
        }
    }

    private shouldCountUnreadMessages(item: SidebarCardElement): boolean {
        return !!item.conversation?.unreadMessagesCount && item.conversation?.isMuted === false;
    }

    calculateUnreadMessagesOfGroup(): void {
        const groupedData = this.groupedData();
        if (!groupedData) {
            this.totalUnreadMessagesPerGroup.set({});
            return;
        }

        const unreadMessagesPerGroup: { [key: string]: number } = {};
        Object.keys(groupedData).forEach((groupKey) => {
            unreadMessagesPerGroup[groupKey] = groupedData[groupKey].entityData
                .filter((item: SidebarCardElement) => this.shouldCountUnreadMessages(item))
                .reduce((sum, item) => sum + (item.conversation?.unreadMessagesCount || 0), 0);
        });
        this.totalUnreadMessagesPerGroup.set(unreadMessagesPerGroup);
    }

    toggleGroupCategoryCollapse(groupCategoryKey: string) {
        const collapseState = cloneWith(this.collapseStateInternal(), { [groupCategoryKey]: !this.collapseStateInternal()[groupCategoryKey] });
        this.collapseStateInternal.set(collapseState);
        this.localStorageService.store<CollapseState>('sidebar.accordion.collapseState.' + this.storageId() + '.byCourse.' + this.courseId(), collapseState);
    }

    getGroupedByWeek(groupKey: string): WeekGroup[] {
        return WeekGroupingUtil.getGroupedByWeek(this.groupedData()[groupKey].entityData, this.storageId(), groupKey, this.searchValue());
    }
}
