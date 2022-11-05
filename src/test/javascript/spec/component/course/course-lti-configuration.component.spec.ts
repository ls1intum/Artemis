import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { Router } from '@angular/router';
import { MockDirective, MockPipe, MockComponent, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/course-lti-configuration.component';
import { SortService } from 'app/shared/service/sort.service';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';
import { mockedActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('Course LTI Configuration Component', () => {
    let comp: CourseLtiConfigurationComponent;
    let fixture: ComponentFixture<CourseLtiConfigurationComponent>;
    let courseService: CourseManagementService;
    let sortService: SortService;

    let findWithExercisesStub: jest.SpyInstance;

    const onlineCourseConfiguration = {
        id: 1,
        ltiKey: 'key',
        ltiSecret: 'secret',
        userPrefix: 'prefix',
        registrationId: 'regId',
        clientId: 'clientId',
    } as OnlineCourseConfiguration;
    const course = {
        id: 123,
        title: 'Course Title',
        isAtLeastInstructor: true,
        endDate: dayjs().subtract(5, 'minutes'),
        courseArchivePath: 'some-path',
        onlineCourseConfiguration,
    } as Course;

    const textExercise = new TextExercise(course, undefined);
    const programmingExercise = new ProgrammingExercise(course, undefined);
    const quizExercise = new QuizExercise(course, undefined);
    const fileUploadExercise = new FileUploadExercise(course, undefined);
    const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
    const courseWithExercises = new Course();
    courseWithExercises.exercises = [textExercise, programmingExercise, quizExercise, fileUploadExercise, modelingExercise];

    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;
    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                CourseLtiConfigurationComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockComponent(FaIconComponent),
            ],
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(SortService),
                { provide: Router, useValue: router },
                mockedActivatedRoute(
                    {},
                    {},
                    {
                        course,
                    },
                    {},
                ),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseLtiConfigurationComponent);
                comp = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                sortService = TestBed.inject(SortService);
                findWithExercisesStub = jest.spyOn(courseService, 'findWithExercises');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseLtiConfigurationComponent).not.toBeNull();
    });

    describe('OnInit', () => {
        it('should load course and exercises', () => {
            findWithExercisesStub.mockReturnValue(
                of(
                    new HttpResponse({
                        body: courseWithExercises,
                        status: 200,
                    }),
                ),
            );
            comp.ngOnInit();

            expect(comp.course).toEqual(course);
            expect(comp.onlineCourseConfiguration).toEqual(course.onlineCourseConfiguration);
            expect(comp.exercises).toEqual(courseWithExercises.exercises);
            expect(findWithExercisesStub).toHaveBeenCalledOnce();
        });
    });
});
