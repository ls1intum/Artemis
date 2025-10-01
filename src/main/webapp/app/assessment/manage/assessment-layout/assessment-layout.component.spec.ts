import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { SimpleChange } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { AssessmentHeaderComponent } from 'app/assessment/manage/assessment-header/assessment-header.component';
import { AssessmentComplaintAlertComponent } from 'app/assessment/manage/assessment-complaint-alert/assessment-complaint-alert.component';
import { ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { AssessmentWarningComponent } from 'app/assessment/manage/assessment-warning/assessment-warning.component';
import { AssessmentNoteComponent } from 'app/assessment/manage/assessment-note/assessment-note.component';

import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';

import { MockComponent, MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockQueryParamsDirective, MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('AssessmentLayoutComponent', () => {
    let component: AssessmentLayoutComponent;
    let fixture: ComponentFixture<AssessmentLayoutComponent>;
    let getStatsForTutorsMock: jest.Mock;
    let exerciseServiceMock: Pick<ExerciseService, 'getStatsForTutors'>;

    const due = (inTime: number, late: number) => ({ inTime, late, total: inTime + late });

    beforeEach(() => {
        getStatsForTutorsMock = jest.fn();
        exerciseServiceMock = { getStatsForTutors: getStatsForTutorsMock };

        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [
                AssessmentLayoutComponent,
                AssessmentHeaderComponent,
                AssessmentNoteComponent,
                MockComponent(AssessmentComplaintAlertComponent),
                MockComponent(AssessmentWarningComponent),
                MockComponent(ComplaintsForTutorComponent),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
                MockQueryParamsDirective,
            ],
            providers: [
                { provide: ExerciseService, useValue: exerciseServiceMock },
                MockProvider(TextAssessmentAnalytics),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentLayoutComponent);
                component = fixture.componentInstance;

                // baseline props needed for new tests
                component.correctionRound = 0 as any;
                component.exercise = { id: 42 } as any;

                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show ComplaintsForTutorComponent when complaint is set', () => {
        let complaintsForTutorComponent = fixture.debugElement.query(By.directive(ComplaintsForTutorComponent));
        expect(complaintsForTutorComponent).toBeFalsy();

        component.complaint = new Complaint();
        fixture.detectChanges();

        complaintsForTutorComponent = fixture.debugElement.query(By.directive(ComplaintsForTutorComponent));
        expect(complaintsForTutorComponent).toBeTruthy();
    });

    // ──────────────────────────────────────────────────────────────
    // hasUnassessedSubmissions logic
    // ──────────────────────────────────────────────────────────────

    it('sets hasUnassessedSubmissions=true when submitted > assessed + locked', () => {
        const body = {
            numberOfSubmissions: due(3, 0),
            numberOfAssessmentsOfCorrectionRounds: [due(1, 0)],
            totalNumberOfAssessmentLocks: 0,
        };
        getStatsForTutorsMock.mockReturnValue(of(new HttpResponse<StatsForDashboard>({ body: body as unknown as StatsForDashboard })));

        component.ngOnChanges({
            exercise: new SimpleChange(undefined, { id: 42 } as any, true),
        } as any);

        expect(component.hasUnassessedSubmissions).toBeTrue();
        expect(getStatsForTutorsMock).toHaveBeenCalledWith(42);
        expect(getStatsForTutorsMock).toHaveBeenCalledOnce();
    });

    it('sets hasUnassessedSubmissions=false when all are assessed', () => {
        const body = {
            numberOfSubmissions: due(1, 0),
            numberOfAssessmentsOfCorrectionRounds: [due(1, 0)],
            totalNumberOfAssessmentLocks: 0,
        };
        getStatsForTutorsMock.mockReturnValue(of(new HttpResponse<StatsForDashboard>({ body: body as unknown as StatsForDashboard })));

        component.ngOnChanges({
            exercise: new SimpleChange(undefined, { id: 42 } as any, true),
        } as any);

        expect(component.hasUnassessedSubmissions).toBeFalse();
        expect(getStatsForTutorsMock).toHaveBeenCalledWith(42);
        expect(getStatsForTutorsMock).toHaveBeenCalledOnce();
    });

    it('falls back to totalNumberOfAssessments when round array missing', () => {
        const body = {
            numberOfSubmissions: due(3, 0),
            numberOfAssessmentsOfCorrectionRounds: [],
            totalNumberOfAssessments: due(3, 0),
            totalNumberOfAssessmentLocks: 0,
        };
        getStatsForTutorsMock.mockReturnValue(of(new HttpResponse<StatsForDashboard>({ body: body as unknown as StatsForDashboard })));

        component.ngOnChanges({
            exercise: new SimpleChange(undefined, { id: 42 } as any, true),
        } as any);

        expect(component.hasUnassessedSubmissions).toBeFalse();
    });

    it('sets hasUnassessedSubmissions=false on error', () => {
        getStatsForTutorsMock.mockReturnValue(throwError(() => new Error('network')));

        component.ngOnChanges({
            exercise: new SimpleChange(undefined, { id: 42 } as any, true),
        } as any);

        expect(component.hasUnassessedSubmissions).toBeFalse();
    });

    it('guards AssessmentNote visibility by submission && hasUnassessedSubmissions', () => {
        // case 1: note visible
        let body = {
            numberOfSubmissions: due(2, 0),
            numberOfAssessmentsOfCorrectionRounds: [due(0, 0)],
            totalNumberOfAssessmentLocks: 0,
        };
        getStatsForTutorsMock.mockReturnValue(of(new HttpResponse<StatsForDashboard>({ body: body as unknown as StatsForDashboard })));

        component.submission = {} as any;
        component.ngOnChanges({
            exercise: new SimpleChange(undefined, { id: 42 } as any, true),
        } as any);
        fixture.detectChanges();

        let note = fixture.debugElement.query(By.directive(AssessmentNoteComponent));
        expect(note).toBeTruthy();

        // case 2: all assessed → note disappears
        body = {
            numberOfSubmissions: due(1, 0),
            numberOfAssessmentsOfCorrectionRounds: [due(1, 0)],
            totalNumberOfAssessmentLocks: 0,
        };
        getStatsForTutorsMock.mockReturnValue(of(new HttpResponse<StatsForDashboard>({ body: body as unknown as StatsForDashboard })));

        component.ngOnChanges({
            exercise: new SimpleChange({ id: 42 } as any, { id: 42 } as any, false),
        } as any);
        fixture.detectChanges();

        note = fixture.debugElement.query(By.directive(AssessmentNoteComponent));
        expect(note).toBeFalsy();
    });
});
