import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUpdateWizardLearningGoalsComponent } from 'app/lecture/wizard-mode/lecture-wizard-learning-goals.component';
import { LectureService } from 'app/lecture/lecture.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { TextExercise } from 'app/entities/text-exercise.model';

describe('LectureWizardLearningGoalsComponent', () => {
    let wizardLearningGoalsComponentFixture: ComponentFixture<LectureUpdateWizardLearningGoalsComponent>;
    let wizardLearningGoalsComponent: LectureUpdateWizardLearningGoalsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LectureUpdateWizardLearningGoalsComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(AlertService),
                MockProvider(LectureService),
                MockProvider(LearningGoalService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardLearningGoalsComponentFixture = TestBed.createComponent(LectureUpdateWizardLearningGoalsComponent);
                wizardLearningGoalsComponent = wizardLearningGoalsComponentFixture.componentInstance;

                const course = new Course();
                course.id = 2;

                wizardLearningGoalsComponent.lecture = new Lecture();
                wizardLearningGoalsComponent.lecture.id = 1;
                wizardLearningGoalsComponent.lecture.course = course;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize and load data', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);

        const lecture = new Lecture();
        lecture.id = 1;
        const lectureResponse: HttpResponse<Lecture> = new HttpResponse({
            body: lecture,
            status: 201,
        });
        const lectureStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(lectureResponse));

        const goals = [new LearningGoal()];
        const goalsResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        const goalsStub = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        wizardLearningGoalsComponentFixture.detectChanges();
        expect(wizardLearningGoalsComponent).not.toBeNull();

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(lectureStub).toHaveBeenCalledOnce();
            expect(goalsStub).toHaveBeenCalledOnce();

            expect(wizardLearningGoalsComponent.lecture).toBe(lecture);
            expect(wizardLearningGoalsComponent.learningGoals).toBe(goals);
        });
    }));

    it('should show create form and load lecture when clicked', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);

        const lecture = new Lecture();
        lecture.id = 1;
        const lectureResponse: HttpResponse<Lecture> = new HttpResponse({
            body: lecture,
            status: 201,
        });
        const lectureStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(lectureResponse));

        const goals = [new LearningGoal()];
        const goalsResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.showCreateLearningGoal();

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(lectureStub).toHaveBeenCalledTimes(2);

            expect(wizardLearningGoalsComponent.lecture).toBe(lecture);
            expect(wizardLearningGoalsComponent.isAddingLearningGoal).toBeTrue();
        });
    }));

    it('should show an alert when loading fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const alertService = TestBed.inject(AlertService);

        const lectureStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new LearningGoal()];
        const goalsResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        const alertStub = jest.spyOn(alertService, 'error');

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(lectureStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledOnce();
        });
    }));

    it('should show an alert when creating fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        const createStub = jest.spyOn(learningGoalService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new LearningGoal()];
        const goalsResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        const alertStub = jest.spyOn(alertService, 'error');

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.createLearningGoal({
            id: 1,
            title: 'Goal',
        });

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledTimes(2);
        });
    }));

    it('should show an alert when deleting fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new LearningGoal()];
        const goalsResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        const alertStub = jest.spyOn(alertService, 'error');
        const deleteStub = jest.spyOn(learningGoalService, 'delete').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.deleteLearningGoal(goals[0]);

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(deleteStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledOnce();
        });
    }));

    it('should show an alert when editing fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        const editStub = jest.spyOn(learningGoalService, 'update').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new LearningGoal()];
        const goalsResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        const alertStub = jest.spyOn(alertService, 'error');

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.currentlyProcessedLearningGoal = new LearningGoal();
            wizardLearningGoalsComponent.editLearningGoal({
                id: 1,
                title: 'Goal',
            });
            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(editStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledTimes(2);
            });
        });
    }));

    it('should close all forms when canceling', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new LearningGoal()];
        const goalsResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.onLearningGoalFormCanceled();

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(wizardLearningGoalsComponent.isAddingLearningGoal).toBeFalse();
            expect(wizardLearningGoalsComponent.isEditingLearningGoal).toBeFalse();
            expect(wizardLearningGoalsComponent.isLoadingLearningGoalForm).toBeFalse();
        });
    }));

    it('should delete the learning goal when clicked', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new LearningGoal()];
        const goalsResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));
        const deleteStub = jest.spyOn(learningGoalService, 'delete').mockReturnValue(of(new HttpResponse<Object>({ status: 201 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.deleteLearningGoal(goals[0]);

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(deleteStub).toHaveBeenCalledOnce();
        });
    }));

    it('should open the form when editing', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        const learningGoal = new LearningGoal();
        learningGoal.id = 12;
        wizardLearningGoalsComponent.startEditLearningGoal(learningGoal);

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(wizardLearningGoalsComponent.isEditingLearningGoal).toBeTrue();
            expect(wizardLearningGoalsComponent.currentlyProcessedLearningGoal.id).toBe(12);
        });
    }));

    it('should return the connected units for a goal and lecture', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        const lectureUnit = new TextUnit();
        lectureUnit.name = 'Test';
        lectureUnit.id = 5;

        wizardLearningGoalsComponent.lecture.lectureUnits = [lectureUnit];

        const learningGoal = new LearningGoal();
        learningGoal.id = 12;
        learningGoal.lectureUnits = [lectureUnit];
        const result = wizardLearningGoalsComponent.getConnectedUnitsForLearningGoal(learningGoal);

        expect(result).toBe('Test');
    }));

    it('should return no connected units for empty goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        const learningGoal = new LearningGoal();
        learningGoal.id = 12;
        const result = wizardLearningGoalsComponent.getConnectedUnitsForLearningGoal(learningGoal);

        expect(result).toBe('artemisApp.lecture.wizardMode.learningGoalNoConnectedUnits');
    }));

    it('should call the service and show an alert when creating a goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const createStub = jest.spyOn(learningGoalService, 'create').mockReturnValue(of(new HttpResponse<Object>({ status: 201, body: new LearningGoal() })));
        const alertStub = jest.spyOn(alertService, 'success');

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: LearningGoalFormData = {
            id: 1,
            title: 'Goal',
        };

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.isEditingLearningGoal = false;
            wizardLearningGoalsComponent.onLearningGoalFormSubmitted(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(wizardLearningGoalsComponent.isAddingLearningGoal).toBeFalse();
                expect(createStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();
            });
        });
    }));

    it('should append exercises as units when creating a goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const goal = new LearningGoal();
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 2;
        goal.exercises = [exercise];
        goal.lectureUnits = [];
        const createStub = jest.spyOn(learningGoalService, 'create').mockReturnValue(of(new HttpResponse<Object>({ status: 201, body: goal })));
        const alertStub = jest.spyOn(alertService, 'success');

        const unit = new ExerciseUnit();
        unit.id = 2;
        unit.exercise = exercise;

        wizardLearningGoalsComponent.lecture.lectureUnits = [unit];

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: LearningGoalFormData = {
            id: 1,
            title: 'Goal',
        };

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.lecture.lectureUnits = [unit];
            wizardLearningGoalsComponent.isEditingLearningGoal = false;
            wizardLearningGoalsComponent.onLearningGoalFormSubmitted(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(wizardLearningGoalsComponent.isAddingLearningGoal).toBeFalse();
                expect(createStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();

                expect(wizardLearningGoalsComponent.learningGoals).toHaveLength(1);
                expect(wizardLearningGoalsComponent.learningGoals[0]!.lectureUnits![0]!.id).toBe(2);
            });
        });
    }));

    it('should not call the service when creating a goal with an empty form', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const createStub = jest.spyOn(learningGoalService, 'create').mockReturnValue(of(new HttpResponse<Object>({ status: 201, body: new LearningGoal() })));
        const alertStub = jest.spyOn(alertService, 'success');

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: LearningGoalFormData = {};

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.createLearningGoal(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(createStub).toHaveBeenCalledTimes(0);
                expect(alertStub).toHaveBeenCalledTimes(0);
            });
        });
    }));

    it('should call the service and show an alert when editing a goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const editStub = jest.spyOn(learningGoalService, 'update').mockReturnValue(of(new HttpResponse<Object>({ status: 201, body: new LearningGoal() })));
        const alertStub = jest.spyOn(alertService, 'success');

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: LearningGoalFormData = {
            id: 1,
            title: 'Goal',
        };

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.currentlyProcessedLearningGoal = new LearningGoal();
            wizardLearningGoalsComponent.isEditingLearningGoal = true;
            wizardLearningGoalsComponent.onLearningGoalFormSubmitted(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(wizardLearningGoalsComponent.isEditingLearningGoal).toBeFalse();
                expect(editStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();
            });
        });
    }));

    it('should append exercises as units when editing a goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const goal = new LearningGoal();
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 2;
        goal.exercises = [exercise];
        goal.lectureUnits = [];
        const editStub = jest.spyOn(learningGoalService, 'update').mockReturnValue(of(new HttpResponse<Object>({ status: 201, body: goal })));
        const alertStub = jest.spyOn(alertService, 'success');

        const unit = new ExerciseUnit();
        unit.id = 2;
        unit.exercise = exercise;

        wizardLearningGoalsComponent.lecture.lectureUnits = [unit];

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: LearningGoalFormData = {
            id: 1,
            title: 'Goal',
        };

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.currentlyProcessedLearningGoal = new LearningGoal();
            wizardLearningGoalsComponent.lecture.lectureUnits = [unit];
            wizardLearningGoalsComponent.isEditingLearningGoal = true;
            wizardLearningGoalsComponent.onLearningGoalFormSubmitted(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(wizardLearningGoalsComponent.isEditingLearningGoal).toBeFalse();
                expect(editStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();

                expect(wizardLearningGoalsComponent.learningGoals).toHaveLength(1);
                expect(wizardLearningGoalsComponent.learningGoals[0]!.lectureUnits![0]!.id).toBe(2);
            });
        });
    }));
});
