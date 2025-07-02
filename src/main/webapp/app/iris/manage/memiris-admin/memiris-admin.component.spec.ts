import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MemirisAdminComponent } from './memiris-admin.component';

describe('MemirisAdminComponent', () => {
    let component: MemirisAdminComponent;
    let fixture: ComponentFixture<MemirisAdminComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MemirisAdminComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(MemirisAdminComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
