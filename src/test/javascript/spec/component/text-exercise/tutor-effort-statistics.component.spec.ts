import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TutorEffortStatisticsComponent } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.component';
import { ArtemisTestModule } from '../../test.module';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { TutorEffort } from 'app/entities/tutor-effort.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { BarChartModule } from '@swimlane/ngx-charts';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { of } from 'rxjs';

describe('TutorEffortStatisticsComponent', () => {
    let fixture: ComponentFixture<TutorEffortStatisticsComponent>;
    let component: TutorEffortStatisticsComponent;
    let compiled: any;
    let textExerciseService: TextExerciseService;
    let textAssessmentService: TextAssessmentService;
    let getNumberOfTutorsInvolvedInAssessmentStub: any;
    let calculateTutorEffortStub: any;
    let router: Router;
    const tutorEffortsMocked: TutorEffort[] = [
        {
            courseId: 1,
            exerciseId: 1,
            numberOfSubmissionsAssessed: 1,
            totalTimeSpentMinutes: 1,
        },
        {
            courseId: 1,
            exerciseId: 1,
            numberOfSubmissionsAssessed: 1,
            totalTimeSpentMinutes: 20,
        },
        {
            courseId: 1,
            exerciseId: 1,
            numberOfSubmissionsAssessed: 1,
            totalTimeSpentMinutes: 30,
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule, MockModule(BarChartModule)],
            declarations: [TutorEffortStatisticsComponent, MockPipe(ArtemisTranslatePipe), MockDirective(MockHasAnyAuthorityDirective), MockComponent(HelpIconComponent)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 1, exerciseId: 1 }),
                },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorEffortStatisticsComponent);
                component = fixture.componentInstance;
                compiled = fixture.debugElement.nativeElement;
                fixture.detectChanges();
                textExerciseService = fixture.debugElement.injector.get(TextExerciseService);
                textAssessmentService = fixture.debugElement.injector.get(TextAssessmentService);
                getNumberOfTutorsInvolvedInAssessmentStub = jest.spyOn(textAssessmentService, 'getNumberOfTutorsInvolvedInAssessment');
                calculateTutorEffortStub = jest.spyOn(textExerciseService, 'calculateTutorEffort');
                router = TestBed.inject(Router);
            });
    });

    it('should call loadTutorEfforts and calculateTutorEffort on init', () => {
        component.ngOnInit();
        expect(calculateTutorEffortStub).toHaveBeenCalledWith(1, 1);
        expect(getNumberOfTutorsInvolvedInAssessmentStub).toHaveBeenCalledWith(1, 1);
    });

    it('should call loadTutorEfforts', () => {
        fixture.detectChanges();
        component.loadTutorEfforts();
        expect(calculateTutorEffortStub).toHaveBeenCalledWith(1, 1);
    });

    it('should check tutor effort response handler with non-empty input', () => {
        component.handleTutorEffortResponse(tutorEffortsMocked);
        expect(component.tutorEfforts).toEqual(tutorEffortsMocked);
        expect(component.numberOfSubmissions).toBe(3);
        expect(component.totalTimeSpent).toBe(51);
        expect(component.averageTimeSpent).toBe(Math.round((component.numberOfSubmissions / component.totalTimeSpent + Number.EPSILON) * 100) / 100);
        checkNgxData();
    });

    it('should check tutor effort response handler with empty input', () => {
        component.currentExerciseId = 1;
        const expected: TutorEffort[] = [];
        component.handleTutorEffortResponse(expected);
        expect(component.tutorEfforts).toEqual(expected);
        expect(component.numberOfSubmissions).toBe(0);
        expect(component.totalTimeSpent).toBe(0);
        expect(component.averageTimeSpent).toBe(0);
        checkNgxData();
    });

    it('should call loadNumberOfTutorsInvolved', () => {
        component.loadNumberOfTutorsInvolved();
        expect(getNumberOfTutorsInvolvedInAssessmentStub).toHaveBeenCalledWith(1, 1);
    });

    it('should call distributeEffortToSets', () => {
        component.tutorEfforts = tutorEffortsMocked;
        const expected = new Array<number>(13).fill(0);
        expected[0] = 1;
        expected[2] = 1;
        expected[3] = 1;
        component.distributeEffortToSets();
        expect(component.effortDistribution).toEqual(expected);
    });

    it('should show the table headers if tutor efforts list is not empty', () => {
        component.tutorEfforts = tutorEffortsMocked;
        fixture.detectChanges();
        const numberOfSubmissionsAssessed = compiled.querySelector('[jhiTranslate$=numberOfSubmissionsAssessed]');
        const totalTimeSpent = compiled.querySelector('[jhiTranslate$=totalTimeSpent]');
        const averageTime = compiled.querySelector('[jhiTranslate$=averageTime]');
        const numberOfTutorsInvolved = compiled.querySelector('[jhiTranslate$=numberOfTutorsInvolved]');
        expect(numberOfSubmissionsAssessed).not.toBeNull();
        expect(totalTimeSpent).not.toBeNull();
        expect(averageTime).not.toBeNull();
        expect(numberOfTutorsInvolved).not.toBeNull();
    });

    it('should show the no data message if tutor efforts list is empty', () => {
        component.tutorEfforts = [];
        fixture.detectChanges();
        const noData = compiled.querySelector('[jhiTranslate$=noData]');
        const numberOfSubmissionsAssessed = compiled.querySelector('[jhiTranslate$=numberOfSubmissionsAssessed]');
        expect(noData).not.toBeNull();
        expect(numberOfSubmissionsAssessed).toBeNull();
    });

    it('should delegate the user correctly', () => {
        component.currentCourseId = 42;
        component.currentExerciseId = 33;
        const navigateSpy = jest.spyOn(router, 'navigate');
        const expectedArray = ['/course-management', 42, 'assessment-dashboard', 33];

        component.onSelect();

        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(expectedArray);
    });

    it('should compute the correct medians', () => {
        const tutorEfforts = [
            ...tutorEffortsMocked,
            {
                courseId: 1,
                exerciseId: 1,
                numberOfSubmissionsAssessed: 15,
                totalTimeSpentMinutes: 32,
            },
        ];
        jest.spyOn(textExerciseService, 'calculateTutorEffort').mockReturnValue(of(tutorEfforts));
        jest.spyOn(component, 'loadNumberOfTutorsInvolved').mockImplementation();

        component.ngOnInit();

        expect(component.medianValue).toBe(25);

        const assessedSubmissionsMedian = component.getMedianAmountOfAssessedSubmissions('[30-40)');

        expect(assessedSubmissionsMedian).toBe(8);
    });

    const checkNgxData = () => {
        for (let i = 0; i < component.effortDistribution.length; i++) {
            expect(component.ngxData[i].value).toBe(component.effortDistribution[i]);
        }
    };
});
