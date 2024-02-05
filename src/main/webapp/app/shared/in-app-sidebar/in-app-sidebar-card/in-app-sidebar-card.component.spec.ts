import { ComponentFixture, TestBed, async } from '@angular/core/testing';

import { InAppSidebarCardComponent } from './in-app-sidebar-card.component';

describe('InAppSidebarCardComponent', () => {
    let component: InAppSidebarCardComponent;
    let fixture: ComponentFixture<InAppSidebarCardComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [InAppSidebarCardComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(InAppSidebarCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
