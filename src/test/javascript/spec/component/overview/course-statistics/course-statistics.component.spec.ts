import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import * as moment from 'moment';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ChartsModule } from 'ng2-charts';
import { TreeviewModule } from 'ngx-treeview';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ActivatedRoute } from '@angular/router';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { CourseLearningGoalsComponent } from 'app/overview/course-learning-goals/course-learning-goals.component';

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
            dueDate: moment('2019-06-17T09:47:12+02:00'),
            assessmentDueDate: moment('2019-06-17T09:55:17+02:00'),
            maxScore: 12.0,
            studentParticipations: [
                {
                    id: 248,
                    initializationState: 'FINISHED',
                    initializationDate: moment('2019-06-17T09:29:34.908+02:00'),
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
            numberOfAssessments: new DueDateStat(),
            numberOfComplaints: 0,
            presentationScoreEnabled: true,
        },
        {
            type: 'modeling',
            id: 193,
            title: 'test 17.06. 2',
            dueDate: moment('2019-06-17T17:50:08+02:00'),
            assessmentDueDate: moment('2019-06-17T17:51:13+02:00'),
            maxScore: 12.0,
            studentParticipations: [
                {
                    id: 249,
                    initializationState: 'FINISHED',
                    initializationDate: moment('2019-06-18T10:53:27.997+02:00'),
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
            numberOfAssessments: new DueDateStat(),
            numberOfComplaints: 0,
        },
        {
            type: 'modeling',
            id: 194,
            title: 'test 18.06. 1',
            dueDate: moment('2019-06-18T07:56:41+02:00'),
            maxScore: 12.0,
            studentParticipations: [],
            diagramType: 'ClassDiagram',
            numberOfSubmissions: new DueDateStat(),
            numberOfAssessments: new DueDateStat(),
            numberOfComplaints: 0,
        },
        {
            type: 'modeling',
            id: 191,
            title: 'Until 18:20',
            dueDate: moment('2019-06-16T18:15:03+02:00'),
            assessmentDueDate: moment('2019-06-16T18:30:57+02:00'),
            maxScore: 12.0,
            studentParticipations: [
                {
                    id: 246,
                    initializationState: 'FINISHED',
                    initializationDate: moment('2019-06-16T18:10:28.293+02:00'),
                    results: [
                        {
                            id: 231,
                            resultString: '11 of 12 points',
                            completionDate: moment('2019-06-17T09:30:17.761+02:00'),
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
            numberOfAssessments: new DueDateStat(),
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

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, TreeviewModule.forRoot(), RouterTestingModule.withRoutes([]), ArtemisSharedModule, ChartsModule],
            declarations: [CourseStatisticsComponent, MockComponent(CourseLearningGoalsComponent)],
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

    it('should group all exercises', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        spyOn(courseScoreCalculationService, 'getCourse').and.returnValue(courseToAdd);
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
});
