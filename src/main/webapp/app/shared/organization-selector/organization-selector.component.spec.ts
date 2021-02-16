import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OrganizationSelectorComponent } from './organization-selector.component';

describe('OrganizationSelectorComponent', () => {
    let component: OrganizationSelectorComponent;
    let fixture: ComponentFixture<OrganizationSelectorComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [OrganizationSelectorComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(OrganizationSelectorComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
