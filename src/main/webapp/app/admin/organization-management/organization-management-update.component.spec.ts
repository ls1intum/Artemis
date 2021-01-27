import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OrganizationManagementUpdateComponent } from './organization-management-update.component';

describe('OrganizationManagementUpdateComponent', () => {
    let component: OrganizationManagementUpdateComponent;
    let fixture: ComponentFixture<OrganizationManagementUpdateComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [OrganizationManagementUpdateComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(OrganizationManagementUpdateComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
