import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { Exam } from 'app/entities/exam.model';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../test.module';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { MockExamChecklistService } from '../../../../helpers/mocks/service/mock-exam-checklist.service';
import { of } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { ExamEditWorkingTimeComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time.component';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { AlertService } from 'app/core/util/alert.service';

function getExerciseGroups(equalPoints: boolean) {
    const dueDateStatArray = [{ inTime: 0, late: 0, total: 0 }];
    const exerciseGroups = [
        {
            id: 1,
            exercises: [
                {
                    id: 3,
                    maxPoints: 100,
                    numberOfAssessmentsOfCorrectionRounds: dueDateStatArray,
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
                {
                    id: 2,
                    maxPoints: 100,
                    numberOfAssessmentsOfCorrectionRounds: dueDateStatArray,
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            ],
        },
    ];
    if (!equalPoints) {
        exerciseGroups[0].exercises[0].maxPoints = 50;
    }
    return exerciseGroups;
}

describe('ExamChecklistComponent', () => {
    let examChecklistComponentFixture: ComponentFixture<ExamChecklistComponent>;
    let component: ExamChecklistComponent;

    let examChecklistService: ExamChecklistService;

    const exam = new Exam();
    const examChecklist = new ExamChecklist();
    const dueDateStatArray = [{ inTime: 0, late: 0, total: 0 }];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedComponentModule],
            declarations: [
                ExamChecklistComponent,
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                ExamChecklistExerciseGroupTableComponent,
                ProgressBarComponent,
                ExamEditWorkingTimeComponent,
                MockPipe(ArtemisTranslatePipe),
                MockRouterLinkDirective,
            ],
            providers: [
                { provide: ExamChecklistService, useClass: MockExamChecklistService },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ArtemisTranslatePipe, useClass: ArtemisTranslatePipe },
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                examChecklistComponentFixture = TestBed.createComponent(ExamChecklistComponent);
                component = examChecklistComponentFixture.componentInstance;
                examChecklistService = TestBed.inject(ExamChecklistService);
            });
    });

    beforeEach(() => {
        // reset exam
        component.getExamRoutesByIdentifier = jest.fn(() => 'mocked-route');
        component.exam = exam;
        exam.course = new Course();
        exam.course.isAtLeastInstructor = true;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });
    it('should count mandatory exercises correctly', () => {
        component.exam.exerciseGroups = getExerciseGroups(true);

        component.ngOnChanges();

        expect(component.countMandatoryExercises).toBe(0);
        expect(component.hasOptionalExercises).toBeTrue();

        component.exam.exerciseGroups[0].isMandatory = true;

        component.ngOnChanges();

        expect(component.countMandatoryExercises).toBe(1);
        expect(component.hasOptionalExercises).toBeFalse();

        const additionalExerciseGroup = {
            id: 13,
            exercises: [
                {
                    id: 23,
                    maxPoints: 100,
                    numberOfAssessmentsOfCorrectionRounds: dueDateStatArray,
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            ],
        };
        component.exam.exerciseGroups.push(additionalExerciseGroup);

        component.ngOnChanges();

        expect(component.countMandatoryExercises).toBe(1);
        expect(component.hasOptionalExercises).toBeTrue();
    });

    it('should set exam checklist correctly', () => {
        const getExamStatisticsStub = jest.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));

        component.ngOnChanges();

        expect(getExamStatisticsStub).toHaveBeenCalledOnce();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(exam);
        expect(component.examChecklist).toEqual(examChecklist);
    });

    it('should create button correctly according to existsUnfinishedAssessments', () => {
        exam.publishResultsDate = dayjs();
        component.existsUnfinishedAssessments = true;
        examChecklistComponentFixture.detectChanges();
        const button = examChecklistComponentFixture.nativeElement.querySelector('button[id="tutor-exam-dashboard-button_table_assessment_check"]');
        expect(button).not.toBeNull();
        expect(button.disabled).toBeFalse();
    });

    it('should create button correctly according to existsUnassessedQuizzes', () => {
        exam.publishResultsDate = dayjs();
        component.existsUnassessedQuizzes = true;
        examChecklistComponentFixture.detectChanges();
        const button = examChecklistComponentFixture.nativeElement.querySelector('#evaluateQuizExercisesButton');
        expect(button).not.toBeNull();
        expect(button.disabled).toBeFalse();
    });

    it('should create button correctly according to existsUnsubmittedExercises', () => {
        exam.publishResultsDate = dayjs();
        component.existsUnsubmittedExercises = true;
        examChecklistComponentFixture.detectChanges();
        const button = examChecklistComponentFixture.nativeElement.querySelector('#assessUnsubmittedExamModelingAndTextParticipationsButton');
        expect(button).not.toBeNull();
        expect(button.disabled).toBeFalse();
    });

    it('should set existsUnassessedQuizzes correctly', () => {
        const getExamStatisticsStub = jest.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));

        component.ngOnChanges();

        expect(getExamStatisticsStub).toHaveBeenCalledOnce();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(exam);
        expect(component.examChecklist.existsUnassessedQuizzes).toEqual(examChecklist.existsUnassessedQuizzes);
    });

    it('should set existsUnsubmittedExercises correctly', () => {
        const getExamStatisticsStub = jest.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));

        component.ngOnChanges();

        expect(getExamStatisticsStub).toHaveBeenCalledOnce();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(exam);
        expect(component.examChecklist.existsUnsubmittedExercises).toEqual(examChecklist.existsUnsubmittedExercises);
    });
});
