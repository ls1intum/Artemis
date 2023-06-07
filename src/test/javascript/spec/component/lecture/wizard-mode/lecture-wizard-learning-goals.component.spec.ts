import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUpdateWizardCompetenciesComponent } from 'app/lecture/wizard-mode/lecture-wizard-competencies.component';
import { LectureService } from 'app/lecture/lecture.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { Competency } from 'app/entities/competency.model';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { CompetencyFormData } from 'app/course/competencies/competency-form/competency-form.component';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('LectureWizardLearningGoalsComponent', () => {
    let wizardLearningGoalsComponentFixture: ComponentFixture<LectureUpdateWizardCompetenciesComponent>;
    let wizardLearningGoalsComponent: LectureUpdateWizardCompetenciesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LectureUpdateWizardCompetenciesComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
            providers: [
                MockProvider(AlertService),
                MockProvider(LectureService),
                MockProvider(CompetencyService),
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
                wizardLearningGoalsComponentFixture = TestBed.createComponent(LectureUpdateWizardCompetenciesComponent);
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
        const learningGoalService = TestBed.inject(CompetencyService);

        const lecture = new Lecture();
        lecture.id = 1;
        const lectureResponse: HttpResponse<Lecture> = new HttpResponse({
            body: lecture,
            status: 201,
        });
        const lectureStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(lectureResponse));

        const goals = [new Competency()];
        const goalsResponse: HttpResponse<Competency[]> = new HttpResponse({
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
            expect(wizardLearningGoalsComponent.competencies).toBe(goals);
        });
    }));

    it('should show create form and load lecture when clicked', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);

        const lecture = new Lecture();
        lecture.id = 1;
        const lectureResponse: HttpResponse<Lecture> = new HttpResponse({
            body: lecture,
            status: 201,
        });
        const lectureStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(lectureResponse));

        const goals = [new Competency()];
        const goalsResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.showCreateCompetency();

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(lectureStub).toHaveBeenCalledTimes(2);

            expect(wizardLearningGoalsComponent.lecture).toBe(lecture);
            expect(wizardLearningGoalsComponent.isAddingCompetency).toBeTrue();
        });
    }));

    it('should show an alert when loading fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        const lectureStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new Competency()];
        const goalsResponse: HttpResponse<Competency[]> = new HttpResponse({
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
        const learningGoalService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        const createStub = jest.spyOn(learningGoalService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new Competency()];
        const goalsResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        const alertStub = jest.spyOn(alertService, 'error');

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.createCompetency({
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
        const learningGoalService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new Competency()];
        const goalsResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        const alertStub = jest.spyOn(alertService, 'error');
        const deleteStub = jest.spyOn(learningGoalService, 'delete').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.deleteCompetency(goals[0]);

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(deleteStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledOnce();
        });
    }));

    it('should show an alert when editing fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        const editStub = jest.spyOn(learningGoalService, 'update').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new Competency()];
        const goalsResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        const alertStub = jest.spyOn(alertService, 'error');

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.currentlyProcessedCompetency = new Competency();
            wizardLearningGoalsComponent.editCompetency({
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
        const learningGoalService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new Competency()];
        const goalsResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.onCompetencyFormCanceled();

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(wizardLearningGoalsComponent.isAddingCompetency).toBeFalse();
            expect(wizardLearningGoalsComponent.isEditingCompetency).toBeFalse();
            expect(wizardLearningGoalsComponent.isLoadingCompetencyForm).toBeFalse();
        });
    }));

    it('should delete the learning goal when clicked', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const goals = [new Competency()];
        const goalsResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: goals,
            status: 201,
        });
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(goalsResponse));
        const deleteStub = jest.spyOn(learningGoalService, 'delete').mockReturnValue(of(new HttpResponse<any>({ status: 201 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        wizardLearningGoalsComponent.deleteCompetency(goals[0]);

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(deleteStub).toHaveBeenCalledOnce();
        });
    }));

    it('should open the form when editing', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        const learningGoal = new Competency();
        learningGoal.id = 12;
        wizardLearningGoalsComponent.startEditCompetency(learningGoal);

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            expect(wizardLearningGoalsComponent.isEditingCompetency).toBeTrue();
            expect(wizardLearningGoalsComponent.currentlyProcessedCompetency.id).toBe(12);
        });
    }));

    it('should return the connected units for a goal and lecture', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        const lectureUnit = new TextUnit();
        lectureUnit.name = 'Test';
        lectureUnit.id = 5;

        wizardLearningGoalsComponent.lecture.lectureUnits = [lectureUnit];

        const learningGoal = new Competency();
        learningGoal.id = 12;
        learningGoal.lectureUnits = [lectureUnit];
        const result = wizardLearningGoalsComponent.getConnectedUnitsForCompetency(learningGoal);

        expect(result).toBe('Test');
    }));

    it('should return no connected units for empty goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardLearningGoalsComponentFixture.detectChanges();

        const learningGoal = new Competency();
        learningGoal.id = 12;
        const result = wizardLearningGoalsComponent.getConnectedUnitsForCompetency(learningGoal);

        expect(result).toBe('artemisApp.lecture.wizardMode.learningGoalNoConnectedUnits');
    }));

    it('should call the service and show an alert when creating a goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const createStub = jest.spyOn(learningGoalService, 'create').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: new Competency() })));
        const alertStub = jest.spyOn(alertService, 'success');

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: CompetencyFormData = {
            id: 1,
            title: 'Goal',
        };

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.isEditingCompetency = false;
            wizardLearningGoalsComponent.onCompetencyFormSubmitted(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(wizardLearningGoalsComponent.isAddingCompetency).toBeFalse();
                expect(createStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();
            });
        });
    }));

    it('should append exercises as units when creating a goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const goal = new Competency();
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 2;
        goal.exercises = [exercise];
        goal.lectureUnits = [];
        const createStub = jest.spyOn(learningGoalService, 'create').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: goal })));
        const alertStub = jest.spyOn(alertService, 'success');

        const unit = new ExerciseUnit();
        unit.id = 2;
        unit.exercise = exercise;

        wizardLearningGoalsComponent.lecture.lectureUnits = [unit];

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: CompetencyFormData = {
            id: 1,
            title: 'Goal',
        };

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.lecture.lectureUnits = [unit];
            wizardLearningGoalsComponent.isEditingCompetency = false;
            wizardLearningGoalsComponent.onCompetencyFormSubmitted(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(wizardLearningGoalsComponent.isAddingCompetency).toBeFalse();
                expect(createStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();

                expect(wizardLearningGoalsComponent.competencies).toHaveLength(1);
                expect(wizardLearningGoalsComponent.competencies[0]!.lectureUnits![0]!.id).toBe(2);
            });
        });
    }));

    it('should not call the service when creating a goal with an empty form', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const createStub = jest.spyOn(learningGoalService, 'create').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: new Competency() })));
        const alertStub = jest.spyOn(alertService, 'success');

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: CompetencyFormData = {};

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.createCompetency(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(createStub).toHaveBeenCalledTimes(0);
                expect(alertStub).toHaveBeenCalledTimes(0);
            });
        });
    }));

    it('should call the service and show an alert when editing a goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const editStub = jest.spyOn(learningGoalService, 'update').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: new Competency() })));
        const alertStub = jest.spyOn(alertService, 'success');

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: CompetencyFormData = {
            id: 1,
            title: 'Goal',
        };

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.currentlyProcessedCompetency = new Competency();
            wizardLearningGoalsComponent.isEditingCompetency = true;
            wizardLearningGoalsComponent.onCompetencyFormSubmitted(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(wizardLearningGoalsComponent.isEditingCompetency).toBeFalse();
                expect(editStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();
            });
        });
    }));

    it('should append exercises as units when editing a goal', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const learningGoalService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const goal = new Competency();
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 2;
        goal.exercises = [exercise];
        goal.lectureUnits = [];
        const editStub = jest.spyOn(learningGoalService, 'update').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: goal })));
        const alertStub = jest.spyOn(alertService, 'success');

        const unit = new ExerciseUnit();
        unit.id = 2;
        unit.exercise = exercise;

        wizardLearningGoalsComponent.lecture.lectureUnits = [unit];

        wizardLearningGoalsComponentFixture.detectChanges();

        const formData: CompetencyFormData = {
            id: 1,
            title: 'Goal',
        };

        wizardLearningGoalsComponentFixture.whenStable().then(() => {
            wizardLearningGoalsComponent.currentlyProcessedCompetency = new Competency();
            wizardLearningGoalsComponent.lecture.lectureUnits = [unit];
            wizardLearningGoalsComponent.isEditingCompetency = true;
            wizardLearningGoalsComponent.onCompetencyFormSubmitted(formData);

            wizardLearningGoalsComponentFixture.whenStable().then(() => {
                expect(wizardLearningGoalsComponent.isEditingCompetency).toBeFalse();
                expect(editStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();

                expect(wizardLearningGoalsComponent.competencies).toHaveLength(1);
                expect(wizardLearningGoalsComponent.competencies[0]!.lectureUnits![0]!.id).toBe(2);
            });
        });
    }));
});
