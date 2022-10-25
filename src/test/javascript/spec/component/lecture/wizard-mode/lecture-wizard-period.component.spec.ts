import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUpdateWizardPeriodComponent } from 'app/lecture/wizard-mode/lecture-wizard-period.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('LectureWizardPeriodComponent', () => {
    let wizardPeriodComponentFixture: ComponentFixture<LectureUpdateWizardPeriodComponent>;
    let wizardPeriodComponent: LectureUpdateWizardPeriodComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LectureUpdateWizardPeriodComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardPeriodComponentFixture = TestBed.createComponent(LectureUpdateWizardPeriodComponent);
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
