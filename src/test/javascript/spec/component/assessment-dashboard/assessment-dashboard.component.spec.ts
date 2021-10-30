import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, convertToParamMap, RouterModule, UrlSegment } from '@angular/router';
import { Observable, of } from 'rxjs';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
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
import { AlertComponent } from 'app/shared/alert/alert.component';
import { NgModel } from '@angular/forms';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortService } from 'app/shared/service/sort.service';
import { HttpResponse } from '@angular/common/http';

describe('AssessmentDashboardInformationComponent', () => {
    let comp: AssessmentDashboardComponent;
    let fixture: ComponentFixture<AssessmentDashboardComponent>;

    let examManagementService: ExamManagementService;
    let getExamWithInterestingExercisesForAssessmentDashboardStub: jest.SpyInstance;
    let getStatsForExamAssessmentDashboardStub: jest.SpyInstance;

    let courseService: CourseManagementService;
    let getCourseWithInterestingExercisesForTutorsStub: jest.SpyInstance;
    let getStatsForTutorsStub: jest.SpyInstance;

    let exerciseService: ExerciseService;

    let accountService: AccountService;

    let sortService: SortService;

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
            imports: [ArtemisTestModule, RouterModule],
            declarations: [
                AssessmentDashboardComponent,
                MockComponent(TutorLeaderboardComponent),
                MockComponent(TutorParticipationGraphComponent),
                MockComponent(AssessmentDashboardInformationComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(SecondCorrectionEnableButtonComponent),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(AlertComponent),
                MockDirective(SortDirective),
                MockDirective(NgModel),
                MockDirective(NgbTooltip),
                MockComponent(NotReleasedTagComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockDirective(MockHasAnyAuthorityDirective),
            ],
            providers: [
                MockProvider(JhiLanguageHelper),
                MockProvider(DeviceDetectorService),
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideComponent(AssessmentDashboardComponent, {
                set: {
                    providers: [{ provide: CourseManagementService, useClass: CourseManagementService }],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentDashboardComponent);
                comp = fixture.componentInstance;
                courseService = fixture.debugElement.injector.get(CourseManagementService);

                sortService = TestBed.inject(SortService);

                examManagementService = TestBed.inject(ExamManagementService);
                getExamWithInterestingExercisesForAssessmentDashboardStub = jest
                    .spyOn(examManagementService, 'getExamWithInterestingExercisesForAssessmentDashboard')
                    .mockReturnValue(of({ body: exam }) as Observable<HttpResponse<Exam>>);
                getStatsForExamAssessmentDashboardStub = jest
                    .spyOn(examManagementService, 'getStatsForExamAssessmentDashboard')
                    .mockReturnValue(of({ body: courseTutorStats }) as Observable<HttpResponse<StatsForDashboard>>);

                exerciseService = TestBed.inject(ExerciseService);

                getCourseWithInterestingExercisesForTutorsStub = jest
                    .spyOn(courseService, 'getCourseWithInterestingExercisesForTutors')
                    .mockReturnValue(of({ body: course }) as Observable<HttpResponse<Course>>);
                getStatsForTutorsStub = jest
                    .spyOn(courseService, 'getStatsForTutors')
                    .mockReturnValue(of({ body: courseTutorStats }) as Observable<HttpResponse<StatsForDashboard>>);

                accountService = TestBed.inject(AccountService);
                jest.spyOn(accountService, 'isAtLeastInstructorInCourse');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should init component correctly for exam', fakeAsync(() => {
        comp.ngOnInit();
        tick();

        expect(comp.isExamMode).toBe(true);
        expect(comp.courseId).toEqual(10);
        expect(comp.examId).toEqual(20);
        expect(comp.isTestRun).toBe(false);
        expect(getExamWithInterestingExercisesForAssessmentDashboardStub).toHaveBeenCalledOnce();
        expect(getStatsForExamAssessmentDashboardStub).toHaveBeenCalledOnce();
        expect(comp.exam).toEqual(exam);
        expect(comp.showFinishedExercises).toBe(true);
        expect(comp.unfinishedExercises).toHaveLength(3);
        expect(comp.finishedExercises).toHaveLength(1);
    }));
    it('should init component correctly for course', fakeAsync(() => {
        const newRoute = {
            snapshot: {
                paramMap: convertToParamMap({ courseId: course.id }),
                url: { path: '/course-management/10/assessment-dashboard', parameterMap: {}, parameters: {} } as UrlSegment,
            },
        } as any as ActivatedRoute;
        const activatedRoute: ActivatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
        activatedRoute.snapshot = newRoute.snapshot;
        TestBed.inject(ActivatedRoute);

        comp.ngOnInit();
        tick();

        expect(comp.isExamMode).toBe(false);
        expect(comp.courseId).toEqual(10);
        expect(comp.examId).toBe(0);
        expect(getCourseWithInterestingExercisesForTutorsStub).toHaveBeenCalledOnce();
        expect(getStatsForTutorsStub).toHaveBeenCalledOnce();
        expect(comp.course).toEqual(Course.from(course));
        expect(comp.unfinishedExercises).toHaveLength(4);
        expect(comp.finishedExercises).toHaveLength(0);
    }));
    it('should toggle correctionRound for exercises', () => {
        comp.exercises = exercises;
        const toggleSecondCorrectionStub = jest.spyOn(exerciseService, 'toggleSecondCorrection');
        toggleSecondCorrectionStub.mockReturnValue(of(true));
        comp.toggleSecondCorrection(fileUploadExercise.id!);
        expect(comp.exercises.find((exercise) => exercise.id === fileUploadExercise.id!)!.secondCorrectionEnabled).toBe(true);
        toggleSecondCorrectionStub.mockReturnValue(of(false));
        comp.toggleSecondCorrection(fileUploadExercise.id!);
        expect(comp.exercises.find((exercise) => exercise.id === fileUploadExercise.id!)!.secondCorrectionEnabled).toBe(false);
        expect(comp.toggelingSecondCorrectionButton).toBe(false);
    });
    it('should getAssessmentDashboardLinkForExercise for exam', () => {
        comp.courseId = course.id!;
        comp.examId = exam.id!;
        comp.isExamMode = true;
        const link = ['/course-management', comp.courseId.toString(), 'exams', comp.examId.toString(), 'assessment-dashboard', fileUploadExercise.id!.toString()];
        expect(comp.getAssessmentDashboardLinkForExercise(fileUploadExercise)).toEqual(link);
    });
    it('should getAssessmentDashboardLinkForExercise for exam and testrun', () => {
        comp.courseId = course.id!;
        comp.examId = exam.id!;
        comp.isExamMode = true;
        comp.isTestRun = true;
        const link = ['/course-management', comp.courseId.toString(), 'exams', comp.examId.toString(), 'test-assessment-dashboard', fileUploadExercise.id!.toString()];
        expect(comp.getAssessmentDashboardLinkForExercise(fileUploadExercise)).toEqual(link);
    });
    it('should getAssessmentDashboardLinkForExercise for course', () => {
        comp.courseId = course.id!;
        comp.examId = exam.id!;
        comp.isExamMode = false;
        const link = ['/course-management', comp.courseId.toString(), 'assessment-dashboard', fileUploadExercise.id!.toString()];
        expect(comp.getAssessmentDashboardLinkForExercise(fileUploadExercise)).toEqual(link);
    });
    it('should update exercises with finished exercises', () => {
        comp.showFinishedExercises = true;
        comp.unfinishedExercises = [textExercise];
        comp.finishedExercises = [programmingExercise];
        comp.updateExercises();
        expect(comp.exercises).toEqual([textExercise, programmingExercise]);
    });
    it('should update exercises without finished exercises', () => {
        comp.showFinishedExercises = false;
        comp.unfinishedExercises = [textExercise];
        comp.updateExercises();
        expect(comp.exercises).toEqual([textExercise]);
    });
    it('should toggle showing finished exercises', () => {
        const updateExercisesSpy = jest.spyOn(comp, 'updateExercises');
        comp.showFinishedExercises = false;
        comp.triggerFinishedExercises();
        expect(comp.showFinishedExercises).toBe(true);
        expect(updateExercisesSpy).toHaveBeenCalledOnce();
    });
    it('should sort rows', () => {
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');
        comp.exercises = [textExercise];
        comp.exercisesSortingPredicate = 'assessmentDueDate';
        comp.exercisesReverseOrder = false;

        comp.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledOnce();
        expect(sortServiceSpy).toHaveBeenCalledWith([textExercise], 'assessmentDueDate', false);
    });
});
