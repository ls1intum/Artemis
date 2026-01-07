import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { SecondCorrectionEnableButtonComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/second-correction-button/second-correction-enable-button.component';

describe('SecondCorrectionEnableButtonComponent', () => {
    setupTestBed({ zoneless: true });
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
        const emitStub = vi.spyOn(comp.ngModelChange, 'emit');
        comp.triggerSecondCorrectionButton();
        expect(emitStub).toHaveBeenCalledTimes(1);
    });
});
