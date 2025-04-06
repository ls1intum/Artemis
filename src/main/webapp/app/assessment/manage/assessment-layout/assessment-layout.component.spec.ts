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
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../../../../../test/javascript/spec/helpers/mocks/directive/mock-router-link.directive';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assesment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from '../../../../../../test/javascript/spec/helpers/mocks/activated-route/mock-activated-route';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { AssessmentNoteComponent } from 'app/assessment/manage/assessment-note/assessment-note.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';

describe('AssessmentLayoutComponent', () => {
    let component: AssessmentLayoutComponent;
    let fixture: ComponentFixture<AssessmentLayoutComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), MockComponent(ComplaintsForTutorComponent)],
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

        component.complaint = new Complaint();
        fixture.detectChanges();
        complaintsForTutorComponent = fixture.debugElement.query(By.directive(ComplaintsForTutorComponent));
        expect(complaintsForTutorComponent).toBeTruthy();
    });
});
