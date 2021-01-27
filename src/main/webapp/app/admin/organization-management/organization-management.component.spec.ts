import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OrganizationManagementComponent } from './organization-management.component';

describe('OrganizationManagementComponent', () => {
    let component: OrganizationManagementComponent;
    let fixture: ComponentFixture<OrganizationManagementComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [OrganizationManagementComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(OrganizationManagementComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
