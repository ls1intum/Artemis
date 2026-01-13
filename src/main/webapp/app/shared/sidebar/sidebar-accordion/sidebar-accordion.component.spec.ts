import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarAccordionComponent } from 'app/shared/sidebar/sidebar-accordion/sidebar-accordion.component';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { SidebarCardDirective } from 'app/shared/sidebar/directive/sidebar-card.directive';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { NgbCollapseModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

describe('SidebarAccordionComponent', () => {
    let component: SidebarAccordionComponent;
    let localStorageService: LocalStorageService;
    let fixture: ComponentFixture<SidebarAccordionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), MockModule(NgbCollapseModule), MockModule(RouterModule), FaIconComponent],
            declarations: [
                SidebarAccordionComponent,
                SidebarCardMediumComponent,
                SidebarCardItemComponent,
                SidebarCardDirective,
                SearchFilterPipe,
                SearchFilterComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(SearchFilterComponent),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(SidebarAccordionComponent);
        component = fixture.componentInstance;
        localStorageService = TestBed.inject(LocalStorageService);

        component.groupedData = {
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
        };
        component.routeParams = { exerciseId: 3 };
        component.collapseState = { current: false, dueSoon: false, past: false, future: true, noDate: true };
        fixture.componentRef.setInput('sidebarItemAlwaysShow', { current: false, dueSoon: false, past: false, future: false, noDate: false });
        fixture.detectChanges();
        component.calculateUnreadMessagesOfGroup();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should toggle collapse state for a group', () => {
        const storeSpy = jest.spyOn(localStorageService, 'store');
        const storageKey = `sidebar.accordion.collapseState.${component.storageId}.byCourse.${component.courseId}`;
        const groupKey = 'noDate';

        component.toggleGroupCategoryCollapse(groupKey);
        expect(storeSpy).toHaveBeenCalledWith(storageKey, expect.objectContaining({ [groupKey]: false }));
        expect(component.collapseState[groupKey]).toBeFalse();

        component.toggleGroupCategoryCollapse(groupKey);
        expect(component.collapseState[groupKey]).toBeTrue();
        expect(storeSpy).toHaveBeenCalledWith(storageKey, expect.objectContaining({ [groupKey]: true }));
    });

    it('should toggle collapse state when group header is clicked', () => {
        const groupKey = 'current';
        const initialCollapseState = component.collapseState[groupKey];

        component.searchValue = '';
        fixture.changeDetectorRef.detectChanges();

        const headerElement: DebugElement = fixture.debugElement.query(By.css('#test-accordion-item-header-' + groupKey));
        expect(headerElement).toBeTruthy();

        headerElement.triggerEventHandler('click', null);
        fixture.changeDetectorRef.detectChanges();

        expect(component.collapseState[groupKey]).toBe(!initialCollapseState);
    });

    it('should call expandAll when searchValue changes to a non-empty string', () => {
        jest.spyOn(component, 'expandAll');

        component.searchValue = 'test';
        component.ngOnChanges();

        expect(component.expandAll).toHaveBeenCalledOnce();
    });

    it('should call expandAll when filter is active', () => {
        jest.spyOn(component, 'expandAll');

        component.isFilterActive = true;
        component.ngOnChanges();

        fixture.changeDetectorRef.detectChanges();

        expect(component.expandAll).toHaveBeenCalledOnce();
    });

    it('should correctly call setStoredCollapseState when searchValue is cleared', () => {
        const expectedStateAfterClear = component.collapseState;
        component.searchValue = 'initial value';
        fixture.changeDetectorRef.detectChanges();

        jest.spyOn(component, 'setStoredCollapseState');

        // Simulate clearing the search value
        component.searchValue = '';
        component.ngOnChanges();

        fixture.changeDetectorRef.detectChanges();

        expect(component.setStoredCollapseState).toHaveBeenCalledOnce();
        expect(component.collapseState).toEqual(expectedStateAfterClear);
    });

    it('should correctly add the d-none class when searchValue is set', () => {
        component.searchValue = '3';
        component.ngOnChanges();
        fixture.changeDetectorRef.detectChanges();

        const displayedDivIndex = 2;
        const elementIdDisplayedDiv = `#test-accordion-item-container-${displayedDivIndex}`;
        const itemDisplayedDiv: HTMLElement = fixture.nativeElement.querySelector(elementIdDisplayedDiv);

        expect(itemDisplayedDiv).toBeTruthy();
        expect(itemDisplayedDiv.classList.contains('d-none')).toBeFalse();

        const elementIdHiddenDiv = `#test-accordion-item-container-0`;
        const itemHiddenDiv: HTMLElement = fixture.nativeElement.querySelector(elementIdHiddenDiv);

        expect(itemHiddenDiv).toBeNull();
    });

    it('should expand the group containing the selected item', () => {
        component.expandGroupWithSelectedItem();
        expect(component.collapseState['future']).toBeFalse();
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
