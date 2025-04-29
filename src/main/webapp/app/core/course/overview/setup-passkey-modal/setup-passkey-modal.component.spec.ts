import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';

import { SetupPasskeyModalComponent } from './setup-passkey-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('SetupPasskeyModalComponent', () => {
    let component: SetupPasskeyModalComponent;
    let fixture: ComponentFixture<SetupPasskeyModalComponent>;
    let router: Router;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SetupPasskeyModalComponent],
            declarations: [MockDirective(TranslateDirective)],
            providers: [MockProvider(NgbActiveModal), { provide: Router, useValue: { navigateByUrl: jest.fn() } }],
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
});
