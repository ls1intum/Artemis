import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { ModalConfirmAutofocusComponent } from 'app/shared/orion/modal-confirm-autofocus/modal-confirm-autofocus.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from '../../../../../main/webapp/app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ModalConfirmAutofocusComponent', () => {
    let fixture: ComponentFixture<ModalConfirmAutofocusComponent>;
    let modal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockPipe(ArtemisTranslatePipe), MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModalConfirmAutofocusComponent);
                modal = TestBed.inject(NgbActiveModal);
            });
    });

    it('should close modal on close button click', () => {
        const closeButton = fixture.debugElement.query(By.css('.btn-danger'));
        expect(closeButton).not.toBeNull();

        const closeSpy = jest.spyOn(modal, 'close').mockImplementation();
        closeButton.nativeElement.click();
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
