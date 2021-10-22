import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ActivatedRoute, convertToParamMap, RouterModule, UrlSegment } from '@angular/router';
import { of } from 'rxjs';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { TranslateModule } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { DeviceDetectorService } from 'ngx-device-detector';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { SecondCorrectionEnableButtonComponent } from 'app/exercises/shared/dashboards/tutor/second-correction-button/second-correction-enable-button.component';
import { AssessmentDashboardComponent } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard.component';
import { AssessmentDashboardInformationComponent } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard-information.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { NgModel } from '@angular/forms';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('AssessmentDashboardInformationComponent', () => {
    let comp: AssessmentDashboardComponent;
    let fixture: ComponentFixture<AssessmentDashboardComponent>;

    let examManagementService: ExamManagementService;
    let getExamWithInterestingExercisesForAssessmentDashboardStub: SinonStub;
    let getStatsForExamAssessmentDashboardStub: SinonStub;

    let courseManagementService: CourseManagementService;
    let getCourseWithInterestingExercisesForTutorsStub: SinonStub;
    let getStatsForTutorsStub: SinonStub;

    let exerciseService: ExerciseService;
    let toggleSecondCorrectionStub: SinonStub;

    let accountService: AccountService;
    let isAtLeastInstructorInCourseStub: SinonStub;

    const programmingExercise = {
        id: 16,
        type: ExerciseType.PROGRAMMING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as ProgrammingExercise;
    const modelingExercise = {
        id: 17,
        type: ExerciseType.MODELING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
    } as ModelingExercise;
    const textExercise = {
        id: 18,
        type: ExerciseType.TEXT,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as TextExercise;
    const fileUploadExercise = {
        id: 19,
        type: ExerciseType.FILE_UPLOAD,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
        numberOfSubmissions: { inTime: 5, late: 0 },
        numberOfAssessmentsOfCorrectionRounds: [{ inTime: 5, late: 0 }],
        totalNumberOfAssessments: { inTime: 5, late: 0 },
        numberOfOpenComplaints: 0,
        numberOfOpenMoreFeedbackRequests: 0,
    } as FileUploadExercise;

    const course = { id: 10, exercises: [programmingExercise, modelingExercise, textExercise, modelingExercise] } as Course;
    const exercises = [programmingExercise, fileUploadExercise, modelingExercise, textExercise];
    const exerciseGroup1 = { id: 141, exercises: [programmingExercise, modelingExercise] } as ExerciseGroup;
    const exerciseGroup2 = { id: 142, exercises: [textExercise, fileUploadExercise] } as ExerciseGroup;
    const exam = { id: 20, exerciseGroups: [exerciseGroup1, exerciseGroup2], course: { id: 10 } } as Exam;

    const numberOfAssessmentsOfCorrectionRounds = [{ inTime: 1, late: 1 } as DueDateStat, { inTime: 8, late: 0 } as DueDateStat];
    const tutorLeaderboardEntries = [] as TutorLeaderboardElement[];
    const courseTutorStats = {
        numberOfSubmissions: { inTime: 5, late: 0 } as DueDateStat,
        totalNumberOfAssessments: { inTime: 3, late: 0 } as DueDateStat,
        numberOfAssessmentsOfCorrectionRounds,
        numberOfComplaints: 0,
        numberOfMoreFeedbackRequests: 0,
        numberOfOpenMoreFeedbackRequests: 0,
        numberOfAssessmentLocks: 2,
        tutorLeaderboardEntries,
        numberOfStudents: 5,
        numberOfAutomaticAssistedAssessments: { inTime: 0, late: 0 },
        numberOfOpenComplaints: 0,
        totalNumberOfAssessmentLocks: 2,
    } as StatsForDashboard;

    const route = {
        snapshot: {
            paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }),
            url: { path: '/course-management/10/assessment-dashboard', parameterMap: {}, parameters: {} } as UrlSegment,
        },
    } as any as ActivatedRoute;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterModule, TranslateModule.forRoot()],
            declarations: [
                AssessmentDashboardComponent,
                MockComponent(TutorLeaderboardComponent),
                MockComponent(TutorParticipationGraphComponent),
                MockComponent(AssessmentDashboardInformationComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(SecondCorrectionEnableButtonComponent),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(FaIconComponent),
                MockComponent(AlertComponent),
                MockDirective(SortDirective),
                MockDirective(NgModel),
                MockDirective(NgbTooltip),
                MockComponent(NotReleasedTagComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockDirective(MockHasAnyAuthorityDirective),
            ],
            providers: [
                JhiLanguageHelper,
                DeviceDetectorService,
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentDashboardComponent);
                comp = fixture.componentInstance;

                examManagementService = TestBed.inject(ExamManagementService);
                courseManagementService = TestBed.inject(CourseManagementService);
                exerciseService = TestBed.inject(ExerciseService);
                accountService = TestBed.inject(AccountService);

                getExamWithInterestingExercisesForAssessmentDashboardStub = stub(examManagementService, 'getExamWithInterestingExercisesForAssessmentDashboard');
                getStatsForExamAssessmentDashboardStub = stub(examManagementService, 'getStatsForExamAssessmentDashboard');
                toggleSecondCorrectionStub = stub(exerciseService, 'toggleSecondCorrection');

                getCourseWithInterestingExercisesForTutorsStub = stub(courseManagementService, 'getCourseWithInterestingExercisesForTutors');
                getStatsForTutorsStub = stub(courseManagementService, 'getStatsForTutors');

                isAtLeastInstructorInCourseStub = stub(accountService, 'isAtLeastInstructorInCourse');
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    describe('ngOnInit', () => {
        it('should loadAll for course', () => {
            const newRoute = {
                snapshot: {
                    paramMap: convertToParamMap({ courseId: course.id }),
                    url: { path: '/course-management/10/assessment-dashboard', parameterMap: {}, parameters: {} } as UrlSegment,
                },
            } as any as ActivatedRoute;
            const activatedRoute: ActivatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
            activatedRoute.snapshot = newRoute.snapshot;
            TestBed.inject(ActivatedRoute);

            // TODO: for some very odd reason those two stubs do not work. I could not figure out why. This test tests nothing atm.
            getCourseWithInterestingExercisesForTutorsStub = getCourseWithInterestingExercisesForTutorsStub.returns(of({ body: course }));
            getStatsForTutorsStub = getStatsForTutorsStub.returns(of(courseTutorStats));

            // let getCourseWithReturnValue: Observable<Course> = of({});
            //  getCourseWithInterestingExercisesForTutorsStub.returns(getCourseWithReturnValue);
            // getStatsForTutorsStub = stub(courseManagementService, 'getStatsForTutors').returns(of(new HttpResponse({body: courseTutorStats })));
            // sinon.replace(courseManagementService, 'getStatsForTutors', sinon.fake.returns(of(new HttpResponse({body: courseTutorStats }))));

            comp.ngOnInit();
            // expect(getCourseWithInterestingExercisesForTutorsStub).to.have.been.called;
            // expect(getStatsForTutorsStub).to.have.been.called;
        });
        it('should loadAll for exam', () => {
            getExamWithInterestingExercisesForAssessmentDashboardStub.returns(of({ body: exam }));
            getStatsForExamAssessmentDashboardStub.returns(of(courseTutorStats));
            isAtLeastInstructorInCourseStub.returns(of(true));

            comp.ngOnInit();
            expect(getExamWithInterestingExercisesForAssessmentDashboardStub).to.have.been.called;
            expect(getStatsForExamAssessmentDashboardStub).to.have.been.called;
            expect(comp.exam).to.deep.equal(exam);
            expect(comp.unfinishedExercises.length).to.be.equal(3);
            expect(comp.finishedExercises.length).to.equal(1);
        });
    });
    describe('toggle second correctionRound', () => {
        it('should toggle correctionRound for exercises', () => {
            comp.exercises = exercises;
            toggleSecondCorrectionStub.returns(of(true));
            comp.toggleSecondCorrection(fileUploadExercise.id!);
            expect(comp.exercises.find((exercise) => exercise.id === fileUploadExercise.id!)!.secondCorrectionEnabled).to.be.true;
            toggleSecondCorrectionStub.returns(of(false));
            comp.toggleSecondCorrection(fileUploadExercise.id!);
            expect(comp.exercises.find((exercise) => exercise.id === fileUploadExercise.id!)!.secondCorrectionEnabled).to.be.false;
            expect(comp.toggelingSecondCorrectionButton).to.be.false;
        });
    });
    describe('getAssessmentDashboardLinkForExercise', () => {
        beforeEach(() => {
            comp.courseId = course.id!;
            comp.examId = exam.id!;
        });
        it('should getAssessmentDashboardLinkForExercise for exam', () => {
            comp.isExamMode = true;
            const link = ['/course-management', comp.courseId.toString(), 'exams', comp.examId.toString(), 'assessment-dashboard', fileUploadExercise.id!.toString()];
            expect(comp.getAssessmentDashboardLinkForExercise(fileUploadExercise)).to.deep.equal(link);
        });
        it('should getAssessmentDashboardLinkForExercise for exam and testrun', () => {
            comp.isExamMode = true;
            comp.isTestRun = true;
            const link = ['/course-management', comp.courseId.toString(), 'exams', comp.examId.toString(), 'test-assessment-dashboard', fileUploadExercise.id!.toString()];
            expect(comp.getAssessmentDashboardLinkForExercise(fileUploadExercise)).to.deep.equal(link);
        });
        it('should getAssessmentDashboardLinkForExercise for course', () => {
            comp.isExamMode = false;
            const link = ['/course-management', comp.courseId.toString(), 'assessment-dashboard', fileUploadExercise.id!.toString()];
            expect(comp.getAssessmentDashboardLinkForExercise(fileUploadExercise)).to.deep.equal(link);
        });
    });
});
