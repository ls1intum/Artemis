import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, SetupPasskeyModalComponent } from './setup-passkey-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';

describe('SetupPasskeyModalComponent', () => {
    let component: SetupPasskeyModalComponent;
    let fixture: ComponentFixture<SetupPasskeyModalComponent>;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SetupPasskeyModalComponent],
            declarations: [MockDirective(TranslateDirective)],
            providers: [
                MockProvider(NgbActiveModal),
                MockProvider(AlertService),
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AccountService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(SetupPasskeyModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        fixture.detectChanges();
    });

    it('should close the modal', () => {
        const closeModalSpy = jest.spyOn(activeModal, 'close');

        component.closeModal();

        expect(closeModalSpy).toHaveBeenCalled();
    });

    it('should set reminder date in localStorage and close the modal', () => {
        const localStorageSpy = jest.spyOn(localStorage, 'setItem');
        const closeModalSpy = jest.spyOn(activeModal, 'close');

        const expectedDateOnlyWithDayToEnsureTestIsNotFlaky = new Date();
        expectedDateOnlyWithDayToEnsureTestIsNotFlaky.setDate(expectedDateOnlyWithDayToEnsureTestIsNotFlaky.getDate() + 30);
        expectedDateOnlyWithDayToEnsureTestIsNotFlaky.setHours(0, 0, 0, 0);

        component.remindMeIn30Days();

        const savedDate = new Date(localStorageSpy.mock.calls[0][1]);

        const savedDateOnlyWithDay = new Date(savedDate);
        savedDateOnlyWithDay.setHours(0, 0, 0, 0);
        expect(savedDateOnlyWithDay.getTime()).toBe(expectedDateOnlyWithDayToEnsureTestIsNotFlaky.getTime());

        expect(localStorageSpy).toHaveBeenCalledWith(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, savedDate.toISOString());
        expect(closeModalSpy).toHaveBeenCalled();
    });
});
