import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormDateTimePickerComponent } from '../../../../../main/webapp/app/shared/date-time-picker/date-time-picker.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Lecture } from '../../../../../main/webapp/app/entities/lecture.model';
import { ArtemisTranslatePipe } from '../../../../../main/webapp/app/shared/pipes/artemis-translate.pipe';
import { LectureUpdatePeriodComponent } from 'app/lecture/manage/lecture-period/lecture-period.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('LectureWizardPeriodComponent', () => {
    let wizardPeriodComponentFixture: ComponentFixture<LectureUpdatePeriodComponent>;
    let wizardPeriodComponent: LectureUpdatePeriodComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [LectureUpdatePeriodComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FormDateTimePickerComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardPeriodComponentFixture = TestBed.createComponent(LectureUpdatePeriodComponent);
                wizardPeriodComponent = wizardPeriodComponentFixture.componentInstance;

                wizardPeriodComponentFixture.componentRef.setInput('lecture', new Lecture());
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        wizardPeriodComponentFixture.detectChanges();
        expect(wizardPeriodComponent).not.toBeNull();
    });
});
