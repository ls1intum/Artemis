import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SecondCorrectionEnableButtonComponent } from 'app/exercises/shared/dashboards/tutor/second-correction-button/second-correction-enable-button.component';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('SecondCorrectionEnableButtonComponent', () => {
    let comp: SecondCorrectionEnableButtonComponent;
    let fixture: ComponentFixture<SecondCorrectionEnableButtonComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SecondCorrectionEnableButtonComponent, TranslatePipeMock],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SecondCorrectionEnableButtonComponent);
                comp = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('test call', () => {
        const emitStub = jest.spyOn(comp.ngModelChange, 'emit');
        comp.triggerSecondCorrectionButton();
        expect(emitStub).toHaveBeenCalledOnce();
    });
});
