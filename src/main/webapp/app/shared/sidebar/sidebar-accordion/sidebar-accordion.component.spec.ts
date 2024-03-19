import { ComponentFixture, TestBed, async } from '@angular/core/testing';

import { SidebarAccordionComponent } from './sidebar-accordion.component';

describe('SidebarAccordionComponent', () => {
    let component: SidebarAccordionComponent;
    let fixture: ComponentFixture<SidebarAccordionComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [SidebarAccordionComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarAccordionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
