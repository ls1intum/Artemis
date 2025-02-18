import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { SecondCorrectionEnableButtonComponent } from 'app/exercises/shared/dashboards/tutor/second-correction-button/second-correction-enable-button.component';

describe('SecondCorrectionEnableButtonComponent', () => {
    let comp: SecondCorrectionEnableButtonComponent;
    let fixture: ComponentFixture<SecondCorrectionEnableButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        }).compileComponents();
        fixture = TestBed.createComponent(SecondCorrectionEnableButtonComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('test call', () => {
        const emitStub = jest.spyOn(comp.ngModelChange, 'emit');
        comp.triggerSecondCorrectionButton();
        expect(emitStub).toHaveBeenCalledOnce();
    });
});
