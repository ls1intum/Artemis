import { ArtemisTestModule } from '../../test.module';
import { LearningGoalSelectionComponent } from 'app/shared/learning-goal-selection/learning-goal-selection.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent, MockDirective, MockModule } from 'ng-mocks';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { NgModel, ReactiveFormsModule } from '@angular/forms';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

describe('LearningGoalSelection', () => {
    let fixture: ComponentFixture<LearningGoalSelectionComponent>;
    let component: LearningGoalSelectionComponent;
    let courseStorageService: CourseStorageService;
    let learningGoalService: LearningGoalService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, MockModule(NgbTooltipModule)],
            declarations: [LearningGoalSelectionComponent, MockComponent(FaIconComponent), MockDirective(NgModel)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ courseId: 1 }),
                        },
                    } as any as ActivatedRoute,
                },
                {
                    provide: Router,
                    useClass: MockRouter,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningGoalSelectionComponent);
                component = fixture.componentInstance;
                courseStorageService = TestBed.inject(CourseStorageService);
                learningGoalService = TestBed.inject(LearningGoalService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should get all learning goals from cache if include optionals true', () => {
        const nonOptional = { id: 1, optional: false } as LearningGoal;
        const optional = { id: 2, optional: true } as LearningGoal;
        component.includeOptionals = true;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [nonOptional, optional] });
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse');

        fixture.detectChanges();

        const select = fixture.debugElement.query(By.css('select'));
        expect(component.value).toBeUndefined();
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).not.toHaveBeenCalled();
        expect(component.isLoading).toBeFalse();
        expect(component.selectableLearningGoals).toBeArrayOfSize(2);
        expect(select).not.toBeNull();
    });

    it('should get non optional learning goals from cache if include optionals false', () => {
        const nonOptional = { id: 1, optional: false } as LearningGoal;
        const optional = { id: 2, optional: true } as LearningGoal;
        component.includeOptionals = false;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [nonOptional, optional] });
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse');

        fixture.detectChanges();

        const select = fixture.debugElement.query(By.css('select'));
        expect(component.value).toBeUndefined();
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).not.toHaveBeenCalled();
        expect(component.isLoading).toBeFalse();
        expect(component.selectableLearningGoals).toBeArrayOfSize(1);
        expect(select).not.toBeNull();
    });

    it('should get all learning goals from service if include optionals true', () => {
        const nonOptional = { id: 1, optional: false } as LearningGoal;
        const optional = { id: 2, optional: true } as LearningGoal;
        component.includeOptionals = true;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [nonOptional, optional] })));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.selectableLearningGoals).toBeArrayOfSize(2);
        expect(component.selectableLearningGoals.first()?.course).toBeUndefined();
        expect(component.selectableLearningGoals.first()?.userProgress).toBeUndefined();
    });

    it('should get non optional learning goals from service if include optionals false', () => {
        const nonOptional = { id: 1, optional: false } as LearningGoal;
        const optional = { id: 2, optional: true } as LearningGoal;
        component.includeOptionals = false;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [nonOptional, optional] })));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.selectableLearningGoals).toBeArrayOfSize(1);
        expect(component.selectableLearningGoals.first()?.course).toBeUndefined();
        expect(component.selectableLearningGoals.first()?.userProgress).toBeUndefined();
    });

    it('should set disabled when error during loading', () => {
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError({ status: 500 }));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.disabled).toBeTrue();
    });

    it('should be hidden when no learning goals', () => {
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [] });

        fixture.detectChanges();

        const select = fixture.debugElement.query(By.css('select'));
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.selectableLearningGoals).toBeEmpty();
        expect(select).toBeNull();
    });

    it('should select learning goals when value is written', () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [{ id: 1, title: 'test' } as LearningGoal] });

        fixture.detectChanges();

        component.writeValue([{ id: 1, title: 'other' } as LearningGoal]);
        expect(component.value).toBeArrayOfSize(1);
        expect(component.value.first()?.title).toBe('test');
    });

    it('should show optional competencies on change', () => {
        const optional = { id: 1, optional: true } as LearningGoal;
        const nonOptional = { id: 2, optional: false } as LearningGoal;
        component.learningGoals = [optional, nonOptional];
        component.includeOptionals = false;
        component.ngOnInit();
        fixture.detectChanges();
        expect(component.selectableLearningGoals).toEqual([nonOptional]);
        component.includeOptionals = true;
        component.ngOnChanges({ includeOptionals: new SimpleChange(false, true, false) } as SimpleChanges);
        expect(component.selectableLearningGoals).toEqual([optional, nonOptional]);
    });

    it('should show non optional competencies on change', () => {
        const optional = { id: 1, optional: true } as LearningGoal;
        const nonOptional = { id: 2, optional: false } as LearningGoal;
        component.learningGoals = [optional, nonOptional];
        component.includeOptionals = true;
        component.value = [];
        component.ngOnInit();
        fixture.detectChanges();
        expect(component.selectableLearningGoals).toEqual([optional, nonOptional]);
        component.includeOptionals = false;
        component.ngOnChanges({ includeOptionals: new SimpleChange(true, false, false) } as SimpleChanges);
        expect(component.selectableLearningGoals).toEqual([nonOptional]);
    });
});
