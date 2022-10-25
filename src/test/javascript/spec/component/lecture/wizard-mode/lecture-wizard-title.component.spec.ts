import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUpdateWizardTitleComponent } from 'app/lecture/wizard-mode/lecture-wizard-title.component';
import { Lecture } from 'app/entities/lecture.model';

describe('LectureWizardTitleComponent', () => {
    let wizardTitleComponentFixture: ComponentFixture<LectureUpdateWizardTitleComponent>;
    let wizardTitleComponent: LectureUpdateWizardTitleComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LectureUpdateWizardTitleComponent],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardTitleComponentFixture = TestBed.createComponent(LectureUpdateWizardTitleComponent);
                wizardTitleComponent = wizardTitleComponentFixture.componentInstance;
                wizardTitleComponent.lecture = new Lecture();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        wizardTitleComponentFixture.detectChanges();
        expect(wizardTitleComponent).not.toBeNull();
    });
});
