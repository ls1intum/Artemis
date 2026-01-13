import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { CourseManagementExercisesComponent } from 'app/core/course/manage/exercises/course-management-exercises.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MODULE_FEATURE_TEXT } from 'app/app.constants';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('Course Management Exercises Component', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseManagementExercisesComponent;
    let fixture: ComponentFixture<CourseManagementExercisesComponent>;

    let profileService: ProfileService;
    let getProfileInfoSub: ReturnType<typeof vi.spyOn>;

    const course = new Course();
    course.id = 123;
    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, queryParams: of({}) } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseManagementExercisesComponent, CourseTitleBarActionsDirective],
            providers: [
                MockProvider(CourseManagementService),
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseManagementExercisesComponent);
        comp = fixture.componentInstance;

        profileService = TestBed.inject(ProfileService);
        getProfileInfoSub = vi.spyOn(profileService, 'getProfileInfo');
        getProfileInfoSub.mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_TEXT] });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should get course on onInit', () => {
        comp.ngOnInit();
        expect(comp.course()).toBe(course);
    });

    it('should open search bar on toggle search', () => {
        fixture.detectChanges();
        comp.toggleSearch();
        fixture.changeDetectorRef.detectChanges();
        const searchBar = fixture.debugElement.nativeElement.querySelector('jhi-course-management-exercises-search');

        expect(comp.showSearch()).toBe(true);
        expect(searchBar).not.toBeNull();
    });
});
