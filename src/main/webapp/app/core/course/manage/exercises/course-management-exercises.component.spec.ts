import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { CourseExerciseCardComponent } from 'app/core/course/manage/course-exercise-card/course-exercise-card.component';
import { CourseManagementExercisesComponent } from 'app/core/course/manage/exercises/course-management-exercises.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FileUploadExerciseComponent } from 'app/fileupload/manage/file-upload-exercise/file-upload-exercise.component';
import { ModelingExerciseComponent } from 'app/modeling/manage/modeling-exercise/modeling-exercise.component';
import { ProgrammingExerciseComponent } from 'app/programming/manage/exercise/programming-exercise.component';
import { QuizExerciseComponent } from 'app/quiz/manage/exercise/quiz-exercise.component';
import { TextExerciseComponent } from 'app/text/manage/text-exercise/exercise/text-exercise.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementExercisesSearchComponent } from 'app/core/course/manage/exercises-search/course-management-exercises-search.component';
import { of } from 'rxjs';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MODULE_FEATURE_TEXT } from 'app/app.constants';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';

describe('Course Management Exercises Component', () => {
    let comp: CourseManagementExercisesComponent;
    let fixture: ComponentFixture<CourseManagementExercisesComponent>;

    let profileService: ProfileService;
    let getProfileInfoSub: jest.SpyInstance;

    const course = new Course();
    course.id = 123;
    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, queryParams: of({}) } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                CourseManagementExercisesComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CourseExerciseCardComponent),
                MockDirective(TranslateDirective),
                MockDirective(ExtensionPointDirective),
                MockComponent(ProgrammingExerciseComponent),
                MockComponent(QuizExerciseComponent),
                MockComponent(ModelingExerciseComponent),
                MockComponent(FileUploadExerciseComponent),
                MockComponent(TextExerciseComponent),
                MockComponent(CourseManagementExercisesSearchComponent),
                MockComponent(DocumentationButtonComponent),
            ],
            providers: [
                MockProvider(CourseManagementService),
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
                CourseTitleBarActionsDirective,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementExercisesComponent);
                comp = fixture.componentInstance;

                profileService = TestBed.inject(ProfileService);
                getProfileInfoSub = jest.spyOn(profileService, 'getProfileInfo');
                getProfileInfoSub.mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_TEXT] });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should get course on onInit', () => {
        comp.ngOnInit();
        expect(comp.course).toBe(course);
    });

    it('should open search bar on toggle search', () => {
        fixture.detectChanges();
        comp.toggleSearch();
        fixture.changeDetectorRef.detectChanges();
        const searchBar = fixture.debugElement.nativeElement.querySelector('jhi-course-management-exercises-search');

        expect(comp.showSearch).toBeTrue();
        expect(searchBar).not.toBeNull();
    });
});
