import { ExamConductionCounts } from 'app/exam/shared/entities/exam-conduction-counts.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ProgressBarComponent } from 'app/exercise/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { MockExamChecklistService } from 'test/helpers/mocks/service/mock-exam-checklist.service';
import { Observable, Subject, of, throwError } from 'rxjs';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import * as ErrorReporting from 'app/core/error-reporting/capture-exception';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExamApi } from 'app/openapi/api/exam-api';
import { StudentExamApi } from 'app/openapi/api/student-exam-api';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ExamEditWorkingTimeComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideRouter } from '@angular/router';
import { MODULE_FEATURE_PLAGIARISM, MODULE_FEATURE_TEXT } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import enExam from '../../../../../i18n/en/exam.json';
import deExam from '../../../../../i18n/de/exam.json';
import enPlagiarism from '../../../../../i18n/en/plagiarism.json';
import dePlagiarism from '../../../../../i18n/de/plagiarism.json';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';

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
    let getProfileInfoSub: ReturnType<typeof vi.spyOn>;

    const exam = new Exam();
    const examChecklist = new ExamChecklist();
    const dueDateStatArray = [{ inTime: 0, late: 0, total: 0 }];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
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
                provideRouter([]),
            ],
        }).compileComponents();

        examChecklistComponentFixture = TestBed.createComponent(ExamChecklistComponent);
        component = examChecklistComponentFixture.componentInstance;
        examChecklistService = TestBed.inject(ExamChecklistService);
        profileService = TestBed.inject(ProfileService);

        getProfileInfoSub = vi.spyOn(profileService, 'getProfileInfo');
        getProfileInfoSub.mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_TEXT] });

        // reset exam
        examChecklistComponentFixture.componentRef.setInput('exam', exam);
        examChecklistComponentFixture.componentRef.setInput('getExamRoutesByIdentifier', () => []);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should count mandatory exercises correctly', () => {
        component.exam().exerciseGroups = getExerciseGroups(true);

        examChecklistComponentFixture.componentRef.setInput('exam', { ...component.exam() });
        examChecklistComponentFixture.detectChanges();

        expect(component.countMandatoryExercises()).toBe(0);
        expect(component.hasOptionalExercises()).toBe(true);

        const examWithMandatory = { ...component.exam() };
        examWithMandatory.exerciseGroups = component.exam().exerciseGroups!.map((group, idx) => (idx === 0 ? { ...group, isMandatory: true } : group));
        examChecklistComponentFixture.componentRef.setInput('exam', examWithMandatory);
        examChecklistComponentFixture.detectChanges();

        expect(component.countMandatoryExercises()).toBe(1);
        expect(component.hasOptionalExercises()).toBe(false);

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

        const examWithAdditional = { ...examWithMandatory, exerciseGroups: [...examWithMandatory.exerciseGroups!, additionalExerciseGroup] };
        examChecklistComponentFixture.componentRef.setInput('exam', examWithAdditional);
        examChecklistComponentFixture.detectChanges();

        expect(component.countMandatoryExercises()).toBe(1);
        expect(component.hasOptionalExercises()).toBe(true);
    });

    it('should set exam checklist correctly', () => {
        const getExamStatisticsStub = vi.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));

        examChecklistComponentFixture.detectChanges();

        expect(getExamStatisticsStub).toHaveBeenCalled();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(exam);
        expect(component.examChecklist()).toEqual(examChecklist);
    });

    it('should set existsUnassessedQuizzes correctly', () => {
        const getExamStatisticsStub = vi.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));

        examChecklistComponentFixture.detectChanges();

        expect(getExamStatisticsStub).toHaveBeenCalled();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(exam);
        expect(component.examChecklist()!.existsUnassessedQuizzes).toEqual(examChecklist.existsUnassessedQuizzes);
    });

    it('should set existsUnsubmittedExercises correctly', () => {
        const getExamStatisticsStub = vi.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));

        examChecklistComponentFixture.detectChanges();

        expect(getExamStatisticsStub).toHaveBeenCalled();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(exam);
        expect(component.examChecklist()!.existsUnsubmittedExercises).toEqual(examChecklist.existsUnsubmittedExercises);
    });

    describe('checklist state', () => {
        let alertService: AlertService;
        let apis: { examApi: ExamApi; studentExamApi: StudentExamApi };

        const setUp = (examOverrides: Partial<Exam> = {}, stats: Partial<ExamChecklist> = {}) => {
            alertService = TestBed.inject(AlertService);
            apis = { examApi: TestBed.inject(ExamApi), studentExamApi: TestBed.inject(StudentExamApi) };
            const checklist = Object.assign(new ExamChecklist(), { numberOfExamsStarted: 3, numberOfExamsSubmitted: 2, numberOfTotalParticipationsForAssessment: 3 }, stats);
            vi.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(checklist));
            const testedExam = Object.assign(new Exam(), { id: 1, examMode: ExamMode.REAL, course: { id: 1, isAtLeastInstructor: true } }, examOverrides);
            examChecklistComponentFixture.componentRef.setInput('exam', testedExam);
            return testedExam;
        };

        // the quiz evaluation answers with the number of evaluated exercises, the assessment of unsubmitted exams with no body
        const spyOnAction = (action: (typeof actions)[number]['action'], outcome?: Observable<number>) => {
            if (action === 'evaluateQuizExercises') {
                const spy = vi.spyOn(apis.examApi, 'evaluateQuizExercises');
                return outcome ? spy.mockReturnValue(outcome) : spy;
            }
            const spy = vi.spyOn(apis.studentExamApi, 'assessUnsubmittedStudentExamsAndEmptySubmissions');
            return outcome ? spy.mockReturnValue(outcome.pipe(map(() => undefined))) : spy;
        };

        const mockLongestWorkingTime = (seconds: number) => vi.spyOn(TestBed.inject(StudentExamApi), 'getLongestWorkingTimeForExam').mockReturnValue(of(seconds));

        const actions = [
            { action: 'evaluateQuizExercises', busy: 'isEvaluatingQuizExercises', done: 'existsUnassessedQuizzes', stats: { existsUnassessedQuizzes: true } },
            {
                action: 'assessUnsubmittedExamModelingAndTextParticipations',
                busy: 'isAssessingUnsubmittedExams',
                done: 'existsUnsubmittedExercises',
                stats: { existsUnsubmittedExercises: true },
            },
        ] as const;

        it('should take the counters from the statistics and count started and submitted exams from the websocket', () => {
            setUp();
            const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
            examChecklistComponentFixture.detectChanges();

            expect(component.numberOfStarted()).toBe(3);
            expect(component.numberOfSubmitted()).toBe(2);

            websocketService.emit('/topic/exam/1/submitted', undefined);
            websocketService.emit('/topic/exam/1/submitted', undefined);
            websocketService.emit('/topic/exam/1/started', undefined);
            expect(component.numberOfSubmitted()).toBe(4);
            expect(component.numberOfStarted()).toBe(4);

            examChecklistComponentFixture.destroy();
            websocketService.emit('/topic/exam/1/submitted', undefined);
            websocketService.emit('/topic/exam/1/started', undefined);
            expect(component.numberOfSubmitted()).toBe(4);
            expect(component.numberOfStarted()).toBe(4);
        });

        it.each([
            { description: 'already generated on the coverpage', initialGenerated: 1, generatedAfterStart: 1 },
            { description: 'created after the initial statistics', initialGenerated: 0, generatedAfterStart: 2 },
        ])('should use the authoritative total for a test exam $description', ({ initialGenerated, generatedAfterStart }) => {
            setUp({ examMode: ExamMode.TEST }, { numberOfGeneratedStudentExams: initialGenerated, numberOfExamsStarted: 0 });
            const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
            examChecklistComponentFixture.detectChanges();
            expect(component.numberOfGeneratedStudentExams()).toBe(initialGenerated);
            vi.spyOn(examChecklistService, 'getExamConductionCounts').mockReturnValue(
                of({ numberOfGeneratedStudentExams: generatedAfterStart, numberOfExamsStarted: 1, numberOfExamsSubmitted: 2 }),
            );

            websocketService.emit('/topic/exam/1/started', undefined);
            examChecklistComponentFixture.detectChanges();

            expect(component.numberOfStarted()).toBe(1);
            expect(component.numberOfGeneratedStudentExams()).toBe(generatedAfterStart);
        });

        it('should retain the initial nonzero baseline while start events arrive', () => {
            setUp({ examMode: ExamMode.TEST });
            const initial = new Subject<ExamChecklist>();
            const refreshedCounts = new Subject<ExamConductionCounts>();
            const fetchCounts = vi.spyOn(examChecklistService, 'getExamConductionCounts').mockReturnValue(refreshedCounts);
            vi.mocked(examChecklistService.getExamStatistics).mockReturnValueOnce(initial).mockReturnValue(of(new ExamChecklist()));
            examChecklistComponentFixture.detectChanges();
            const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;

            websocketService.emit('/topic/exam/1/started', undefined);
            websocketService.emit('/topic/exam/1/started', undefined);
            expect(initial.observed).toBe(true);
            expect(examChecklistService.getExamStatistics).toHaveBeenCalledTimes(1);
            initial.next(Object.assign(new ExamChecklist(), { numberOfGeneratedStudentExams: 8, numberOfExamsStarted: 5, numberOfExamsSubmitted: 2 }));
            initial.complete();

            expect(component.numberOfGeneratedStudentExams()).toBe(8);
            expect(component.numberOfStarted()).toBe(5);
            expect(component.numberOfSubmitted()).toBe(2);
            expect(fetchCounts).toHaveBeenCalledTimes(1);
            refreshedCounts.next({ numberOfGeneratedStudentExams: 8, numberOfExamsStarted: 7, numberOfExamsSubmitted: 2 });
            refreshedCounts.complete();
            expect(component.numberOfStarted()).toBe(7);
        });

        it('should cancel stale counts and unsubscribe on exam change and destroy', () => {
            setUp({ examMode: ExamMode.TEST }, { numberOfGeneratedStudentExams: 1, numberOfExamsStarted: 0 });
            const previous = new Subject<ExamConductionCounts>();
            const current = new Subject<ExamConductionCounts>();
            vi.spyOn(examChecklistService, 'getExamConductionCounts').mockReturnValueOnce(previous).mockReturnValue(current);
            examChecklistComponentFixture.detectChanges();
            const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
            websocketService.emit('/topic/exam/1/started', undefined);
            expect(previous.observed).toBe(true);

            examChecklistComponentFixture.componentRef.setInput('exam', { ...component.exam(), id: 2, course: { id: 2 } });
            examChecklistComponentFixture.detectChanges();
            expect(previous.observed).toBe(false);
            previous.next({ numberOfGeneratedStudentExams: 100, numberOfExamsStarted: 100, numberOfExamsSubmitted: 100 });
            expect(component.numberOfGeneratedStudentExams()).toBe(1);
            websocketService.emit('/topic/exam/1/started', undefined);
            expect(component.numberOfStarted()).toBe(0);
            websocketService.emit('/topic/exam/2/started', undefined);
            expect(current.observed).toBe(true);
            examChecklistComponentFixture.destroy();
            expect(current.observed).toBe(false);
            current.next({ numberOfGeneratedStudentExams: 100, numberOfExamsStarted: 100, numberOfExamsSubmitted: 100 });
            expect(component.numberOfGeneratedStudentExams()).toBe(1);
        });

        it('should coalesce overlapping starts without repeating the full checklist', () => {
            vi.useFakeTimers();
            try {
                setUp({ examMode: ExamMode.TEST }, { numberOfGeneratedStudentExams: 1, numberOfExamsStarted: 0 });
                const baseline = Object.assign(new ExamChecklist(), { numberOfGeneratedStudentExams: 1, numberOfExamsStarted: 0, numberOfExamsSubmitted: 0 });
                vi.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(baseline));
                const first = new Subject<ExamConductionCounts>();
                const fetchCounts = vi
                    .spyOn(examChecklistService, 'getExamConductionCounts')
                    .mockReturnValueOnce(first)
                    .mockReturnValue(of({ numberOfGeneratedStudentExams: 22, numberOfExamsStarted: 21, numberOfExamsSubmitted: 0 }));
                examChecklistComponentFixture.detectChanges();
                const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
                websocketService.emit('/topic/exam/1/started', undefined);
                for (let i = 0; i < 20; i++) {
                    vi.advanceTimersByTime(200);
                    websocketService.emit('/topic/exam/1/started', undefined);
                }
                expect(examChecklistService.getExamStatistics).toHaveBeenCalledTimes(1);
                expect(fetchCounts).toHaveBeenCalledTimes(1);
                expect(first.observed).toBe(true);
                first.next({ numberOfGeneratedStudentExams: 2, numberOfExamsStarted: 1, numberOfExamsSubmitted: 0 });
                expect(component.numberOfStarted()).toBe(21);
                first.complete();
                expect(fetchCounts).toHaveBeenCalledTimes(2);
                expect(component.numberOfStarted()).toBe(21);
                expect(component.numberOfGeneratedStudentExams()).toBe(22);
            } finally {
                examChecklistComponentFixture.destroy();
                vi.useRealTimers();
            }
        });

        it('should keep the number of generated student exams of a regular exam when a student starts', () => {
            // the student exams of a regular exam exist before anybody starts, so a start does not create one
            setUp({ examMode: ExamMode.REAL }, { numberOfGeneratedStudentExams: 12, numberOfExamsStarted: 3 });
            const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
            examChecklistComponentFixture.detectChanges();

            websocketService.emit('/topic/exam/1/started', undefined);

            expect(component.numberOfStarted()).toBe(4);
            expect(component.numberOfGeneratedStudentExams()).toBe(12);
            expect(examChecklistService.getExamStatistics).toHaveBeenCalledExactlyOnceWith(component.exam());
        });

        it.each([
            { description: 'is still running', startedSecondsAgo: 1800, gracePeriod: undefined, over: false },
            { description: 'has ended', startedSecondsAgo: 7200, gracePeriod: undefined, over: true },
            { description: 'is within its grace period', startedSecondsAgo: 3800, gracePeriod: 600, over: false },
            { description: 'is past its grace period', startedSecondsAgo: 5400, gracePeriod: 600, over: true },
        ])('should detect that the exam is over based on the longest working time ($description)', ({ startedSecondsAgo, gracePeriod, over }) => {
            const getLongestWorkingTime = mockLongestWorkingTime(3600);
            setUp({ startDate: dayjs().subtract(startedSecondsAgo, 'seconds'), gracePeriod });

            examChecklistComponentFixture.detectChanges();

            expect(getLongestWorkingTime).toHaveBeenCalledWith(1, 1);
            expect(component.longestWorkingTime).toBe(3600);
            expect(component.isExamOver()).toBe(over);
        });

        it('should not ask for the longest working time of an exam without ids', () => {
            const getLongestWorkingTime = mockLongestWorkingTime(3600);
            setUp({ id: undefined });

            examChecklistComponentFixture.detectChanges();

            expect(getLongestWorkingTime).not.toHaveBeenCalled();
            expect(component.isExamOver()).toBe(false);
        });

        it.each([
            { finishedByRound: [3, 2], unfinished: true },
            { finishedByRound: [1, 3], unfinished: false },
        ])('should flag unfinished assessments of the last correction round only after the exam is over ($finishedByRound)', ({ finishedByRound, unfinished }) => {
            mockLongestWorkingTime(3600);
            setUp({ startDate: dayjs().subtract(2, 'hours') }, { numberOfTotalExamAssessmentsFinishedByCorrectionRound: finishedByRound });

            examChecklistComponentFixture.detectChanges();

            expect(component.isExamOver()).toBe(true);
            expect(component.existsUnfinishedAssessments()).toBe(unfinished);
        });

        it('should not flag unfinished assessments while the exam is running', () => {
            mockLongestWorkingTime(3600);
            setUp({ startDate: dayjs().subtract(10, 'minutes') }, { numberOfTotalExamAssessmentsFinishedByCorrectionRound: [0, 0] });

            examChecklistComponentFixture.detectChanges();

            expect(component.isExamOver()).toBe(false);
            expect(component.existsUnfinishedAssessments()).toBe(false);
        });

        it.each([
            { textActive: false, disabledIds: [11] },
            { textActive: true, disabledIds: [] as number[] },
        ])('should list text exercises as disabled only when the text module is inactive (text active: $textActive)', ({ textActive, disabledIds }) => {
            getProfileInfoSub.mockReturnValue({ activeModuleFeatures: textActive ? [MODULE_FEATURE_TEXT] : [] });
            setUp({
                exerciseGroups: [
                    {
                        id: 1,
                        exercises: [
                            { id: 11, type: ExerciseType.TEXT },
                            { id: 12, type: ExerciseType.PROGRAMMING },
                        ],
                    },
                ] as ExerciseGroup[],
            });

            examChecklistComponentFixture.detectChanges();

            expect(component.disabledExercises().map((exercise) => exercise.id)).toEqual(disabledIds);
        });

        it.each(actions)('should reset the state and report success after $action', ({ action, busy, done, stats }) => {
            setUp({}, stats);
            examChecklistComponentFixture.detectChanges();
            const serviceSpy = spyOnAction(action, of(7));
            const successSpy = vi.spyOn(alertService, 'success').mockImplementation(() => undefined as any);
            expect(component[done]()).toBe(true);

            component[action]();

            expect(serviceSpy).toHaveBeenCalledWith(1, 1);
            expect(component[done]()).toBe(false);
            expect(component[busy]()).toBe(false);
            expect(successSpy).toHaveBeenCalledOnce();
            if (action === 'evaluateQuizExercises') {
                expect(successSpy.mock.calls[0][1]).toEqual({ number: 7 });
            }
        });

        it.each(actions)('should keep the open work and show the error when $action fails', ({ action, busy, done, stats }) => {
            setUp({}, stats);
            examChecklistComponentFixture.detectChanges();
            const error = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
            spyOnAction(
                action,
                throwError(() => error),
            );
            const errorSpy = vi.spyOn(alertService, 'error').mockImplementation(() => undefined as any);
            const successSpy = vi.spyOn(alertService, 'success');
            const dialogErrors: string[] = [];
            component.dialogError$.subscribe((message) => dialogErrors.push(message));

            component[action]();

            expect(component[done]()).toBe(true);
            expect(component[busy]()).toBe(false);
            expect(dialogErrors).toEqual([error.message]);
            expect(errorSpy).toHaveBeenCalledOnce();
            expect(successSpy).not.toHaveBeenCalled();
        });

        it.each(actions)('should report $action as an error and not call the server without ids', ({ action }) => {
            setUp({ id: undefined });
            examChecklistComponentFixture.detectChanges();
            const serviceSpy = spyOnAction(action);
            const captureSpy = vi.spyOn(ErrorReporting, 'captureException').mockImplementation(() => '');

            component[action]();

            expect(serviceSpy).not.toHaveBeenCalled();
            expect(captureSpy).toHaveBeenCalledOnce();
            expect((captureSpy.mock.calls[0][0] as Error).message).toContain('missing course ID or exam ID');
        });
    });

    describe('exam correction table', () => {
        const plagiarismCasesLink = 'a[href="/course-management/1/exams/1/plagiarism-cases"]';
        const assessmentDashboardLink = 'a[href="/course-management/1/exams/1/assessment-dashboard"]';
        const exerciseGroupsLink = 'a[href="/course-management/1/exams/1/exercise-groups"]';
        const plagiarismButtonKey = 'artemisApp.plagiarism.cases.plagiarismCases';
        const tableItem = 'artemisApp.examManagement.checklist.tableItem.';
        const descriptionItem = 'artemisApp.examManagement.checklist.descriptionItem.';
        const correctionSteps = ['suspiciousBehavior', 'assessAllSubmissions', 'plagiarismCases', 'publishResults', 'examReview', 'resolveComplaints', 'exportResults'];

        const render = ({ testExam = false, plagiarismActive = true, instructor = true } = {}) => {
            getProfileInfoSub.mockReturnValue({ activeModuleFeatures: plagiarismActive ? [MODULE_FEATURE_TEXT, MODULE_FEATURE_PLAGIARISM] : [MODULE_FEATURE_TEXT] });
            examChecklistComponentFixture.componentRef.setInput(
                'exam',
                Object.assign(new Exam(), { id: 1, examMode: testExam ? ExamMode.TEST : ExamMode.REAL, course: { id: 1, isAtLeastInstructor: instructor } }),
            );
            examChecklistComponentFixture.componentRef.setInput('getExamRoutesByIdentifier', (identifier: string) => ['/course-management', 1, 'exams', 1, identifier]);
            examChecklistComponentFixture.detectChanges();
            return examChecklistComponentFixture.nativeElement as HTMLElement;
        };

        const correctionRows = (element: HTMLElement) => {
            const link = element.querySelector(plagiarismCasesLink)!;
            expect(link).not.toBeNull();
            return Array.from(link.closest('table')!.querySelectorAll(':scope > tbody > tr'));
        };

        const translationKeys = (element: Element) => Array.from(element.querySelectorAll('[jhiTranslate]')).map((node) => node.getAttribute('jhiTranslate'));

        it('should list the plagiarism cases as the step right after assessing all submissions', () => {
            const rows = correctionRows(render());

            expect(rows.map((row) => row.querySelector('td')!.textContent!.trim())).toEqual(['1', '2', '3', '4', '5', '6', '7']);
            expect(rows[1].querySelector(assessmentDashboardLink)).not.toBeNull();
            expect(rows[2].querySelector(plagiarismCasesLink)).not.toBeNull();
            expect(rows[2].querySelector(assessmentDashboardLink)).toBeNull();
        });

        it('should keep the content of the steps in order after inserting the plagiarism cases', () => {
            const rows = correctionRows(render());

            expect(rows.map((row) => row.querySelector(':scope > td:nth-child(2) > span')!.getAttribute('jhiTranslate'))).toEqual(correctionSteps.map((step) => tableItem + step));
            expect(rows[3].querySelector('a[data-testid="editButton_publish"]')).not.toBeNull();
            expect(rows[4].querySelector('a[data-testid="editButton_review"]')).not.toBeNull();
            expect(rows[6].querySelector('a[href="/course-management/1/exams/1/scores"]')).not.toBeNull();
            expect(rows[2].querySelector('a[data-testid="editButton_publish"]')).toBeNull();
        });

        it('should describe the plagiarism cases step and label its button', () => {
            const row = correctionRows(render())[2];

            expect(translationKeys(row)).toEqual([tableItem + 'plagiarismCases', descriptionItem + 'plagiarismCases', plagiarismButtonKey]);
            expect(row.querySelectorAll('a')).toHaveLength(1);
        });

        it.each([
            { plagiarismActive: true, disabledClasses: [] as string[] },
            { plagiarismActive: false, disabledClasses: ['pointer-events-none', 'opacity-50'] },
        ])('should only enable the plagiarism cases link when the plagiarism module is active (active: $plagiarismActive)', ({ plagiarismActive, disabledClasses }) => {
            const element = render({ plagiarismActive });

            expect(component.plagiarismEnabled()).toBe(plagiarismActive);
            const link = element.querySelector(plagiarismCasesLink)!;
            const overlay = link.closest('jhi-feature-overlay')!;
            expect(overlay).not.toBeNull();
            // the overlay keeps the link in the DOM and only disables it through its wrapper classes
            const wrapper = link.parentElement!;
            expect(Array.from(wrapper.classList).filter((cssClass) => ['pointer-events-none', 'opacity-50'].includes(cssClass))).toEqual(disabledClasses);
            // the other steps are never disabled by the plagiarism module
            const assessmentOverlay = element.querySelector(assessmentDashboardLink)!.closest('jhi-feature-overlay');
            expect(assessmentOverlay).toBeNull();
        });

        it('should not offer the plagiarism cases for a test exam, which has no correction table', () => {
            const element = render({ testExam: true });

            // positive control: the rest of the checklist is rendered for the test exam
            expect(element.querySelector(exerciseGroupsLink)).not.toBeNull();
            expect(element.querySelector(plagiarismCasesLink)).toBeNull();
            expect(element.querySelector(assessmentDashboardLink)).toBeNull();
            expect(element.querySelector('jhi-feature-overlay')).toBeNull();
            const shownSteps = translationKeys(element).filter((key) => key!.startsWith(tableItem));
            expect(shownSteps).toContain(tableItem + 'exerciseGroups');
            correctionSteps.forEach((step) => expect(shownSteps).not.toContain(tableItem + step));
        });

        it('should show the correction table for a regular exam with the same fixture', () => {
            const element = render({ testExam: false });

            expect(element.querySelector(exerciseGroupsLink)).not.toBeNull();
            const shownSteps = translationKeys(element).filter((key) => key!.startsWith(tableItem));
            correctionSteps.forEach((step) => expect(shownSteps).toContain(tableItem + step));
        });

        it.each([
            { instructor: true, shown: true },
            { instructor: false, shown: false },
        ])('should render the checklist and its plagiarism cases step only for instructors (instructor: $instructor)', ({ instructor, shown }) => {
            const element = render({ instructor });

            expect(element.querySelector(plagiarismCasesLink) !== null).toBe(shown);
            expect(element.querySelector(exerciseGroupsLink) !== null).toBe(shown);
            expect(element.querySelectorAll('table').length > 0).toBe(shown);
        });

        it.each([
            { language: 'en', translations: enExam, title: 'Handle the plagiarism cases', buttonLabel: 'Plagiarism Cases', buttons: enPlagiarism },
            { language: 'de', translations: deExam, title: 'Bearbeitung der Plagiatsfälle', buttonLabel: 'Plagiatsfälle', buttons: dePlagiarism },
        ])('should define every text of the plagiarism cases step in $language', ({ translations, title, buttonLabel, buttons }) => {
            const row = correctionRows(render())[2];
            const resolve = (key: string) =>
                [translations, buttons]
                    .map((dictionary) => key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], dictionary))
                    .find((text) => text !== undefined);

            const keys = translationKeys(row) as string[];
            expect(keys).toHaveLength(3);
            keys.forEach((key) => {
                expect(typeof resolve(key), key).toBe('string');
                expect(resolve(key) as string, key).not.toBe('');
            });
            expect(resolve(tableItem + 'plagiarismCases')).toBe(title);
            expect(resolve(plagiarismButtonKey)).toBe(buttonLabel);
        });

        it('should provide the same checklist steps in English and German', () => {
            const steps = (translations: unknown) => (translations as typeof enExam).artemisApp.examManagement.checklist;

            expect(Object.keys(steps(deExam).tableItem)).toEqual(Object.keys(steps(enExam).tableItem));
            expect(Object.keys(steps(deExam).descriptionItem)).toEqual(Object.keys(steps(enExam).descriptionItem));
            expect(steps(enExam).tableItem.plagiarismCases).not.toBe(steps(deExam).tableItem.plagiarismCases);
            expect(steps(enExam).descriptionItem.plagiarismCases).not.toBe(steps(deExam).descriptionItem.plagiarismCases);
        });
    });
});
