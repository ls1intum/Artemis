import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OrganizationManagementDetailComponent } from './organization-management-detail.component';

describe('OrganizationManagementDetailComponent', () => {
    let component: OrganizationManagementDetailComponent;
    let fixture: ComponentFixture<OrganizationManagementDetailComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [OrganizationManagementDetailComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(OrganizationManagementDetailComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
