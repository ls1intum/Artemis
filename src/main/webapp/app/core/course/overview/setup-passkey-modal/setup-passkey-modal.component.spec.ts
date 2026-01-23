import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, SetupPasskeyModalComponent } from './setup-passkey-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SetupPasskeyModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: SetupPasskeyModalComponent;
    let fixture: ComponentFixture<SetupPasskeyModalComponent>;
    let activeModal: NgbActiveModal;
    let localStorageService: LocalStorageService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SetupPasskeyModalComponent, MockDirective(TranslateDirective)],
            providers: [
                MockProvider(NgbActiveModal),
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AccountService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(SetupPasskeyModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        localStorageService = TestBed.inject(LocalStorageService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should close the modal', () => {
        const closeModalSpy = vi.spyOn(activeModal, 'close');

        component.closeModal();

        expect(closeModalSpy).toHaveBeenCalled();
    });

    it('should set reminder date in localStorage and close the modal', () => {
        const localStorageServiceSpy = vi.spyOn(localStorageService, 'store');
        const closeModalSpy = vi.spyOn(activeModal, 'close');

        const expectedDateOnlyWithDayToEnsureTestIsNotFlaky = new Date();
        expectedDateOnlyWithDayToEnsureTestIsNotFlaky.setDate(expectedDateOnlyWithDayToEnsureTestIsNotFlaky.getDate() + 30);
        expectedDateOnlyWithDayToEnsureTestIsNotFlaky.setHours(0, 0, 0, 0);

        component.remindMeIn30Days();

        const savedDate = localStorageServiceSpy.mock.calls[0][1] as Date;

        const savedDateOnlyWithDay = savedDate;
        savedDateOnlyWithDay.setHours(0, 0, 0, 0);
        expect(savedDateOnlyWithDay.getTime()).toBe(expectedDateOnlyWithDayToEnsureTestIsNotFlaky.getTime());

        expect(localStorageServiceSpy).toHaveBeenCalledWith(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, savedDate);
        expect(closeModalSpy).toHaveBeenCalled();
    });
});
