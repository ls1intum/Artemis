import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseLtiConfigurationComponent } from 'app/core/course/manage/course-lti-configuration/course-lti-configuration.component';
import { SortService } from 'app/shared/service/sort.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { OnlineCourseConfiguration } from 'app/lti/shared/entities/online-course-configuration.model';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { UMLDiagramType } from '@tumaet/apollon';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { CopyToClipboardButtonComponent } from 'app/shared/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';

describe('Course LTI Configuration Component', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseLtiConfigurationComponent;
    let fixture: ComponentFixture<CourseLtiConfigurationComponent>;
    let sortService: SortService;

    let findWithExercisesStub: ReturnType<typeof vi.spyOn>;

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

    const lecture = new Lecture();
    lecture.course = course;
    lecture.title = 'Lecture Title';

    const courseWithExercisesAndLectures = new Course();
    courseWithExercisesAndLectures.exercises = [programmingExercise, quizExercise, fileUploadExercise, modelingExercise];
    courseWithExercisesAndLectures.lectures = [lecture];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseLtiConfigurationComponent],
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
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(CourseLtiConfigurationComponent, {
                set: {
                    imports: [
                        NgbNavModule,
                        MockModule(NgbTooltipModule),
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe),
                        MockRouterLinkDirective,
                        MockComponent(FaIconComponent),
                        MockComponent(HelpIconComponent),
                        MockComponent(CopyToClipboardButtonComponent),
                        MockDirective(SortDirective),
                        MockDirective(SortByDirective),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseLtiConfigurationComponent);
        comp = fixture.componentInstance;
        TestBed.inject(CourseManagementService);
        sortService = TestBed.inject(SortService);

        findWithExercisesStub = vi
            .spyOn(TestBed.inject(CourseManagementService), 'findWithExercisesAndLecturesAndCompetencies')
            .mockReturnValue(of(new HttpResponse({ body: courseWithExercisesAndLectures, status: 200 })));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        findWithExercisesStub.mockReturnValue(of(new HttpResponse({ body: courseWithExercisesAndLectures, status: 200 })));
        fixture.detectChanges();
        expect(CourseLtiConfigurationComponent).not.toBeNull();
    });

    describe('OnInit', () => {
        it('should load course and exercises', () => {
            findWithExercisesStub.mockReturnValue(
                of(
                    new HttpResponse({
                        body: courseWithExercisesAndLectures,
                        status: 200,
                    }),
                ),
            );
            comp.ngOnInit();

            expect(comp.course()).toEqual(course);
            expect(comp.onlineCourseConfiguration()).toEqual(course.onlineCourseConfiguration);
            expect(comp.exercises()).toEqual(courseWithExercisesAndLectures.exercises);
            expect(findWithExercisesStub).toHaveBeenCalledOnce();
        });
    });

    it('should display exercises in exercise tab', () => {
        findWithExercisesStub.mockReturnValue(
            of(
                new HttpResponse({
                    body: courseWithExercisesAndLectures,
                    status: 200,
                }),
            ),
        );
        comp.ngOnInit();
        comp.activeTab.set(2);

        fixture.detectChanges();

        const tableRows = fixture.debugElement.queryAll(By.css('tbody > tr'));
        expect(tableRows).toHaveLength(4);
    });

    it('should display lecture in lecture tab', () => {
        findWithExercisesStub.mockReturnValue(
            of(
                new HttpResponse({
                    body: courseWithExercisesAndLectures,
                    status: 200,
                }),
            ),
        );
        comp.ngOnInit();
        comp.activeTab.set(3);

        fixture.detectChanges();

        const tableRows = fixture.debugElement.queryAll(By.css('tbody > tr'));
        expect(tableRows).toHaveLength(1);
    });

    it('should call sortService when sortRows is called', () => {
        vi.spyOn(sortService, 'sortByProperty').mockReturnValue([]);

        comp.sortRows();

        expect(sortService.sortByProperty).toHaveBeenCalledOnce();
    });
});
