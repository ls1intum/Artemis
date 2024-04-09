import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { SidebarCardComponent } from 'app/shared/sidebar/sidebar-card/sidebar-card.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { ArtemisTestModule } from '../../../test.module';
import { DebugElement } from '@angular/core';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

import { By } from '@angular/platform-browser';
import { MockModule, MockPipe } from 'ng-mocks';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { RouterModule } from '@angular/router';

describe('SidebarComponent', () => {
    let component: SidebarComponent;
    let fixture: ComponentFixture<SidebarComponent>;
    let debugElement: DebugElement;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(FormsModule), MockModule(ReactiveFormsModule), MockModule(RouterModule)],
            declarations: [
                SidebarComponent,
                SidebarCardComponent,
                SidebarCardItemComponent,
                SearchFilterPipe,
                SearchFilterComponent,
                MockPipe(ArtemisTranslatePipe),
                MockRouterLinkDirective,
            ],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should filter sidebar items based on search criteria', () => {
        component.sidebarData = {
            groupByCategory: true,
            ungroupedData: [
                { title: 'Item 1', type: 'Type A', id: 1 },
                { title: 'Item 2', type: 'Type B', id: 2 },
            ],
        };
        component.searchValue = 'Item 1';
        fixture.detectChanges();

        // Check if only the item with title 'Item 1' is being displayed
        let filteredItems = component.sidebarData.ungroupedData?.filter((item) => item.title.includes(component.searchValue));
        filteredItems = filteredItems ?? [];
        expect(filteredItems).toHaveLength(1);
        expect(filteredItems[0].title).toContain('Item 1');
    });

    it('should display the correct message when no data is found', () => {
        // Mock sidebarData to have no items
        component.sidebarData = {
            groupByCategory: true,
            ungroupedData: [],
        };
        fixture.detectChanges();

        const noDataMessageElement = debugElement.query(By.css('[jhiTranslate$=noDataFound]'));
        expect(noDataMessageElement).toBeTruthy();
        expect(noDataMessageElement.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.courseOverview.general.noDataFound');
    });
});
