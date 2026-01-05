import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LectureUpdatePeriodComponent } from 'app/lecture/manage/lecture-period/lecture-period.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';

describe('LectureWizardPeriodComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<LectureUpdatePeriodComponent>;
    let component: LectureUpdatePeriodComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                LectureUpdatePeriodComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FormDateTimePickerComponent),
                FontAwesomeModule,
                OwlDateTimeModule,
                OwlNativeDateTimeModule,
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureUpdatePeriodComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lecture', new Lecture());
        fixture.componentRef.setInput('validateDatesFunction', vi.fn());
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should call validateDatesFunction on date change', () => {
        const validateSpy = vi.fn();
        fixture.componentRef.setInput('validateDatesFunction', validateSpy);
        fixture.detectChanges();
        component.onDateChange();
        expect(validateSpy).toHaveBeenCalledTimes(1);
    });

    it('should compute isPeriodSectionValid correctly when all children valid', () => {
        const pickers = component.periodSectionDatepickers();
        pickers[0].isValid = vi.fn(() => true) as any;
        pickers[1].isValid = vi.fn(() => true) as any;
        const result = component.isPeriodSectionValid();
        expect(result).toBe(true);
    });

    it('should compute isPeriodSectionValid correctly when any child invalid', () => {
        const pickers = component.periodSectionDatepickers();
        pickers[0].isValid = vi.fn(() => true) as any;
        pickers[1].isValid = vi.fn(() => false) as any;
        const result = component.isPeriodSectionValid();
        expect(result).toBe(false);
    });
});
