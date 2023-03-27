import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PrivacyStatementUpdateComponent } from 'app/admin/privacy-statement/privacy-statement-update/privacy-statement-update.component';

describe('PrivacyStatementUpdateComponent', () => {
    let component: PrivacyStatementUpdateComponent;
    let fixture: ComponentFixture<PrivacyStatementUpdateComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [PrivacyStatementUpdateComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(PrivacyStatementUpdateComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
