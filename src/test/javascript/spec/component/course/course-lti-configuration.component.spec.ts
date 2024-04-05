import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CopyIconButtonComponent } from 'app/shared/components/copy-icon-button/copy-icon-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/course-lti-configuration.component';
import { SortService } from 'app/shared/service/sort.service';
import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';
import { mockedActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { ArtemisTestModule } from '../../test.module';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { UMLDiagramType } from '@ls1intum/apollon';
import { NgModel } from '@angular/forms';

describe('Course LTI Configuration Component', () => {
    let comp: CourseLtiConfigurationComponent;
    let fixture: ComponentFixture<CourseLtiConfigurationComponent>;
    let courseService: CourseManagementService;
    let sortService: SortService;

    let findWithExercisesStub: jest.SpyInstance;

    const onlineCourseConfiguration = {
        id: 1,
        userPrefix: 'prefix',
    } as OnlineCourseConfiguration;
    const course = {
        id: 123,
        title: 'Course Title',
        isAtLeastInstructor: true,
        onlineCourseConfiguration,
    } as Course;

    const programmingExercise = new ProgrammingExercise(course, undefined);
    const quizExercise = new QuizExercise(course, undefined);
    const fileUploadExercise = new FileUploadExercise(course, undefined);
    const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
    const courseWithExercises = new Course();
    courseWithExercises.exercises = [programmingExercise, quizExercise, fileUploadExercise, modelingExercise];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbNavModule, MockModule(NgbTooltipModule)],
            declarations: [
                CourseLtiConfigurationComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockComponent(HelpIconComponent),
                MockComponent(CopyIconButtonComponent),
                MockDirective(NgModel),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(SortService),
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

    it('should display exercises in exercise tab', () => {
        findWithExercisesStub.mockReturnValue(
            of(
                new HttpResponse({
                    body: courseWithExercises,
                    status: 200,
                }),
            ),
        );
        comp.ngOnInit();
        comp.activeTab = 2;

        fixture.detectChanges();

        const tableRows = fixture.debugElement.queryAll(By.css('tbody > tr'));
        expect(tableRows).toHaveLength(4);
    });

    it('should call sortService when sortRows is called', () => {
        jest.spyOn(sortService, 'sortByProperty').mockReturnValue([]);

        comp.sortRows();

        expect(sortService.sortByProperty).toHaveBeenCalledOnce();
    });
});
