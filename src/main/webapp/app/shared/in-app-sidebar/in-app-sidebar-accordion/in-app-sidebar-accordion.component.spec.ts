import { ComponentFixture, TestBed, async } from '@angular/core/testing';

import { InAppSidebarAccordionComponent } from './in-app-sidebar-accordion.component';

describe('InAppSidebarAccordionComponent', () => {
    let component: InAppSidebarAccordionComponent;
    let fixture: ComponentFixture<InAppSidebarAccordionComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [InAppSidebarAccordionComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(InAppSidebarAccordionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
