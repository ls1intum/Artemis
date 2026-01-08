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
});
