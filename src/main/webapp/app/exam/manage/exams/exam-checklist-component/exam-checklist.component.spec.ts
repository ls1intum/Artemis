import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { MockExamChecklistService } from 'test/helpers/mocks/service/mock-exam-checklist.service';
import { of } from 'rxjs';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ExamEditWorkingTimeComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MODULE_FEATURE_TEXT } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

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
    let profileService: ProfileService;
    let getProfileInfoSub: jest.SpyInstance;

    const exam = new Exam();
    const examChecklist = new ExamChecklist();
    const dueDateStatArray = [{ inTime: 0, late: 0, total: 0 }];

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                ExamChecklistComponent,
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                ExamChecklistExerciseGroupTableComponent,
                ProgressBarComponent,
                ExamEditWorkingTimeComponent,
            ],
            providers: [
                { provide: ExamChecklistService, useClass: MockExamChecklistService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                examChecklistComponentFixture = TestBed.createComponent(ExamChecklistComponent);
                component = examChecklistComponentFixture.componentInstance;
                examChecklistService = TestBed.inject(ExamChecklistService);
                profileService = TestBed.inject(ProfileService);

                getProfileInfoSub = jest.spyOn(profileService, 'getProfileInfo');
                getProfileInfoSub.mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_TEXT] });
            });
    });

    beforeEach(() => {
        // reset exam
        examChecklistComponentFixture.componentRef.setInput('exam', exam);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });
    it('should count mandatory exercises correctly', () => {
        component.exam().exerciseGroups = getExerciseGroups(true);

        component.ngOnChanges();

        expect(component.countMandatoryExercises).toBe(0);
        expect(component.hasOptionalExercises).toBeTrue();

        component.exam().exerciseGroups![0].isMandatory = true;

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

        component.exam().exerciseGroups!.push(additionalExerciseGroup);

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
