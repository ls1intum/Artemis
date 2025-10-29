import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LoginWithPasskeyModalComponent } from './login-with-passkey-modal.component';

describe('LoginWithPasskeyModal', () => {
    let component: LoginWithPasskeyModalComponent;
    let fixture: ComponentFixture<LoginWithPasskeyModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LoginWithPasskeyModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LoginWithPasskeyModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
