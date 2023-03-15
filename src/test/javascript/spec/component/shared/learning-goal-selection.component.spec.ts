import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgModel, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule } from 'ng-mocks';
import { of, throwError } from 'rxjs';

import { MockRouter } from '../../helpers/mocks/mock-router';
import { ArtemisTestModule } from '../../test.module';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { LearningGoalSelectionComponent } from 'app/shared/learning-goal-selection/learning-goal-selection.component';

describe('LearningGoalSelection', () => {
    let fixture: ComponentFixture<LearningGoalSelectionComponent>;
    let component: LearningGoalSelectionComponent;
    let courseCalculation: CourseScoreCalculationService;
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
                courseCalculation = TestBed.inject(CourseScoreCalculationService);
                learningGoalService = TestBed.inject(LearningGoalService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should get learning goals from cache', () => {
        const getCourseSpy = jest.spyOn(courseCalculation, 'getCourse').mockReturnValue({ learningGoals: [new LearningGoal()] });
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse');

        fixture.detectChanges();

        const select = fixture.debugElement.query(By.css('select'));
        expect(component.value).toBeUndefined();
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).not.toHaveBeenCalled();
        expect(component.isLoading).toBeFalse();
        expect(component.learningGoals).toBeArrayOfSize(1);
        expect(select).not.toBeNull();
    });

    it('should get learning goals from service', () => {
        const getCourseSpy = jest.spyOn(courseCalculation, 'getCourse').mockReturnValue({ learningGoals: undefined });
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [new LearningGoal()] })));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.learningGoals).toBeArrayOfSize(1);
        expect(component.learningGoals.first()?.course).toBeUndefined();
        expect(component.learningGoals.first()?.userProgress).toBeUndefined();
    });

    it('should set disabled when error during loading', () => {
        const getCourseSpy = jest.spyOn(courseCalculation, 'getCourse').mockReturnValue({ learningGoals: undefined });
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError({ status: 500 }));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.disabled).toBeTrue();
    });

    it('should be hidden when no learning goals', () => {
        const getCourseSpy = jest.spyOn(courseCalculation, 'getCourse').mockReturnValue({ learningGoals: [] });

        fixture.detectChanges();

        const select = fixture.debugElement.query(By.css('select'));
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.learningGoals).toBeEmpty();
        expect(select).toBeNull();
    });

    it('should select learning goals when value is written', () => {
        jest.spyOn(courseCalculation, 'getCourse').mockReturnValue({ learningGoals: [{ id: 1, title: 'test' } as LearningGoal] });

        fixture.detectChanges();

        component.writeValue([{ id: 1, title: 'other' } as LearningGoal]);
        expect(component.value).toBeArrayOfSize(1);
        expect(component.value.first()?.title).toBe('test');
    });
});
