import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import dayjs from 'dayjs';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ChartsModule } from 'ng2-charts';
import { TreeviewModule } from 'ngx-treeview';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { ActivatedRoute } from '@angular/router';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { CourseLearningGoalsComponent } from 'app/overview/course-learning-goals/course-learning-goals.component';
import { TextExercise } from 'app/entities/text-exercise.model';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExerciseScoresChartComponent } from 'app/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseStatisticsComponent', () => {
    let comp: CourseStatisticsComponent;
    let fixture: ComponentFixture<CourseStatisticsComponent>;
    let courseScoreCalculationService: CourseScoreCalculationService;

    const modelingExercises = [
        {
            type: 'modeling',
            id: 192,
            title: 'test 17.06. 1',
            dueDate: dayjs('2019-06-17T09:47:12+02:00'),
            assessmentDueDate: dayjs('2019-06-17T09:55:17+02:00'),
            includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
            maxPoints: 12.0,
            studentParticipations: [
                {
                    id: 248,
                    initializationState: 'FINISHED',
                    initializationDate: dayjs('2019-06-17T09:29:34.908+02:00'),
                    presentationScore: 2,
                    student: {
                        id: 9,
                        login: 'artemis_test_user_1',
                        firstName: 'Artemis Test User 1',
                        email: 'krusche+testuser_1@in.tum.de',
                        activated: true,
                        langKey: 'en',
                    },
                },
            ],
            diagramType: 'ClassDiagram',
            numberOfSubmissions: new DueDateStat(),
            totalNumberOfAssessments: new DueDateStat(),
            numberOfComplaints: 0,
            presentationScoreEnabled: true,
        },
        {
            type: 'modeling',
            id: 193,
            title: 'test 17.06. 2',
            dueDate: dayjs('2019-06-17T17:50:08+02:00'),
            assessmentDueDate: dayjs('2019-06-17T17:51:13+02:00'),
            includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
            maxPoints: 12.0,
            studentParticipations: [
                {
                    id: 249,
                    initializationState: 'FINISHED',
                    initializationDate: dayjs('2019-06-18T10:53:27.997+02:00'),
                    student: {
                        id: 9,
                        login: 'artemis_test_user_1',
                        firstName: 'Artemis Test User 1',
                        email: 'krusche+testuser_1@in.tum.de',
                        activated: true,
                        langKey: 'en',
                    },
                },
            ],
            diagramType: 'ClassDiagram',
            numberOfSubmissions: new DueDateStat(),
            totalNumberOfAssessments: new DueDateStat(),
            numberOfComplaints: 0,
        },
        {
            type: 'modeling',
            id: 194,
            title: 'test 18.06. 1',
            dueDate: dayjs('2019-06-18T07:56:41+02:00'),
            includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
            maxPoints: 12.0,
            studentParticipations: [],
            diagramType: 'ClassDiagram',
            numberOfSubmissions: new DueDateStat(),
            totalNumberOfAssessments: new DueDateStat(),
            numberOfComplaints: 0,
        },
        {
            type: 'modeling',
            id: 191,
            title: 'Until 18:20',
            dueDate: dayjs('2019-06-16T18:15:03+02:00'),
            includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
            assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
            maxPoints: 12.0,
            studentParticipations: [
                {
                    id: 246,
                    initializationState: 'FINISHED',
                    initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
                    results: [
                        {
                            id: 231,
                            resultString: '11 of 12 points',
                            completionDate: dayjs('2019-06-17T09:30:17.761+02:00'),
                            successful: false,
                            score: 92,
                            rated: true,
                            hasFeedback: false,
                            assessmentType: 'MANUAL',
                            hasComplaint: false,
                        },
                    ],
                    student: {
                        id: 9,
                        login: 'artemis_test_user_1',
                        firstName: 'Artemis Test User 1',
                        email: 'krusche+testuser_1@in.tum.de',
                        activated: true,
                        langKey: 'en',
                    },
                },
            ],
            diagramType: 'ClassDiagram',
            numberOfSubmissions: new DueDateStat(),
            totalNumberOfAssessments: new DueDateStat(),
            numberOfComplaints: 0,
        },
        {
            type: 'modeling',
            id: 195,
            title: 'Until 18:20 too',
            includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
            dueDate: dayjs('2019-06-16T18:15:03+02:00'),
            assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
            maxPoints: 12.0,
            studentParticipations: [
                {
                    id: 249,
                    initializationState: 'FINISHED',
                    initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
                    results: [
                        {
                            id: 230,
                            resultString: '9 of 12 points',
                            completionDate: dayjs('2019-06-17T09:30:17.761+02:00'),
                            successful: false,
                            score: 75,
                            rated: true,
                            hasFeedback: false,
                            assessmentType: 'MANUAL',
                            hasComplaint: false,
                        },
                    ],
                    student: {
                        id: 9,
                        login: 'artemis_test_user_1',
                        firstName: 'Artemis Test User 1',
                        email: 'krusche+testuser_1@in.tum.de',
                        activated: true,
                        langKey: 'en',
                    },
                },
            ],
            diagramType: 'ClassDiagram',
            numberOfSubmissions: new DueDateStat(),
            totalNumberOfAssessments: new DueDateStat(),
            numberOfComplaints: 0,
        },
    ] as ModelingExercise[];

    const course = new Course();
    course.id = 64;
    course.title = 'Checking statistics';
    course.description = 'Testing the statistics view';
    course.shortName = 'CHS';
    course.studentGroupName = 'jira-users';
    course.teachingAssistantGroupName = 'artemis-dev';
    course.instructorGroupName = 'artemis-dev';
    course.onlineCourse = false;
    course.registrationEnabled = false;
    course.exercises = [];
    course.presentationScore = 1;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TreeviewModule.forRoot(), RouterTestingModule.withRoutes([]), ArtemisSharedModule, ChartsModule],
            declarations: [CourseStatisticsComponent, MockComponent(CourseLearningGoalsComponent), MockComponent(ExerciseScoresChartComponent)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: {
                                subscribe: (fn: (value: any) => void) => fn(1),
                            },
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseStatisticsComponent);
                comp = fixture.componentInstance;
                courseScoreCalculationService = TestBed.inject(CourseScoreCalculationService);
            });
    });

    afterEach(function () {
        // has to be done so the component can cleanup properly
        jest.spyOn(comp, 'ngOnDestroy').mockImplementation();
        fixture.destroy();
    });

    it('should group all exercises', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        jest.spyOn(courseScoreCalculationService, 'getCourse').mockReturnValue(courseToAdd);
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.groupedExercises.length).to.equal(1);
        const modelingWrapper = fixture.debugElement.query(By.css('#modeling-wrapper'));
        expect(modelingWrapper.query(By.css('h2')).nativeElement.textContent).to.exist;
        expect(modelingWrapper.query(By.css('#absolute-score')).nativeElement.textContent).to.exist;
        expect(modelingWrapper.query(By.css('#reachable-score')).nativeElement.textContent).to.exist;
        expect(modelingWrapper.query(By.css('#max-score')).nativeElement.textContent).to.exist;
        expect(fixture.debugElement.query(By.css('#presentation-score')).nativeElement.textContent).to.exist;
        const exercise: any = comp.groupedExercises[0];
        expect(exercise.presentationScore).to.equal(2);
        expect(exercise.notGraded.tooltips).to.include.members(['artemisApp.courseOverview.statistics.exerciseNotGraded']);
        expect(exercise.scores.tooltips).to.include.members(['artemisApp.courseOverview.statistics.exerciseAchievedScore']);
        expect(exercise.missedScores.tooltips).to.include.members(['artemisApp.courseOverview.statistics.exerciseParticipatedAfterDueDate']);
        expect(exercise.missedScores.tooltips).to.include.members(['artemisApp.courseOverview.statistics.exerciseNotParticipated']);
        expect(exercise.missedScores.tooltips).to.include.members(['artemisApp.courseOverview.statistics.exerciseMissedScore']);
    });

    it('should calculate scores correctly', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        jest.spyOn(courseScoreCalculationService, 'getCourse').mockReturnValue(courseToAdd);
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.groupedExercises.length).to.equal(1);
        let exercise: any = comp.groupedExercises[0];
        expect(exercise.absoluteScore).to.equal(20);
        expect(exercise.reachableScore).to.equal(60);
        expect(exercise.overallMaxPoints).to.equal(60);

        const newExercise = [
            {
                type: 'text',
                id: 200,
                title: 'Until 18:20 too',
                dueDate: dayjs('2019-06-16T18:15:03+02:00'),
                assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
                maxPoints: 10.0,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                studentParticipations: [
                    {
                        id: 289,
                        initializationState: 'FINISHED',
                        initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
                        results: [
                            {
                                id: 222,
                                resultString: '5.5 of 10 points',
                                completionDate: dayjs('2019-06-17T09:30:17.761+02:00'),
                                successful: false,
                                score: 55,
                                rated: true,
                                hasFeedback: false,
                                assessmentType: 'MANUAL',
                                hasComplaint: false,
                            },
                        ],
                        student: {
                            id: 9,
                            login: 'artemis_test_user_1',
                            firstName: 'Artemis Test User 1',
                            email: 'krusche+testuser_1@in.tum.de',
                            activated: true,
                            langKey: 'en',
                        },
                    },
                ],
                diagramType: 'ClassDiagram',
                numberOfSubmissions: new DueDateStat(),
                totalNumberOfAssessments: new DueDateStat(),
                numberOfComplaints: 0,
            } as unknown as TextExercise,
            {
                type: 'text',
                id: 999,
                title: 'Until 18:20 tooo',
                dueDate: dayjs('2019-06-16T18:15:03+02:00'),
                assessmentDueDate: dayjs().add(1, 'days'),
                maxPoints: 10.0,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                studentParticipations: [
                    {
                        id: 888,
                        initializationState: 'FINISHED',
                        initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
                        student: {
                            id: 9,
                            login: 'artemis_test_user_1',
                            firstName: 'Artemis Test User 1',
                            email: 'krusche+testuser_1@in.tum.de',
                            activated: true,
                            langKey: 'en',
                        },
                    },
                ],
                diagramType: 'ClassDiagram',
                numberOfSubmissions: new DueDateStat(),
                totalNumberOfAssessments: new DueDateStat(),
                numberOfComplaints: 0,
            } as unknown as TextExercise,
        ];
        courseToAdd.exercises = [...modelingExercises, ...newExercise];
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.detectChanges();

        // check that exerciseGroup scores are untouched
        exercise = comp.groupedExercises[0];
        expect(exercise.absoluteScore).to.equal(20);
        expect(exercise.reachableScore).to.equal(60);
        expect(exercise.overallMaxPoints).to.equal(60);

        // check that overall course score is adapted accordingly -> one exercise after assessment, one before
        expect(comp.overallPoints).to.equal(25.5);
        expect(comp.reachablePoints).to.equal(70);
        expect(comp.overallMaxPoints).to.equal(80);

        // check that html file displays the correct elements
        let debugElement = fixture.debugElement.query(By.css('#absolute-course-score'));
        expect(debugElement.nativeElement.textContent).to.equal('artemisApp.courseOverview.statistics.yourPoints');
        debugElement = fixture.debugElement.query(By.css('#reachable-course-score'));
        expect(debugElement.nativeElement.textContent).to.equal(' artemisApp.courseOverview.statistics.reachablePoints ');
        debugElement = fixture.debugElement.query(By.css('#max-course-score'));
        expect(debugElement.nativeElement.textContent).to.equal(' artemisApp.courseOverview.statistics.totalPoints ');
    });
});
