import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockPipe, MockDirective } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TutorEffortStatisticsComponent } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.component';
import { ArtemisTestModule } from '../../test.module';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ChartsModule } from 'ng2-charts';
import { TutorEffort } from 'app/entities/tutor-effort.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import * as sinon from 'sinon';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

chai.use(sinonChai);
const expect = chai.expect;

describe('TutorEffortStatisticsComponent', () => {
    let fixture: ComponentFixture<TutorEffortStatisticsComponent>;
    let component: TutorEffortStatisticsComponent;
    let compiled: any;
    let textExerciseService: TextExerciseService;
    let textAssessmentService: TextAssessmentService;
    let getNumberOfTutorsInvolvedInAssessmentStub: any;
    let calculateTutorEffortStub: any;
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
            imports: [ChartsModule, ArtemisTestModule, HttpClientTestingModule],
            declarations: [TutorEffortStatisticsComponent, MockPipe(ArtemisTranslatePipe), MockDirective(MockHasAnyAuthorityDirective)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 1, exerciseId: 1 }),
                },
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
                getNumberOfTutorsInvolvedInAssessmentStub = sinon.spy(textAssessmentService, 'getNumberOfTutorsInvolvedInAssessment');
                calculateTutorEffortStub = sinon.spy(textExerciseService, 'calculateTutorEffort');
            });
    });

    it('should call loadTutorEfforts and calculateTutorEffort on init', () => {
        component.ngOnInit();
        expect(component).to.be.ok;
        expect(calculateTutorEffortStub).to.have.been.calledWith(1, 1);
        expect(getNumberOfTutorsInvolvedInAssessmentStub).to.have.been.calledWith(1, 1);
    });

    it('should call loadTutorEfforts', () => {
        fixture.detectChanges();
        component.loadTutorEfforts();
        expect(component).to.be.ok;
        expect(calculateTutorEffortStub).to.have.been.calledWith(1, 1);
    });

    it('should check tutor effort response handler with non-empty input', () => {
        component.handleTutorEffortResponse(tutorEffortsMocked);
        expect(component.tutorEfforts).to.deep.equal(tutorEffortsMocked);
        expect(component.numberOfSubmissions).to.equal(3);
        expect(component.totalTimeSpent).to.equal(51);
        expect(component.averageTimeSpent).to.equal(Math.round((component.numberOfSubmissions / component.totalTimeSpent + Number.EPSILON) * 100) / 100);
        expect(component.chartDataSets[0].data).to.deep.equal(component.effortDistribution);
    });

    it('should check tutor effort response handler with empty input', () => {
        component.currentExerciseId = 1;
        const expected: TutorEffort[] = [];
        component.handleTutorEffortResponse(expected);
        expect(component.tutorEfforts).to.deep.equal(expected);
        expect(component.numberOfSubmissions).to.equal(0);
        expect(component.totalTimeSpent).to.equal(0);
        expect(component.averageTimeSpent).to.equal(0);
        expect(component.chartDataSets[0].data).to.deep.equal(component.effortDistribution);
    });

    it('should call loadNumberOfTutorsInvolved', () => {
        component.loadNumberOfTutorsInvolved();
        expect(getNumberOfTutorsInvolvedInAssessmentStub).to.have.been.calledWith(1, 1);
    });

    it('should call distributeEffortToSets', () => {
        component.tutorEfforts = tutorEffortsMocked;
        const expected = new Array<number>(13).fill(0);
        expected[0] = 1;
        expected[2] = 1;
        expected[3] = 1;
        component.distributeEffortToSets();
        expect(component.effortDistribution).to.deep.equal(expected);
    });

    it('should show the table headers if tutor efforts list is not empty', () => {
        component.tutorEfforts = tutorEffortsMocked;
        fixture.detectChanges();
        const numberOfSubmissionsAssessed = compiled.querySelector('[jhiTranslate$=numberOfSubmissionsAssessed]');
        const totalTimeSpent = compiled.querySelector('[jhiTranslate$=totalTimeSpent]');
        const averageTime = compiled.querySelector('[jhiTranslate$=averageTime]');
        const numberOfTutorsInvolved = compiled.querySelector('[jhiTranslate$=numberOfTutorsInvolved]');
        expect(numberOfSubmissionsAssessed).to.be.ok;
        expect(totalTimeSpent).to.be.ok;
        expect(averageTime).to.be.ok;
        expect(numberOfTutorsInvolved).to.be.ok;
    });

    it('should show the no data message if tutor efforts list is empty', () => {
        component.tutorEfforts = [];
        fixture.detectChanges();
        const noData = compiled.querySelector('[jhiTranslate$=noData]');
        const numberOfSubmissionsAssessed = compiled.querySelector('[jhiTranslate$=numberOfSubmissionsAssessed]');
        expect(noData).to.be.ok;
        expect(numberOfSubmissionsAssessed).to.not.be.ok;
    });
});
