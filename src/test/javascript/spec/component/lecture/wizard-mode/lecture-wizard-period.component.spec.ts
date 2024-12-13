import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Lecture } from 'app/entities/lecture.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { LectureUpdatePeriodComponent } from '../../../../../../main/webapp/app/lecture/lecture-period/lecture-period.component';

describe('LectureWizardPeriodComponent', () => {
    let wizardPeriodComponentFixture: ComponentFixture<LectureUpdatePeriodComponent>;
    let wizardPeriodComponent: LectureUpdatePeriodComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [LectureUpdatePeriodComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FormDateTimePickerComponent)],
            providers: [],
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
