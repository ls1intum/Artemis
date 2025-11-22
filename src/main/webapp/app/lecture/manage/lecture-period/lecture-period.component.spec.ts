import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LectureUpdatePeriodComponent } from 'app/lecture/manage/lecture-period/lecture-period.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

describe('LectureWizardPeriodComponent', () => {
    let fixture: ComponentFixture<LectureUpdatePeriodComponent>;
    let component: LectureUpdatePeriodComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [LectureUpdatePeriodComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FormDateTimePickerComponent), FontAwesomeModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LectureUpdatePeriodComponent);
                component = fixture.componentInstance;

                fixture.componentRef.setInput('lecture', new Lecture());
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should call validateDatesFunction on date change', () => {
        const validateSpy = jest.fn();
        component.validateDatesFunction = validateSpy;
        fixture.detectChanges();
        component.onDateChange();
        expect(validateSpy).toHaveBeenCalledOnce();
    });

    it('should compute isPeriodSectionValid correctly when all children valid', () => {
        const pickers = component.periodSectionDatepickers();
        pickers[0].isValid = jest.fn(() => true) as any;
        pickers[1].isValid = jest.fn(() => true) as any;
        const result = component.isPeriodSectionValid();
        expect(result).toBeTrue();
    });

    it('should compute isPeriodSectionValid correctly when any child invalid', () => {
        const pickers = component.periodSectionDatepickers();
        pickers[0].isValid = jest.fn(() => true) as any;
        pickers[1].isValid = jest.fn(() => false) as any;
        const result = component.isPeriodSectionValid();
        expect(result).toBeFalse();
    });
});
