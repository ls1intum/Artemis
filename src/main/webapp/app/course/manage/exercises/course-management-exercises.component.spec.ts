import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { CourseManagementExercisesComponent } from 'app/course/manage/exercises/course-management-exercises.component';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { createIntroToJavaCourse } from 'app/core/course/manage/exercises/mock/intro-to-programming-java-exercises';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('Course Management Exercises Component', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseManagementExercisesComponent;
    let fixture: ComponentFixture<CourseManagementExercisesComponent>;

    const course = createIntroToJavaCourse();
    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, queryParams: of({}) } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseManagementExercisesComponent],
            providers: [
                MockProvider(DialogService, MockDialogService),
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseManagementExercisesComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set course on init', () => {
        comp.ngOnInit();
        expect(comp.course()).toBe(course);
    });

    it('should populate buckets on init', () => {
        comp.ngOnInit();
        expect(comp.buckets().length).toBeGreaterThan(0);
    });

    it('should filter exercises on search', () => {
        comp.ngOnInit();
        const initialCount = comp.buckets().reduce((sum, b) => sum + b.exercises.length, 0);
        comp.onSearchChange('zzz_nomatch_zzz');
        const filteredCount = comp.buckets().reduce((sum, b) => sum + b.exercises.length, 0);
        expect(filteredCount).toBeLessThan(initialCount);
    });
});
