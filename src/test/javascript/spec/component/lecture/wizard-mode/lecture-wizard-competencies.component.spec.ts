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

describe('LectureWizardCompetenciesComponent', () => {
    let wizardCompetenciesComponentFixture: ComponentFixture<LectureUpdateWizardCompetenciesComponent>;
    let wizardCompetenciesComponent: LectureUpdateWizardCompetenciesComponent;

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
                wizardCompetenciesComponentFixture = TestBed.createComponent(LectureUpdateWizardCompetenciesComponent);
                wizardCompetenciesComponent = wizardCompetenciesComponentFixture.componentInstance;

                const course = new Course();
                course.id = 2;

                wizardCompetenciesComponent.lecture = new Lecture();
                wizardCompetenciesComponent.lecture.id = 1;
                wizardCompetenciesComponent.lecture.course = course;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize and load data', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);

        const lecture = new Lecture();
        lecture.id = 1;
        const lectureResponse: HttpResponse<Lecture> = new HttpResponse({
            body: lecture,
            status: 201,
        });
        const lectureStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(lectureResponse));

        const competencies = [new Competency()];
        const competenciesResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: competencies,
            status: 201,
        });
        const competenciesStub = jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesResponse));

        wizardCompetenciesComponentFixture.detectChanges();
        expect(wizardCompetenciesComponent).not.toBeNull();

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            expect(lectureStub).toHaveBeenCalledOnce();
            expect(competenciesStub).toHaveBeenCalledOnce();

            expect(wizardCompetenciesComponent.lecture).toBe(lecture);
            expect(wizardCompetenciesComponent.competencies).toBe(competencies);
        });
    }));

    it('should show create form and load lecture when clicked', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);

        const lecture = new Lecture();
        lecture.id = 1;
        const lectureResponse: HttpResponse<Lecture> = new HttpResponse({
            body: lecture,
            status: 201,
        });
        const lectureStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(lectureResponse));

        const competencies = [new Competency()];
        const competenciesResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: competencies,
            status: 201,
        });
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesResponse));

        wizardCompetenciesComponentFixture.detectChanges();

        wizardCompetenciesComponent.showCreateCompetency();

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            expect(lectureStub).toHaveBeenCalledTimes(2);

            expect(wizardCompetenciesComponent.lecture).toBe(lecture);
            expect(wizardCompetenciesComponent.isAddingCompetency).toBeTrue();
        });
    }));

    it('should show an alert when loading fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        const lectureStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const competencies = [new Competency()];
        const competenciesResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: competencies,
            status: 201,
        });
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesResponse));

        const alertStub = jest.spyOn(alertService, 'error');

        wizardCompetenciesComponentFixture.detectChanges();

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            expect(lectureStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledOnce();
        });
    }));

    it('should show an alert when creating fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        const createStub = jest.spyOn(competencyService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));

        const competencies = [new Competency()];
        const competenciesResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: competencies,
            status: 201,
        });
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesResponse));

        const alertStub = jest.spyOn(alertService, 'error');

        wizardCompetenciesComponentFixture.detectChanges();

        wizardCompetenciesComponent.createCompetency({
            id: 1,
            title: 'Competency',
        });

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledTimes(2);
        });
    }));

    it('should show an alert when deleting fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const competencies = [new Competency()];
        const competenciesResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: competencies,
            status: 201,
        });
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesResponse));

        const alertStub = jest.spyOn(alertService, 'error');
        const deleteStub = jest.spyOn(competencyService, 'delete').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardCompetenciesComponentFixture.detectChanges();

        wizardCompetenciesComponent.deleteCompetency(competencies[0]);

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            expect(deleteStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledOnce();
        });
    }));

    it('should show an alert when editing fails', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        const editStub = jest.spyOn(competencyService, 'update').mockReturnValue(throwError(() => ({ status: 404 })));

        const competencies = [new Competency()];
        const competenciesResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: competencies,
            status: 201,
        });
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesResponse));

        const alertStub = jest.spyOn(alertService, 'error');

        wizardCompetenciesComponentFixture.detectChanges();

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            wizardCompetenciesComponent.currentlyProcessedCompetency = new Competency();
            wizardCompetenciesComponent.editCompetency({
                id: 1,
                title: 'Competency',
            });
            wizardCompetenciesComponentFixture.whenStable().then(() => {
                expect(editStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledTimes(2);
            });
        });
    }));

    it('should close all forms when canceling', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const competencies = [new Competency()];
        const competenciesResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: competencies,
            status: 201,
        });
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesResponse));

        wizardCompetenciesComponentFixture.detectChanges();

        wizardCompetenciesComponent.onCompetencyFormCanceled();

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            expect(wizardCompetenciesComponent.isAddingCompetency).toBeFalse();
            expect(wizardCompetenciesComponent.isEditingCompetency).toBeFalse();
            expect(wizardCompetenciesComponent.isLoadingCompetencyForm).toBeFalse();
        });
    }));

    it('should delete the competency when clicked', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));

        const competencies = [new Competency()];
        const competenciesResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: competencies,
            status: 201,
        });
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesResponse));
        const deleteStub = jest.spyOn(competencyService, 'delete').mockReturnValue(of(new HttpResponse<any>({ status: 201 })));

        wizardCompetenciesComponentFixture.detectChanges();

        wizardCompetenciesComponent.deleteCompetency(competencies[0]);

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            expect(deleteStub).toHaveBeenCalledOnce();
        });
    }));

    it('should open the form when editing', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardCompetenciesComponentFixture.detectChanges();

        const competency = new Competency();
        competency.id = 12;
        wizardCompetenciesComponent.startEditCompetency(competency);

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            expect(wizardCompetenciesComponent.isEditingCompetency).toBeTrue();
            expect(wizardCompetenciesComponent.currentlyProcessedCompetency.id).toBe(12);
        });
    }));

    it('should return the connected units for a competency and lecture', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardCompetenciesComponentFixture.detectChanges();

        const lectureUnit = new TextUnit();
        lectureUnit.name = 'Test';
        lectureUnit.id = 5;

        wizardCompetenciesComponent.lecture.lectureUnits = [lectureUnit];

        const competency = new Competency();
        competency.id = 12;
        competency.lectureUnits = [lectureUnit];
        const result = wizardCompetenciesComponent.getConnectedUnitsForCompetency(competency);

        expect(result).toBe('Test');
    }));

    it('should return no connected units for empty competency', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        wizardCompetenciesComponentFixture.detectChanges();

        const competency = new Competency();
        competency.id = 12;
        const result = wizardCompetenciesComponent.getConnectedUnitsForCompetency(competency);

        expect(result).toBe('artemisApp.lecture.wizardMode.competencyNoConnectedUnits');
    }));

    it('should call the service and show an alert when creating a competency', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const createStub = jest.spyOn(competencyService, 'create').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: new Competency() })));
        const alertStub = jest.spyOn(alertService, 'success');

        wizardCompetenciesComponentFixture.detectChanges();

        const formData: CompetencyFormData = {
            id: 1,
            title: 'Competency',
        };

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            wizardCompetenciesComponent.isEditingCompetency = false;
            wizardCompetenciesComponent.onCompetencyFormSubmitted(formData);

            wizardCompetenciesComponentFixture.whenStable().then(() => {
                expect(wizardCompetenciesComponent.isAddingCompetency).toBeFalse();
                expect(createStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();
            });
        });
    }));

    it('should append exercises as units when creating a competency', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const competency = new Competency();
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 2;
        competency.exercises = [exercise];
        competency.lectureUnits = [];
        const createStub = jest.spyOn(competencyService, 'create').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: competency })));
        const alertStub = jest.spyOn(alertService, 'success');

        const unit = new ExerciseUnit();
        unit.id = 2;
        unit.exercise = exercise;

        wizardCompetenciesComponent.lecture.lectureUnits = [unit];

        wizardCompetenciesComponentFixture.detectChanges();

        const formData: CompetencyFormData = {
            id: 1,
            title: 'Competency',
        };

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            wizardCompetenciesComponent.lecture.lectureUnits = [unit];
            wizardCompetenciesComponent.isEditingCompetency = false;
            wizardCompetenciesComponent.onCompetencyFormSubmitted(formData);

            wizardCompetenciesComponentFixture.whenStable().then(() => {
                expect(wizardCompetenciesComponent.isAddingCompetency).toBeFalse();
                expect(createStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();

                expect(wizardCompetenciesComponent.competencies).toHaveLength(1);
                expect(wizardCompetenciesComponent.competencies[0]!.lectureUnits![0]!.id).toBe(2);
            });
        });
    }));

    it('should not call the service when creating a competency with an empty form', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const createStub = jest.spyOn(competencyService, 'create').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: new Competency() })));
        const alertStub = jest.spyOn(alertService, 'success');

        wizardCompetenciesComponentFixture.detectChanges();

        const formData: CompetencyFormData = {};

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            wizardCompetenciesComponent.createCompetency(formData);

            wizardCompetenciesComponentFixture.whenStable().then(() => {
                expect(createStub).not.toHaveBeenCalled();
                expect(alertStub).not.toHaveBeenCalled();
            });
        });
    }));

    it('should call the service and show an alert when editing a competency', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const editStub = jest.spyOn(competencyService, 'update').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: new Competency() })));
        const alertStub = jest.spyOn(alertService, 'success');

        wizardCompetenciesComponentFixture.detectChanges();

        const formData: CompetencyFormData = {
            id: 1,
            title: 'Competency',
        };

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            wizardCompetenciesComponent.currentlyProcessedCompetency = new Competency();
            wizardCompetenciesComponent.isEditingCompetency = true;
            wizardCompetenciesComponent.onCompetencyFormSubmitted(formData);

            wizardCompetenciesComponentFixture.whenStable().then(() => {
                expect(wizardCompetenciesComponent.isEditingCompetency).toBeFalse();
                expect(editStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();
            });
        });
    }));

    it('should append exercises as units when editing a competency', fakeAsync(() => {
        const lectureService = TestBed.inject(LectureService);
        const competencyService = TestBed.inject(CompetencyService);
        const alertService = TestBed.inject(AlertService);

        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => ({ status: 404 })));
        jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => ({ status: 404 })));

        const competency = new Competency();
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = 2;
        competency.exercises = [exercise];
        competency.lectureUnits = [];
        const editStub = jest.spyOn(competencyService, 'update').mockReturnValue(of(new HttpResponse<any>({ status: 201, body: competency })));
        const alertStub = jest.spyOn(alertService, 'success');

        const unit = new ExerciseUnit();
        unit.id = 2;
        unit.exercise = exercise;

        wizardCompetenciesComponent.lecture.lectureUnits = [unit];

        wizardCompetenciesComponentFixture.detectChanges();

        const formData: CompetencyFormData = {
            id: 1,
            title: 'Competency',
        };

        wizardCompetenciesComponentFixture.whenStable().then(() => {
            wizardCompetenciesComponent.currentlyProcessedCompetency = new Competency();
            wizardCompetenciesComponent.lecture.lectureUnits = [unit];
            wizardCompetenciesComponent.isEditingCompetency = true;
            wizardCompetenciesComponent.onCompetencyFormSubmitted(formData);

            wizardCompetenciesComponentFixture.whenStable().then(() => {
                expect(wizardCompetenciesComponent.isEditingCompetency).toBeFalse();
                expect(editStub).toHaveBeenCalledOnce();
                expect(alertStub).toHaveBeenCalledOnce();

                expect(wizardCompetenciesComponent.competencies).toHaveLength(1);
                expect(wizardCompetenciesComponent.competencies[0]!.lectureUnits![0]!.id).toBe(2);
            });
        });
    }));
});
