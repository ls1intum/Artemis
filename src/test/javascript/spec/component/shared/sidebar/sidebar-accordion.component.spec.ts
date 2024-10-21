import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { SidebarAccordionComponent } from 'app/shared/sidebar/sidebar-accordion/sidebar-accordion.component';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { SidebarCardDirective } from 'app/shared/sidebar/sidebar-card.directive';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { NgbCollapseModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { RouterModule } from '@angular/router';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';

describe('SidebarAccordionComponent', () => {
    let component: SidebarAccordionComponent;
    let fixture: ComponentFixture<SidebarAccordionComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgbTooltipModule), MockModule(NgbCollapseModule), MockModule(RouterModule)],
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
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarAccordionComponent);
        component = fixture.componentInstance;

        component.groupedData = {
            current: {
                entityData: [{ title: 'Title 1', type: 'Type A', id: 1, size: 'M' }],
            },
            past: {
                entityData: [{ title: 'Title 2', type: 'Type B', id: 2, size: 'M' }],
            },
            future: {
                entityData: [{ title: 'Title 3', type: 'Type C', id: 3, size: 'M' }],
            },
            noDate: {
                entityData: [{ title: 'Title 4', type: 'Type D', id: 4, size: 'M' }],
            },
        };
        component.routeParams = { exerciseId: 3 };
        component.collapseState = { current: false, dueSoon: false, past: false, future: true, noDate: true };
        fixture.componentRef.setInput('sidebarItemAlwaysShow', { current: false, dueSoon: false, past: false, future: false, noDate: false });
        fixture.detectChanges();
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
});
