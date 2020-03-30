import { TestBed, ComponentFixture } from '@angular/core/testing';
import { TextSubmissionAssessmentComponent } from 'app/exercises/text/assess-new/text-submission-assessment.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess-new/text-assessment-area/text-assessment-area.component';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('TextSubmissionAssessmentComponent', () => {
    let component: TextSubmissionAssessmentComponent;
    let fixture: ComponentFixture<TextSubmissionAssessmentComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, ArtemisAssessmentSharedModule, AssessmentInstructionsModule],
            declarations: [TextSubmissionAssessmentComponent, TextAssessmentAreaComponent],
        }).overrideModule(ArtemisTestModule, {
            remove: {
                declarations: [MockComponent(FaIconComponent)],
                exports: [MockComponent(FaIconComponent)],
            }
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextSubmissionAssessmentComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should not print default message', () => {
        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('p').textContent).not.toContain('text-submission-assessment works!');
    });

    it('should use jhi-assessment-layout', () => {
        const sharedLayout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent));
        expect(sharedLayout).toBeTruthy();
    });
});
