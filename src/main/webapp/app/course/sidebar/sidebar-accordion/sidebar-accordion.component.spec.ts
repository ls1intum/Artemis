import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SidebarAccordionComponent', () => {
    setupTestBed({ zoneless: true });
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

    it('should expand the group containing the selected item', () => {
        component.expandGroupWithSelectedItem();
        expect(component.collapseStateInternal()['future']).toBe(false);
    });

    it('should calculate unread messages of each group correctly', () => {
        expect(component.totalUnreadMessagesPerGroup['current']).toBe(2);
        expect(component.totalUnreadMessagesPerGroup['past']).toBe(5);
        expect(component.totalUnreadMessagesPerGroup['future']).toBe(0);
        expect(component.totalUnreadMessagesPerGroup['noDate']).toBe(0);
    });

    it('should use the week grouping utility for grouping items', () => {
        const result = component.getGroupedByWeek('current');
        expect(result).toBeDefined();
        expect(Array.isArray(result)).toBeTruthy();
    });
});
