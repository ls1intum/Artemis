import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LoginWithPasskeyModal } from './login-with-passkey.modal';

describe('LoginWithPasskeyModal', () => {
    let component: LoginWithPasskeyModal;
    let fixture: ComponentFixture<LoginWithPasskeyModal>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LoginWithPasskeyModal],
        }).compileComponents();

        fixture = TestBed.createComponent(LoginWithPasskeyModal);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
