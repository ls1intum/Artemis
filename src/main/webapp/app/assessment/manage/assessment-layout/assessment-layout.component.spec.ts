import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { AssessmentHeaderComponent } from 'app/assessment/manage/assessment-header/assessment-header.component';
import { AssessmentComplaintAlertComponent } from 'app/assessment/manage/assessment-complaint-alert/assessment-complaint-alert.component';
import { ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { MockComponent, MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { AssessmentWarningComponent } from 'app/assessment/manage/assessment-warning/assessment-warning.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockQueryParamsDirective, MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { AssessmentNoteComponent } from 'app/assessment/manage/assessment-note/assessment-note.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Result } from '../../../exercise/shared/entities/result/result.model';
import { AssessmentNote } from '../../shared/entities/assessment-note.model';

describe('AssessmentLayoutComponent', () => {
    let component: AssessmentLayoutComponent;
    let fixture: ComponentFixture<AssessmentLayoutComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), MockComponent(ComplaintsForTutorComponent), FaIconComponent],
            declarations: [
                AssessmentLayoutComponent,
                AssessmentHeaderComponent,
                AssessmentNoteComponent,
                MockComponent(AssessmentComplaintAlertComponent),
                MockComponent(AssessmentWarningComponent),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
                MockQueryParamsDirective,
            ],
            providers: [
                MockProvider(TextAssessmentAnalytics),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentLayoutComponent);
                component = fixture.componentInstance;
                fixture.componentRef.setInput('isLoading', false);
                fixture.componentRef.setInput('isTeamMode', false);
                fixture.componentRef.setInput('isAssessor', true);
                fixture.componentRef.setInput('exerciseDashboardLink', []);
                fixture.componentRef.setInput('canOverride', false);
                fixture.componentRef.setInput('hasAssessmentDueDatePassed', true);
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should include jhi-assessment-header', () => {
        const assessmentHeaderComponent = fixture.debugElement.query(By.directive(AssessmentHeaderComponent));
        expect(assessmentHeaderComponent).toBeTruthy();
    });

    it('should include jhi-assessment-complaint-alert', () => {
        const assessmentComplaintAlertComponent = fixture.debugElement.query(By.directive(AssessmentComplaintAlertComponent));
        expect(assessmentComplaintAlertComponent).toBeTruthy();
    });

    it('should include jhi-assessment-note', () => {
        const assessmentNoteComponent = fixture.debugElement.query(By.directive(AssessmentNoteComponent));
        expect(assessmentNoteComponent).not.toBeNull();
    });

    it('should include jhi-complaints-for-tutor-form', () => {
        let complaintsForTutorComponent = fixture.debugElement.query(By.directive(ComplaintsForTutorComponent));
        expect(complaintsForTutorComponent).toBeFalsy();

        fixture.componentRef.setInput('complaint', new Complaint());
        fixture.detectChanges();
        complaintsForTutorComponent = fixture.debugElement.query(By.directive(ComplaintsForTutorComponent));
        expect(complaintsForTutorComponent).toBeTruthy();
    });

    it('should set assessment note for result', () => {
        const mockResult = new Result();
        const mockAssessmentNote = { note: 'Test assessment note' } as AssessmentNote;
        fixture.componentRef.setInput('result', () => mockResult);
        fixture.detectChanges();

        component.setAssessmentNoteForResult(mockAssessmentNote);
        expect(component.result()!.assessmentNote).toBe(mockAssessmentNote);
    });
});
