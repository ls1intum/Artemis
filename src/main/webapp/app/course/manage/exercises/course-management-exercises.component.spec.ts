import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseManagementExercisesComponent } from 'app/course/manage/exercises/course-management-exercises.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseVariantGroupService } from 'app/core/course/manage/exercises/exercise-variant-group.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('Course Management Exercises Component', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseManagementExercisesComponent;
    let fixture: ComponentFixture<CourseManagementExercisesComponent>;

    const exercises: Exercise[] = [
        { id: 1, title: 'Intro Programming', type: ExerciseType.PROGRAMMING } as Exercise,
        { id: 2, title: 'Intro Text', type: ExerciseType.TEXT } as Exercise,
    ];
    const course: Course = { id: 1, title: 'Introduction to Programming in Java', shortName: 'INTRO_JAVA', exercises } as Course;

    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, queryParams: of({}) } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseManagementExercisesComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                MockProvider(CourseManagementService, {
                    findWithExercises: () => of(new HttpResponse({ body: course })),
                }),
                MockProvider(ExerciseVariantGroupService, {
                    getGroupsForCourse: () => of([]),
                }),
                MockProvider(QuizExerciseService, {
                    findForCourse: () => of(new HttpResponse({ body: [] })),
                }),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseManagementExercisesComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        localStorage.clear();
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

    it('should only mark itself loaded after the initial load (gating the empty state)', () => {
        // Before init nothing is loaded, so the "no matches" empty state must stay hidden even though buckets are empty.
        expect(comp.loaded()).toBe(false);
        expect(comp.buckets().length).toBe(0);
        comp.ngOnInit();
        expect(comp.loaded()).toBe(true);
    });

    it('should default to the type view when nothing is stored', () => {
        expect(comp.view()).toBe('type');
    });

    it('should persist the selected view to local storage on change', () => {
        comp.onViewChange('week');
        expect(comp.view()).toBe('week');
        expect(localStorage.getItem('artemis.exerciseManagement.view')).toBe(JSON.stringify('week'));
    });

    it('should restore the persisted view on re-instantiation', () => {
        localStorage.setItem('artemis.exerciseManagement.view', JSON.stringify('group'));
        const restored = TestBed.createComponent(CourseManagementExercisesComponent).componentInstance;
        expect(restored.view()).toBe('group');
    });

    it('should ignore an invalid persisted view and fall back to the default', () => {
        localStorage.setItem('artemis.exerciseManagement.view', JSON.stringify('bogus'));
        const restored = TestBed.createComponent(CourseManagementExercisesComponent).componentInstance;
        expect(restored.view()).toBe('type');
    });
});
