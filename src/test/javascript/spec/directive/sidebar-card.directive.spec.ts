import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, ViewChild } from '@angular/core';
import { MockComponent } from 'ng-mocks';
import { SidebarCardDirective } from 'app/shared/sidebar/sidebar-card.directive';
import { ConversationSelectionSidebarComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-selection-sidebar.component';
import { ConversationHeaderComponent } from 'app/overview/course-conversations/layout/conversation-header/conversation-header.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';

@Component({
    template: `<div jhiSidebarCard [size]="size" [itemSelected]="false"></div>`,
})
class TestHostComponent {
    @ViewChild(SidebarCardDirective) directive: SidebarCardDirective;
    size: string;
    sidebarItem: SidebarCardElement;
    sidebarType?: SidebarTypes;
    itemSelected?: boolean;
}

describe('SidebarCardDirective', () => {
    let component: TestHostComponent;
    let fixture: ComponentFixture<TestHostComponent>;
    //   let hostElement: DebugElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                TestHostComponent,
                MockComponent(ConversationSelectionSidebarComponent),
                MockComponent(ConversationHeaderComponent),
                MockComponent(DocumentationButtonComponent),
                SidebarCardDirective,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TestHostComponent);
        component = fixture.componentInstance;
        //        hostElement = fixture.debugElement.query(By.directive(SidebarCardDirective));
        fixture.detectChanges();
    });

    it('should create an instance', () => {
        expect(component.directive).toBeDefined();
    });
});
