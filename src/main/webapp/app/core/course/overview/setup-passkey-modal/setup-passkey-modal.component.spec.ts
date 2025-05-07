import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, SetupPasskeyModalComponent } from './setup-passkey-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';

describe('SetupPasskeyModalComponent', () => {
    let component: SetupPasskeyModalComponent;
    let fixture: ComponentFixture<SetupPasskeyModalComponent>;
    let router: Router;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SetupPasskeyModalComponent],
            declarations: [MockDirective(TranslateDirective)],
            providers: [
                MockProvider(NgbActiveModal),
                {
                    provide: Router,
                    useValue: { navigateByUrl: jest.fn() },
                },
                MockProvider(AlertService),
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AccountService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(SetupPasskeyModalComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        activeModal = TestBed.inject(NgbActiveModal);
        fixture.detectChanges();
    });

    it('should navigate to setup passkey and close modal', () => {
        const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');
        const closeModalSpy = jest.spyOn(activeModal, 'close');

        component.navigateToSetupPasskey();

        expect(closeModalSpy).toHaveBeenCalled();
        expect(navigateByUrlSpy).toHaveBeenCalledWith('/user-settings/passkeys');
    });

    it('should close the modal', () => {
        const closeModalSpy = jest.spyOn(activeModal, 'close');

        component.closeModal();

        expect(closeModalSpy).toHaveBeenCalled();
    });

    it('should set reminder date in localStorage and close the modal', () => {
        const localStorageSpy = jest.spyOn(localStorage, 'setItem');
        const closeModalSpy = jest.spyOn(activeModal, 'close');

        component.remindMeIn30Days();

        const expectedDate = new Date();
        expectedDate.setDate(expectedDate.getDate() + 30);

        expect(localStorageSpy).toHaveBeenCalledWith(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, expectedDate.toISOString());
        expect(closeModalSpy).toHaveBeenCalled();
    });
});
