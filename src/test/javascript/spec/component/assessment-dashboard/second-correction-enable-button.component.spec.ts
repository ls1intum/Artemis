import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SecondCorrectionEnableButtonComponent } from 'app/exercise/dashboards/tutor/second-correction-button/second-correction-enable-button.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('SecondCorrectionEnableButtonComponent', () => {
    let comp: SecondCorrectionEnableButtonComponent;
    let fixture: ComponentFixture<SecondCorrectionEnableButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
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
