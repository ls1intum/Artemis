import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HealthModalComponent } from 'app/admin/health/health-modal.component';
import { HealthDetails, HealthKey } from 'app/admin/health/health.model';
import { MockProvider } from 'ng-mocks';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';

describe('HealthModalComponentTest', () => {
    let fixture: ComponentFixture<HealthModalComponent>;
    let comp: HealthModalComponent;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [HealthModalComponent, TranslatePipeMock],
            providers: [MockProvider(NgbActiveModal)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(HealthModalComponent);
                comp = fixture.componentInstance;
                activeModal = TestBed.inject(NgbActiveModal);
            });
    });

    it('should convert basic types to string', () => {
        expect(comp.readableValue(42)).toBe('42');
    });

    it('passed object should still be parsable', () => {
        const object = {
            foo: 'bar',
            bar: 42,
        };
        const result = comp.readableValue(object);
        expect(JSON.parse(result)).toEqual(object);
    });

    it('should parse GB-value to String', () => {
        comp.health = { key: 'diskSpace' as HealthKey, value: {} as HealthDetails };
        const gbValueInByte = 4156612385;
        const expectedString = '3.87 GB';
        expect(comp.readableValue(gbValueInByte)).toBe(expectedString);
    });

    it('should parse MB-value to String', () => {
        comp.health = { key: 'diskSpace' as HealthKey, value: {} as HealthDetails };
        const gbValueInByte = 41566;
        const expectedString = '0.04 MB';
        expect(comp.readableValue(gbValueInByte)).toBe(expectedString);
    });

    it('should dismiss the modal if close button is clicked', () => {
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');

        const button = fixture.debugElement.query(By.css('button.btn-close'));
        expect(button).not.toBeNull();

        button.nativeElement.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });
});
