import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../test.module';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { AssessmentHeaderComponent } from 'app/assessment/assessment-header/assessment-header.component';
import { AssessmentComplaintAlertComponent } from 'app/assessment/assessment-complaint-alert/assessment-complaint-alert.component';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { Complaint } from 'app/entities/complaint.model';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { NgbAlert, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { TextAssessmentAnalytics } from 'app/exercises/text/assess/analytics/text-assesment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';

describe('AssessmentLayoutComponent', () => {
    let component: AssessmentLayoutComponent;
    let fixture: ComponentFixture<AssessmentLayoutComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                AssessmentLayoutComponent,
                AssessmentHeaderComponent,
                MockComponent(ComplaintsForTutorComponent),
                MockComponent(AssessmentComplaintAlertComponent),
                MockComponent(NgbAlert),
                MockComponent(AssessmentWarningComponent),
                MockDirective(TranslateDirective),
                MockDirective(NgbTooltip),
                TranslatePipeMock,
                MockRouterLinkDirective,
                MockQueryParamsDirective,
            ],
            providers: [MockProvider(TextAssessmentAnalytics), { provide: ActivatedRoute, useValue: new MockActivatedRoute() }],
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

    it('should include jhi-complaints-for-tutor-form', () => {
        let complaintsForTutorComponent = fixture.debugElement.query(By.directive(ComplaintsForTutorComponent));
        expect(complaintsForTutorComponent).toBeFalsy();

        component.complaint = new Complaint();
        fixture.detectChanges();
        complaintsForTutorComponent = fixture.debugElement.query(By.directive(ComplaintsForTutorComponent));
        expect(complaintsForTutorComponent).toBeTruthy();
    });
});
