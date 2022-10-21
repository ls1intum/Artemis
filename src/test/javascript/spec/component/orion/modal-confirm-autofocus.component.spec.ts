import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ModalConfirmAutofocusComponent } from 'app/shared/orion/modal-confirm-autofocus/modal-confirm-autofocus.component';
import { MockProvider } from 'ng-mocks';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('ModalConfirmAutofocusComponent', () => {
    let fixture: ComponentFixture<ModalConfirmAutofocusComponent>;
    let modal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ModalConfirmAutofocusComponent, TranslatePipeMock],
            providers: [MockProvider(NgbActiveModal)],
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
