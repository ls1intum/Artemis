import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SetupPasskeyModalComponent } from './setup-passkey-modal.component';

describe('SetupPasskeyModalComponent', () => {
    let component: SetupPasskeyModalComponent;
    let fixture: ComponentFixture<SetupPasskeyModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SetupPasskeyModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(SetupPasskeyModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
