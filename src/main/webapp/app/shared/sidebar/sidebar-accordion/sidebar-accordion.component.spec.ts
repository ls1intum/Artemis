import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarAccordionComponent } from 'app/shared/sidebar/sidebar-accordion/sidebar-accordion.component';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { SidebarCardDirective } from 'app/shared/sidebar/directive/sidebar-card.directive';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { NgbCollapseModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import dayjs from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';

dayjs.extend(isoWeek);

describe('SidebarAccordionComponent', () => {
    let component: SidebarAccordionComponent;
    let fixture: ComponentFixture<SidebarAccordionComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), MockModule(NgbCollapseModule), MockModule(RouterModule)],
            declarations: [
                SidebarAccordionComponent,
                SidebarCardMediumComponent,
                SidebarCardItemComponent,
                SidebarCardDirective,
                SearchFilterPipe,
                SearchFilterComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(SearchFilterComponent),
            ],
            providers: [{ provide: ActivatedRoute, useValue: new MockActivatedRoute() }],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarAccordionComponent);
        component = fixture.componentInstance;

        component.groupedData = {
            current: {
                entityData: [{ title: 'Title 1', type: 'Type A', id: 1, size: 'M', conversation: { unreadMessagesCount: 1 } }],
            },
            past: {
                entityData: [{ title: 'Title 2', type: 'Type B', id: 2, size: 'M', conversation: { unreadMessagesCount: 0 } }],
            },
            future: {
                entityData: [{ title: 'Title 3', type: 'Type C', id: 3, size: 'M', conversation: { unreadMessagesCount: 1 } }],
            },
            noDate: {
                entityData: [{ title: 'Title 4', type: 'Type D', id: 4, size: 'M', conversation: { unreadMessagesCount: 0 } }],
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
        const groupKey = 'noDate';
        component.toggleGroupCategoryCollapse(groupKey);
        expect(component.collapseState[groupKey]).toBeFalse();
        component.toggleGroupCategoryCollapse(groupKey);
        expect(component.collapseState[groupKey]).toBeTrue();
    });

    it('should toggle collapse state when group header is clicked', () => {
        const groupKey = 'current';
        const initialCollapseState = component.collapseState[groupKey];

        component.searchValue = '';
        fixture.detectChanges();

        const headerElement: DebugElement = fixture.debugElement.query(By.css('#test-accordion-item-header-' + groupKey));
        expect(headerElement).toBeTruthy();

        headerElement.triggerEventHandler('click', null);
        fixture.detectChanges();

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

        fixture.detectChanges();

        expect(component.expandAll).toHaveBeenCalledOnce();
    });

    it('should correctly call setStoredCollapseState when searchValue is cleared', () => {
        const expectedStateAfterClear = component.collapseState;
        component.searchValue = 'initial value';
        fixture.detectChanges();

        jest.spyOn(component, 'setStoredCollapseState');

        // Simulate clearing the search value
        component.searchValue = '';
        component.ngOnChanges();

        fixture.detectChanges();

        expect(component.setStoredCollapseState).toHaveBeenCalledOnce();
        expect(component.collapseState).toEqual(expectedStateAfterClear);
    });

    it('should correctly add the d-none class when searchValue is set', () => {
        component.searchValue = '3';
        component.ngOnChanges();
        fixture.detectChanges();

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
        expect(component.totalUnreadMessagesPerGroup['current']).toBe(1);
        expect(component.totalUnreadMessagesPerGroup['past']).toBe(0);
        expect(component.totalUnreadMessagesPerGroup['future']).toBe(1);
        expect(component.totalUnreadMessagesPerGroup['noDate']).toBe(0);
    });

    ['real', 'test', 'attempt'].forEach((examKey) => {
        it(`should return a single group with an empty weekRange for exam-type group key '${examKey}'`, () => {
            component.groupedData = {
                [examKey]: {
                    entityData: [
                        { title: `${examKey} Exam 1`, id: 1, exercise: { dueDate: dayjs('2025-01-01') } },
                        { title: `${examKey} Exam 2`, id: 2, exercise: { dueDate: dayjs('2025-01-02') } },
                    ],
                },
            } as any;
            component.searchValue = '';

            const result = component.getGroupedByWeek(examKey);
            expect(result).toHaveLength(1);
            expect(result[0].weekRange).toBe('');
            expect(result[0].items).toHaveLength(2);
        });
    });

    [
        { groupKey: 'smallGroup', searchValue: '', expectedCount: 2 },
        { groupKey: 'filteredGroup', searchValue: 'Alpha', expectedCount: 1 },
    ].forEach(({ groupKey, searchValue, expectedCount }) => {
        it(`should return a single group for '${groupKey}' when searchValue is '${searchValue}'`, () => {
            let entityData;
            if (groupKey === 'smallGroup') {
                entityData = [
                    { title: 'Small 1', id: 1, exercise: { dueDate: dayjs('2025-01-03') } },
                    { title: 'Small 2', id: 2, exercise: { dueDate: dayjs('2025-01-04') } },
                ];
            } else if (groupKey === 'filteredGroup') {
                entityData = [
                    { title: 'Alpha', id: 1, exercise: { dueDate: dayjs('2025-02-01') } },
                    { title: 'Beta', id: 2, exercise: { dueDate: dayjs('2025-02-02') } },
                ];
            }
            component.groupedData = { [groupKey]: { entityData } } as any;
            component.searchValue = searchValue;
            const result = component.getGroupedByWeek(groupKey);
            expect(result).toHaveLength(1);
            expect(result[0].weekRange).toBe('');
            expect(result[0].items).toHaveLength(expectedCount);
            if (searchValue === 'Alpha') {
                expect(result[0].items[0].title).toBe('Alpha');
            }
        });
    });

    it('should group items by week if there are more than 5 items and no search filter', () => {
        component.groupedData = {
            bigGroup: {
                entityData: [
                    { title: 'Item 1', id: 1, exercise: { dueDate: dayjs('2025-01-05') } },
                    { title: 'Item 2', id: 2, exercise: { dueDate: dayjs('2025-01-06') } },
                    { title: 'Item 3', id: 3, exercise: { dueDate: dayjs('2025-01-07') } },
                    { title: 'Item 4', id: 4, exercise: { dueDate: dayjs('2025-01-08') } },
                    { title: 'Item 5', id: 5, exercise: { dueDate: dayjs('2025-01-09') } },
                    { title: 'Item 6', id: 6, exercise: { dueDate: dayjs('2025-01-15') } },
                ],
            },
        } as any;
        component.searchValue = '';
        const result = component.getGroupedByWeek('bigGroup');
        expect(result.length).toBeGreaterThan(1);
    });

    it('should place an item in a correct week if it only has startDateWithTime', () => {
        component.groupedData = {
            startDateGroup: {
                entityData: [{ title: 'StartDate Only', id: 1, startDateWithTime: dayjs('2025-03-10') }],
            },
        } as any;
        component.searchValue = '';
        const result = component.getGroupedByWeek('startDateGroup');
        expect(result).toHaveLength(1);
        expect(result[0].items).toHaveLength(1);
        expect(result[0].items[0].title).toBe('StartDate Only');
    });

    it('should place items without date in the "No Date" group when there are more than 5 items', () => {
        component.groupedData = {
            past: {
                entityData: [
                    { title: 'No Date 1', id: 1 },
                    { title: 'No Date 2', id: 2 },
                    { title: 'No Date 3', id: 3 },
                    { title: 'No Date 4', id: 4 },
                    { title: 'No Date 5', id: 5 },
                    { title: 'No Date 6', id: 6 },
                ],
            },
        } as any;
        component.searchValue = '';
        const result = component.getGroupedByWeek('past');
        expect(result).toHaveLength(1);
        expect(result[0].weekRange).toBe('No Date');
        expect(result[0].items).toHaveLength(6);
    });
});
