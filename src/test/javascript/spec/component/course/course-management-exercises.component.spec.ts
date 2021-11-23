import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { CourseManagementExercisesComponent } from 'app/course/manage/course-management-exercises.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { FileUploadExerciseComponent } from 'app/exercises/file-upload/manage/file-upload-exercise.component';
import { ModelingExerciseComponent } from 'app/exercises/modeling/manage/modeling-exercise.component';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { QuizExerciseComponent } from 'app/exercises/quiz/manage/quiz-exercise.component';
import { TextExerciseComponent } from 'app/exercises/text/manage/text-exercise/text-exercise.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementExercisesSearchComponent } from 'app/course/manage/course-management-exercises-search.component';
import { of } from 'rxjs';

describe('Course Management Exercises Component', () => {
    let comp: CourseManagementExercisesComponent;
    let fixture: ComponentFixture<CourseManagementExercisesComponent>;
    let courseService: CourseManagementService;
    const course = new Course();
    course.id = 123;
    const parentRoute = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;
    let findStub: jest.SpyInstance;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseManagementExercisesComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CourseExerciseCardComponent),
                MockDirective(NgbCollapse),
                MockDirective(TranslateDirective),
                MockDirective(ExtensionPointDirective),
                MockComponent(AlertComponent),
                MockComponent(ProgrammingExerciseComponent),
                MockDirective(OrionFilterDirective),
                MockComponent(QuizExerciseComponent),
                MockComponent(ModelingExerciseComponent),
                MockComponent(FileUploadExerciseComponent),
                MockComponent(TextExerciseComponent),
                MockComponent(CourseManagementExercisesSearchComponent),
            ],
            providers: [
                MockProvider(CourseManagementService),
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseManagementExercisesComponent);
        comp = fixture.componentInstance;
        courseService = TestBed.inject(CourseManagementService);
        findStub = jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should find course on on init', () => {
        comp.ngOnInit();
        expect(comp.courseId).toBe(course.id);
        expect(findStub).toHaveBeenCalledWith(course.id);
        expect(findStub).toHaveBeenCalledTimes(1);
    });

    it('should open search bar on button click', () => {
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#toggleSearchButton');
        button.click();
        fixture.detectChanges();

        const searchBar = fixture.debugElement.nativeElement.querySelector('jhi-course-management-exercises-search');

        expect(comp.showSearch).toBe(true);
        expect(searchBar).not.toBe(null);
    });
});
