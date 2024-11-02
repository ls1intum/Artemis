import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUpdatePeriodComponent } from 'app/lecture/wizard-mode/lecture-wizard-period.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('LectureUpdatePeriodComponent', () => {
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
                wizardPeriodComponent.lecture = new Lecture();
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
