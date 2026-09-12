import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarAccordionComponent } from 'app/course/sidebar/sidebar-accordion/sidebar-accordion.component';
import { SidebarCardMediumComponent } from 'app/course/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardItemComponent } from 'app/course/sidebar/sidebar-card-item/sidebar-card-item.component';
import { SidebarCardDirective } from 'app/course/sidebar/directive/sidebar-card.directive';
import { SearchFilterPipe } from 'app/foundation/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { NgbCollapseModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { CollapseState } from 'app/foundation/types/sidebar';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SidebarAccordionComponent', () => {
    let component: SidebarAccordionComponent;
    let localStorageService: LocalStorageService;
    let fixture: ComponentFixture<SidebarAccordionComponent>;

    beforeEach(async () => {
        // The real LocalStorageService persists across tests in the same worker. Clear it so a collapse state
        // stored by one test (e.g. the toggle test) does not leak into setStoredCollapseState() of the next.
        localStorage.clear();
        await TestBed.configureTestingModule({
            imports: [
                MockModule(NgbTooltipModule),
                MockModule(NgbCollapseModule),
                MockModule(RouterModule),
                FaIconComponent,
                SidebarAccordionComponent,
                SidebarCardMediumComponent,
                SidebarCardItemComponent,
                SidebarCardDirective,
                SearchFilterPipe,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(SearchFilterComponent),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(SidebarAccordionComponent);
        component = fixture.componentInstance;
        localStorageService = TestBed.inject(LocalStorageService);

        fixture.componentRef.setInput('groupedData', {
            current: {
                entityData: [{ title: 'Title 1', type: 'Type A', id: 1, size: 'M', conversation: { unreadMessagesCount: 2, isMuted: false } }],
            },
            past: {
                entityData: [{ title: 'Title 2', type: 'Type B', id: 2, size: 'M', conversation: { unreadMessagesCount: 5, isMuted: false } }],
            },
            future: {
                entityData: [{ title: 'Title 3', type: 'Type C', id: 3, size: 'M', conversation: { unreadMessagesCount: 4, isMuted: true } }],
            },
            noDate: {
                entityData: [{ title: 'Title 4', type: 'Type D', id: 4, size: 'M', conversation: { unreadMessagesCount: 3, isMuted: true } }],
            },
        });
        fixture.componentRef.setInput('routeParams', { exerciseId: 3 });
        fixture.componentRef.setInput('collapseState', { current: false, dueSoon: false, past: false, future: true, noDate: true });
        fixture.componentRef.setInput('sidebarItemAlwaysShow', { current: false, dueSoon: false, past: false, future: false, noDate: false });
        fixture.detectChanges();
        component.calculateUnreadMessagesOfGroup();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should toggle collapse state for a group', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');
        const storageKey = `sidebar.accordion.collapseState.${component.storageId()}.byCourse.${component.courseId()}`;
        const groupKey = 'noDate';

        component.toggleGroupCategoryCollapse(groupKey);
        expect(storeSpy).toHaveBeenCalledWith(storageKey, expect.objectContaining({ [groupKey]: false }));
        expect(component.collapseStateInternal()[groupKey]).toBe(false);

        component.toggleGroupCategoryCollapse(groupKey);
        expect(component.collapseStateInternal()[groupKey]).toBe(true);
        expect(storeSpy).toHaveBeenCalledWith(storageKey, expect.objectContaining({ [groupKey]: true }));
    });

    it('should toggle collapse state when group header is clicked', () => {
        const groupKey = 'current';
        const initialCollapseState = component.collapseStateInternal()[groupKey];

        fixture.componentRef.setInput('searchValue', '');
        fixture.changeDetectorRef.detectChanges();

        const headerElement: DebugElement = fixture.debugElement.query(By.css('#test-accordion-item-header-' + groupKey));
        expect(headerElement).toBeTruthy();

        headerElement.triggerEventHandler('click', null);
        fixture.changeDetectorRef.detectChanges();

        expect(component.collapseStateInternal()[groupKey]).toBe(!initialCollapseState);
    });

    it('should call expandAll when searchValue changes to a non-empty string', () => {
        vi.spyOn(component, 'expandAll');

        fixture.componentRef.setInput('searchValue', 'test');
        fixture.changeDetectorRef.detectChanges();

        expect(component.expandAll).toHaveBeenCalledOnce();
    });

    it('should call expandAll when filter is active', () => {
        vi.spyOn(component, 'expandAll');

        fixture.componentRef.setInput('isFilterActive', true);
        fixture.changeDetectorRef.detectChanges();

        expect(component.expandAll).toHaveBeenCalledOnce();
    });

    it('should correctly call setStoredCollapseState when searchValue is cleared', () => {
        fixture.componentRef.setInput('searchValue', 'initial value');
        fixture.changeDetectorRef.detectChanges();
        // Capture the collapse state after the search was applied; clearing the search must not clobber it
        // when there is no persisted state to restore.
        const expectedStateAfterClear = { ...component.collapseStateInternal() };

        vi.spyOn(component, 'setStoredCollapseState');

        // Simulate clearing the search value
        fixture.componentRef.setInput('searchValue', '');
        fixture.changeDetectorRef.detectChanges();

        expect(component.setStoredCollapseState).toHaveBeenCalledOnce();
        expect(component.collapseStateInternal()).toEqual(expectedStateAfterClear);
    });

    it('should reload the stored collapse state when the course key changes', () => {
        fixture.changeDetectorRef.detectChanges();
        // Persist a collapse state for a different course key, then switch to it: the effect must reload it
        // (mirrors the former ngOnChanges, which reacted to courseId/storageId changes too).
        const newCourseId = 999;
        const storedState = { current: true, past: true, future: true, noDate: true } as CollapseState;
        localStorageService.store(`sidebar.accordion.collapseState.${component.storageId()}.byCourse.${newCourseId}`, storedState);
        const setStoredSpy = vi.spyOn(component, 'setStoredCollapseState');

        fixture.componentRef.setInput('courseId', newCourseId);
        fixture.changeDetectorRef.detectChanges();

        expect(setStoredSpy).toHaveBeenCalled();
        expect(component.collapseStateInternal()).toEqual(storedState);
    });

    it('should correctly add the d-none class when searchValue is set', () => {
        fixture.componentRef.setInput('searchValue', '3');
        fixture.changeDetectorRef.detectChanges();

        const displayedDivIndex = 2;
        const elementIdDisplayedDiv = `#test-accordion-item-container-${displayedDivIndex}`;
        const itemDisplayedDiv: HTMLElement = fixture.nativeElement.querySelector(elementIdDisplayedDiv);

        expect(itemDisplayedDiv).toBeTruthy();
        expect(itemDisplayedDiv.classList.contains('d-none')).toBe(false);

        const elementIdHiddenDiv = `#test-accordion-item-container-0`;
        const itemHiddenDiv: HTMLElement = fixture.nativeElement.querySelector(elementIdHiddenDiv);

        expect(itemHiddenDiv).toBeNull();
    });

    describe('searching a variant group', () => {
        // A variant-group card carries the group title and its members in groupedItems, but no type of its own,
        // so searching for a member's title or type must still keep the whole group visible.
        beforeEach(() => {
            fixture.componentRef.setInput('groupedData', {
                current: {
                    entityData: [
                        {
                            title: 'Variant group',
                            id: 10,
                            size: 'M',
                            groupedItems: [
                                { title: 'Sorting algorithms', id: 11, size: 'M', type: 'programming' },
                                { title: 'Binary trees', id: 12, size: 'M', type: 'modeling' },
                            ],
                        },
                    ],
                },
            });
        });

        const groupIsVisible = (searchValue: string): boolean => {
            fixture.componentRef.setInput('searchValue', searchValue);
            fixture.changeDetectorRef.detectChanges();
            return !!fixture.nativeElement.querySelector('#test-accordion-item-container-0')?.querySelector('#test-sidebar-card-medium');
        };

        it('should keep the group when the search matches a member title', () => {
            expect(groupIsVisible('Binary')).toBe(true);
        });

        it('should keep the group when the search matches a member type', () => {
            expect(groupIsVisible('programming')).toBe(true);
        });

        it('should keep the group when the search matches the group title', () => {
            expect(groupIsVisible('Variant')).toBe(true);
        });

        it('should hide the group when the search matches neither the group nor a member', () => {
            expect(groupIsVisible('quiz')).toBe(false);
        });

        it('should highlight the group card while the detail route shows one of its members', () => {
            // The member has no card of its own, so the group card must carry the selection.
            fixture.componentRef.setInput('routeParams', { exerciseId: 12 });
            fixture.changeDetectorRef.detectChanges();

            const card: HTMLElement = fixture.nativeElement.querySelector('#test-accordion-item-container-0 #test-sidebar-card-medium');
            expect(card.className).toContain('bg-group-selected');
        });

        it('should not highlight the group card for an unrelated selected item', () => {
            fixture.componentRef.setInput('routeParams', { exerciseId: 999 });
            fixture.changeDetectorRef.detectChanges();

            const card: HTMLElement = fixture.nativeElement.querySelector('#test-accordion-item-container-0 #test-sidebar-card-medium');
            expect(card.className).not.toContain('bg-group-selected');
        });

        it('should render the group as a single card without a card per member', () => {
            fixture.changeDetectorRef.detectChanges();
            const cards = fixture.nativeElement.querySelector('#test-accordion-item-container-0').querySelectorAll('#test-sidebar-card-medium');
            expect(cards).toHaveLength(1);
        });
    });

    it('should expand the group containing the selected item', () => {
        // 'future' starts collapsed and holds the item the route shows, so the initial render must open it.
        expect(component.collapseStateInternal()['future']).toBe(false);
    });

    it('should expand the group containing the selected item when the route changes after init', () => {
        // routeParams follows every NavigationEnd, so selecting an item in a collapsed category must open it.
        expect(component.collapseStateInternal()['noDate']).toBe(true);

        fixture.componentRef.setInput('routeParams', { exerciseId: 4 });
        fixture.changeDetectorRef.detectChanges();

        expect(component.collapseStateInternal()['noDate']).toBe(false);
    });

    it('should keep a category the user collapsed while the selected item does not change', () => {
        // Only the category key is tracked, so a sidebar refresh handing over fresh grouped data with the same
        // selection must not re-expand a category the user just collapsed.
        component.toggleGroupCategoryCollapse('future');
        expect(component.collapseStateInternal()['future']).toBe(true);

        fixture.componentRef.setInput('groupedData', {
            future: { entityData: [{ title: 'Title 3', type: 'Type C', id: 3, size: 'M' }] },
        });
        fixture.changeDetectorRef.detectChanges();

        expect(component.collapseStateInternal()['future']).toBe(true);
    });

    it('should expand the group when the selected item is a nested variant of a group', () => {
        // A variant group is one top-level card whose members live in groupedItems. Selecting a variant via a
        // direct URL / refresh must still expand its (collapsed) time category so the selected card is visible.
        fixture.componentRef.setInput('groupedData', {
            future: {
                entityData: [
                    {
                        title: 'Variant group',
                        id: 10,
                        size: 'M',
                        groupedItems: [
                            { title: 'Sorting algorithms', id: 11, size: 'M', type: 'programming' },
                            { title: 'Binary trees', id: 12, size: 'M', type: 'modeling' },
                        ],
                    },
                ],
            },
        });
        fixture.componentRef.setInput('routeParams', { exerciseId: 12 });
        fixture.componentRef.setInput('collapseState', { future: true });
        fixture.detectChanges();

        component.expandGroupWithSelectedItem();
        expect(component.collapseStateInternal()['future']).toBe(false);
    });

    it('should leave the group collapsed when no card or nested variant matches the selected item', () => {
        fixture.componentRef.setInput('groupedData', {
            future: {
                entityData: [
                    {
                        title: 'Variant group',
                        id: 10,
                        size: 'M',
                        groupedItems: [{ title: 'Sorting algorithms', id: 11, size: 'M', type: 'programming' }],
                    },
                ],
            },
        });
        fixture.componentRef.setInput('routeParams', { exerciseId: 999 });
        fixture.componentRef.setInput('collapseState', { future: true });
        fixture.detectChanges();

        component.expandGroupWithSelectedItem();
        expect(component.collapseStateInternal()['future']).toBe(true);
    });

    describe('colliding group and exercise ids', () => {
        // Group and exercise ids come from independent sequences, so the same number can name both a variant group
        // and an unrelated exercise. The route param says which of the two the open detail page shows.
        const groupedDataWithCollision = {
            current: {
                entityData: [
                    {
                        title: 'Variant group',
                        id: 10,
                        size: 'M',
                        // A member of a *different* group carries the id that also names the group below.
                        groupedItems: [{ title: 'Sorting algorithms', id: 20, size: 'M', type: 'programming' }],
                    },
                ],
            },
            future: {
                entityData: [
                    {
                        title: 'Other variant group',
                        id: 20,
                        size: 'M',
                        groupedItems: [{ title: 'Binary trees', id: 21, size: 'M', type: 'modeling' }],
                    },
                ],
            },
            noDate: {
                entityData: [{ title: 'Standalone exercise', id: 10, size: 'M', type: 'text' }],
            },
        };

        beforeEach(() => {
            fixture.componentRef.setInput('groupedData', groupedDataWithCollision);
            fixture.componentRef.setInput('collapseState', { current: true, future: true, noDate: true });
            // Clear the outer selection first: only a *change* of the category holding the selection re-expands it,
            // so a test whose route happens to land in the same category as the outer one would see no effect run.
            fixture.componentRef.setInput('routeParams', {});
            fixture.detectChanges();
            expect(component.collapseStateInternal()).toEqual({ current: true, future: true, noDate: true });
        });

        it('should expand the group card category for a group route, not the exercise sharing its id', () => {
            fixture.componentRef.setInput('routeParams', { groupId: 10 });
            fixture.detectChanges();

            expect(component.collapseStateInternal()['current']).toBe(false);
            expect(component.collapseStateInternal()['noDate']).toBe(true);
        });

        it('should expand the exercise category for an exercise route, not the group sharing its id', () => {
            fixture.componentRef.setInput('routeParams', { exerciseId: 10 });
            fixture.detectChanges();

            expect(component.collapseStateInternal()['noDate']).toBe(false);
            expect(component.collapseStateInternal()['current']).toBe(true);
        });

        it('should not expand a group whose member id collides with the selected group id', () => {
            // 'current' holds a member with id 20, which is also the id of the group in 'future'.
            fixture.componentRef.setInput('routeParams', { groupId: 20 });
            fixture.detectChanges();

            expect(component.collapseStateInternal()['future']).toBe(false);
            expect(component.collapseStateInternal()['current']).toBe(true);
        });

        it('should not forward the group id to the cards, so no group is marked as holding the open variant', () => {
            // `routerLinkActive` already highlights the open group's own card; forwarding the id would additionally
            // mark the group whose member happens to carry it.
            fixture.componentRef.setInput('routeParams', { groupId: 20 });
            fixture.detectChanges();

            expect(component.selectedItemId()).toBeUndefined();
            const cards: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('#test-sidebar-card-medium'));
            expect(cards.every((card) => !card.className.includes('bg-group-selected'))).toBe(true);
        });

        it('should forward an exercise id so the group holding that member is marked', () => {
            fixture.componentRef.setInput('routeParams', { exerciseId: 20 });
            fixture.detectChanges();

            expect(component.selectedItemId()).toBe(20);
            const card: HTMLElement = fixture.nativeElement.querySelector('#test-accordion-item-container-0 #test-sidebar-card-medium');
            expect(card.className).toContain('bg-group-selected');
        });
    });

    it('should calculate unread messages of each group correctly', () => {
        expect(component.totalUnreadMessagesPerGroup()['current']).toBe(2);
        expect(component.totalUnreadMessagesPerGroup()['past']).toBe(5);
        expect(component.totalUnreadMessagesPerGroup()['future']).toBe(0);
        expect(component.totalUnreadMessagesPerGroup()['noDate']).toBe(0);
    });

    it('should use the week grouping utility for grouping items', () => {
        const result = component.getGroupedByWeek('current');
        expect(result).toBeDefined();
        expect(Array.isArray(result)).toBeTruthy();
    });
});
