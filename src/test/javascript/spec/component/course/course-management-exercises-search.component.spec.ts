import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementExercisesSearchComponent } from 'app/course/manage/course-management-exercises-search.component';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { NgModel } from '@angular/forms';

describe('Course Management Exercises Search Component', () => {
    let comp: CourseManagementExercisesSearchComponent;
    let fixture: ComponentFixture<CourseManagementExercisesSearchComponent>;
    let emitSpy: jest.SpyInstance;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseManagementExercisesSearchComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CourseExerciseCardComponent),
                MockDirective(TranslateDirective),
                MockDirective(NgModel),
            ],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseManagementExercisesSearchComponent);
        comp = fixture.componentInstance;
        emitSpy = jest.spyOn(comp.exerciseFilter, 'emit');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseManagementExercisesSearchComponent).toBeDefined();
    });

    it('should have empty filter on init', () => {
        comp.ngOnInit();
        expect(comp.exerciseNameSearch).toBe('');
        expect(comp.exerciseCategorySearch).toBe('');
        expect(comp.exerciseTypeSearch).toBe('all');
    });

    it('should change filter on name change', () => {
        const filter = new ExerciseFilter();
        filter.exerciseNameSearch = 'test';
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#saveFilterButton');
        comp.exerciseNameSearch = filter.exerciseNameSearch;
        button.click();
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(filter);
    });

    it('should change filter on category change', () => {
        const filter = new ExerciseFilter();
        filter.exerciseCategorySearch = 'homework';
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#saveFilterButton');
        comp.exerciseCategorySearch = filter.exerciseCategorySearch;
        button.click();
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(filter);
    });

    it('should change filter on type change', () => {
        const filter = new ExerciseFilter();
        filter.exerciseTypeSearch = 'programming';
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#saveFilterButton');
        comp.exerciseTypeSearch = filter.exerciseTypeSearch;
        button.click();
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(filter);
    });
});
