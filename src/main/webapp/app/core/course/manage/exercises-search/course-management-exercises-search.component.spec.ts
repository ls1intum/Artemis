import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementExercisesSearchComponent } from 'app/core/course/manage/exercises-search/course-management-exercises-search.component';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Course Management Exercises Search Component', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseManagementExercisesSearchComponent;
    let fixture: ComponentFixture<CourseManagementExercisesSearchComponent>;
    let emitSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseManagementExercisesSearchComponent);
        comp = fixture.componentInstance;
        emitSpy = vi.spyOn(comp.exerciseFilter, 'emit');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseManagementExercisesSearchComponent).toBeDefined();
    });

    it('should have empty filter on init', () => {
        comp.ngOnInit();
        expect(comp.exerciseNameSearch()).toBe('');
        expect(comp.exerciseCategorySearch()).toBe('');
        expect(comp.exerciseTypeSearch()).toBe('all');
    });

    it('should change filter on name change', () => {
        const filter = new ExerciseFilter();
        filter.exerciseNameSearch = 'test';
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#saveFilterButton');
        comp.exerciseNameSearch.set(filter.exerciseNameSearch);
        button.click();
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(filter);
    });

    it('should change filter on category change', () => {
        const filter = new ExerciseFilter();
        filter.exerciseCategorySearch = 'homework';
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#saveFilterButton');
        comp.exerciseCategorySearch.set(filter.exerciseCategorySearch);
        button.click();
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(filter);
    });

    it('should change filter on type change', () => {
        const filter = new ExerciseFilter();
        filter.exerciseTypeSearch = 'programming';
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#saveFilterButton');
        comp.exerciseTypeSearch.set(filter.exerciseTypeSearch);
        button.click();
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(filter);
    });

    describe('reset', () => {
        it('should reset all search fields to default values and emit filter', () => {
            comp.ngOnInit();
            // Set some non-default values
            comp.exerciseNameSearch.set('test name');
            comp.exerciseCategorySearch.set('test category');
            comp.exerciseTypeSearch.set('programming');

            comp.reset();

            // Verify values are reset to defaults
            expect(comp.exerciseNameSearch()).toBe('');
            expect(comp.exerciseCategorySearch()).toBe('');
            expect(comp.exerciseTypeSearch()).toBe('all');
            // Verify emit was called with default filter
            expect(emitSpy).toHaveBeenCalled();
        });
    });

    describe('change handlers', () => {
        it('should update exerciseNameSearch signal on name change', () => {
            comp.onExerciseNameSearchChange('new name');
            expect(comp.exerciseNameSearch()).toBe('new name');
        });

        it('should update exerciseCategorySearch signal on category change', () => {
            comp.onExerciseCategorySearchChange('new category');
            expect(comp.exerciseCategorySearch()).toBe('new category');
        });

        it('should update exerciseTypeSearch signal and emit on type change', () => {
            comp.onExerciseTypeSearchChange('quiz');
            expect(comp.exerciseTypeSearch()).toBe('quiz');
            expect(emitSpy).toHaveBeenCalled();
        });
    });

    describe('sendUpdate', () => {
        it('should emit ExerciseFilter with current values', () => {
            comp.exerciseNameSearch.set('search name');
            comp.exerciseCategorySearch.set('search category');
            comp.exerciseTypeSearch.set('text');

            comp.sendUpdate();

            expect(emitSpy).toHaveBeenCalledWith(new ExerciseFilter('search name', 'search category', 'text'));
        });
    });

    describe('typeOptions', () => {
        it('should initialize typeOptions with all exercise types', () => {
            comp.ngOnInit();

            const options = comp.typeOptions();
            expect(options).toContain('all');
            expect(options.length).toBeGreaterThan(1);
        });
    });
});
