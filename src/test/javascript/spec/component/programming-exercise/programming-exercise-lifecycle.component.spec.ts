import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-test-schedule-date-picker.component';
import { NgModel } from '@angular/forms';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { QueryList, SimpleChange } from '@angular/core';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { expectElementToBeDisabled, expectElementToBeEnabled } from '../../helpers/utils/general.utils';
import { Course } from 'app/entities/course.model';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercises/shared/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { Subject } from 'rxjs';

describe('ProgrammingExerciseLifecycleComponent', () => {
    let comp: ProgrammingExerciseLifecycleComponent;
    let fixture: ComponentFixture<ProgrammingExerciseLifecycleComponent>;

    const startDate = dayjs().add(5, 'days');
    const nextDueDate = dayjs().add(6, 'days');
    const afterDueDate = dayjs().add(7, 'days');
    const exampleSolutionPublicationDate = dayjs().add(9, 'days');
    let exercise: ProgrammingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ProgrammingExerciseLifecycleComponent,
                MockComponent(ProgrammingExerciseTestScheduleDatePickerComponent),
                MockComponent(HelpIconComponent),
                MockComponent(ExerciseFeedbackSuggestionOptionsComponent),
                MockDirective(NgModel),
                TranslatePipeMock,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseLifecycleComponent);
                comp = fixture.componentInstance;

                exercise = {
                    id: 42,
                    dueDate: nextDueDate,
                    startDate,
                    buildAndTestStudentSubmissionsAfterDueDate: afterDueDate,
                    exampleSolutionPublicationDate,
                } as ProgrammingExercise;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should do nothing if the release date is set to null', () => {
        comp.exercise = exercise;
        comp.updateReleaseDate(undefined);

        expect(comp.exercise.startDate).toEqual(startDate);
        expect(comp.exercise.dueDate).toEqual(nextDueDate);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);
        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(exampleSolutionPublicationDate);
    });

    it('should only reset the due date if the release date is between the due date and the after due date', () => {
        comp.exercise = exercise;
        const newRelease = dayjs().add(6, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise.startDate).toEqual(newRelease);
        expect(comp.exercise.dueDate).toEqual(newRelease);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);
        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(exampleSolutionPublicationDate);
    });

    it('should reset the due date, the after due date and the example solution publication date if the new release is after all dates', () => {
        comp.exercise = exercise;
        const newRelease = dayjs().add(10, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise.releaseDate).toEqual(newRelease);
        expect(comp.exercise.startDate).toEqual(newRelease);
        expect(comp.exercise.dueDate).toEqual(newRelease);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(newRelease);
        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(newRelease);
    });

    it('should only reset the due date if the start date is between the due date and the after due date', () => {
        comp.exercise = exercise;
        const newStart = dayjs().add(6, 'days');
        comp.updateStartDate(newStart);

        expect(comp.exercise.dueDate).toEqual(newStart);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);
        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(exampleSolutionPublicationDate);
    });

    it('should reset the due date, the after due date and the example solution publication date if the new start date is after all dates', () => {
        comp.exercise = exercise;
        const newStart = dayjs().add(10, 'days');
        comp.updateStartDate(newStart);

        expect(comp.exercise.dueDate).toEqual(newStart);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(newStart);
        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(newStart);
    });

    it('should reset the example solution publication date if the new due date is later', () => {
        const newDueDate = dayjs().add(10, 'days');
        comp.exercise = exercise;
        exercise.dueDate = newDueDate;
        comp.updateExampleSolutionPublicationDate(newDueDate);

        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(newDueDate);
    });

    it('should change the value for allowing complaints for exercise with automatic assessment after toggling', () => {
        comp.exercise = exercise;
        comp.exercise.allowComplaintsForAutomaticAssessments = false;
        comp.exercise.assessmentType = AssessmentType.AUTOMATIC;
        comp.toggleComplaintsType();

        expect(comp.exercise.allowComplaintsForAutomaticAssessments).toBeTrue();
    });

    it('should change feedback request allowed after toggling', () => {
        comp.exercise = { ...exercise, allowManualFeedbackRequests: false };
        expect(comp.exercise.allowManualFeedbackRequests).toBeFalse();

        comp.toggleManualFeedbackRequests();

        expect(comp.exercise.allowManualFeedbackRequests).toBeTrue();
    });

    it('should change assessment type from automatic to semi-automatic after toggling', () => {
        comp.exercise = exercise;
        comp.exercise.assessmentType = AssessmentType.AUTOMATIC;
        comp.toggleAssessmentType();

        expect(comp.exercise.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
        expect(comp.exercise.allowComplaintsForAutomaticAssessments).toBeFalse();
    });

    it('should change assessment type from semi-automatic to automatic after toggling', () => {
        comp.exercise = exercise;
        comp.exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        comp.toggleAssessmentType();

        expect(comp.exercise.assessmentType).toBe(AssessmentType.AUTOMATIC);
        expect(comp.exercise.assessmentDueDate).toBeUndefined();
    });

    it('should disable feedback suggestions when changing the assessment type to automatic', () => {
        comp.exercise = exercise;
        comp.exercise.id = undefined;
        comp.ngOnInit();

        expect(comp.exercise.assessmentType).toBe(AssessmentType.AUTOMATIC);

        comp.exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        comp.exercise.feedbackSuggestionModule = 'programming_module';
        comp.toggleAssessmentType(); // toggle to AUTOMATIC

        expect(comp.exercise.feedbackSuggestionModule).toBeUndefined();
    });

    it('should change publication of tests for programming exercise with published solution', () => {
        comp.exercise = { ...exercise, exampleSolutionPublicationDate: dayjs() };
        expect(comp.exercise.releaseTestsWithExampleSolution).toBeFalsy();
        comp.toggleReleaseTests();
        expect(comp.exercise.releaseTestsWithExampleSolution).toBeTrue();
    });

    it('should not cascade date changes when updateReleaseDate is called when readOnly is true', () => {
        comp.exercise = exercise;
        const oldRelease = dayjs().add(10, 'days');
        comp.updateReleaseDate(oldRelease);

        expect(comp.exercise.releaseDate).toEqual(oldRelease);
        expect(comp.exercise.dueDate).toEqual(oldRelease);
        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(oldRelease);

        comp.readOnly = true;

        const newRelease = dayjs().add(20, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise.releaseDate).toEqual(newRelease);
        expect(comp.exercise.dueDate).toEqual(oldRelease);
        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(oldRelease);
    });

    it('should not cascade date changes when updateExampleSolutionPublicationDate is called when readOnly is true', () => {
        const oldDueDate = dayjs().add(10, 'days');
        comp.exercise = exercise;
        exercise.dueDate = oldDueDate;
        comp.updateExampleSolutionPublicationDate(oldDueDate);

        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(oldDueDate);

        comp.readOnly = true;

        const newDueDate = dayjs().add(20, 'days');
        exercise.dueDate = newDueDate;
        comp.updateExampleSolutionPublicationDate(newDueDate);

        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(oldDueDate);
    });

    it('should alert correct date when exampleSolutionPublicationDate is updated automatically', () => {
        const alertSpy = jest.spyOn(window, 'alert');

        const now = dayjs();
        exercise.dueDate = now.add(10, 'days');
        exercise.releaseDate = now.add(20, 'days');
        comp.exercise = exercise;

        comp.updateExampleSolutionPublicationDate(exercise.releaseDate);

        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(exercise.releaseDate);
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenLastCalledWith('artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsReleaseDate');

        exercise.dueDate = now.add(40, 'days');
        exercise.releaseDate = now.add(30, 'days');
        comp.updateExampleSolutionPublicationDate(exercise.dueDate);

        expect(comp.exercise.exampleSolutionPublicationDate).toEqual(exercise.dueDate);
        expect(alertSpy).toHaveBeenCalledTimes(2);
        expect(alertSpy).toHaveBeenLastCalledWith('artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsDueDate');
    });

    it('should alert each distinct string only once', () => {
        const alertSpy = jest.spyOn(window, 'alert');

        const newExercise = { ...exercise, includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY };

        const now = dayjs();
        newExercise.dueDate = now.add(10, 'days');
        newExercise.releaseDate = now.add(20, 'days');
        newExercise.startDate = now.add(21, 'days');
        comp.exercise = newExercise;

        comp.ngOnChanges({ exercise: { currentValue: newExercise } as SimpleChange });

        expect(alertSpy).toHaveBeenCalledTimes(3);
        let nthCall = 0;
        expect(alertSpy).toHaveBeenNthCalledWith(++nthCall, 'artemisApp.programmingExercise.timeline.alertNewDueDate');
        expect(alertSpy).toHaveBeenNthCalledWith(++nthCall, 'artemisApp.programmingExercise.timeline.alertNewAfterDueDate');
        expect(alertSpy).toHaveBeenNthCalledWith(++nthCall, 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsDueDate');

        const newerExercise = { ...newExercise };
        newerExercise.dueDate = now.add(40, 'days');
        comp.exercise = newerExercise;
        comp.ngOnChanges({ exercise: { currentValue: newerExercise } as SimpleChange });

        expect(alertSpy).toHaveBeenCalledTimes(nthCall + 1);
        expect(alertSpy).toHaveBeenNthCalledWith(++nthCall, 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsDueDate');
    });

    it('should enable checkbox for complaints on automatic assessments for automatically assessed exam exercises', () => {
        comp.exercise = exercise;
        comp.exercise.assessmentType = AssessmentType.AUTOMATIC;
        comp.isExamMode = true;
        exercise.dueDate = undefined;
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment');
        expectElementToBeEnabled(checkbox);
    });

    it.each([true, false])('should disable checkbox for complaints on automatic assessments for exercises without automatic assessment', (examMode: boolean) => {
        comp.exercise = exercise;
        comp.exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        comp.isExamMode = examMode;
        if (!examMode) {
            exercise.course = new Course();
            exercise.course.complaintsEnabled = true;
        }
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment');
        expectElementToBeDisabled(checkbox);
    });

    it('should disable checkbox for complaints on automatic assessments for automatically assessed course exercises without due date', () => {
        comp.exercise = exercise;
        comp.exercise.assessmentType = AssessmentType.AUTOMATIC;
        comp.isExamMode = false;
        exercise.dueDate = undefined;
        exercise.course = new Course();
        exercise.course.complaintsEnabled = true;
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment');
        expectElementToBeDisabled(checkbox);
    });

    it.each([true, false])('should enable checkbox to include tests in example solution', (examMode: boolean) => {
        comp.exercise = exercise;
        comp.isExamMode = examMode;
        if (!examMode) {
            exercise.course = new Course();
            exercise.course.complaintsEnabled = true;
        } else {
            comp.exercise.exampleSolutionPublicationDate = undefined;
            comp.exercise.exerciseGroup = { exam: { exampleSolutionPublicationDate } };
        }
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution');
        expectElementToBeEnabled(checkbox);
    });

    it('should disable checkbox to include tests in example solution without example solution publication date', () => {
        comp.exercise = exercise;
        comp.exercise.exampleSolutionPublicationDate = undefined;
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution');
        expectElementToBeDisabled(checkbox);
    });

    it('should calculate form validation status', fakeAsync(() => {
        const datePicker = { dateInput: { valueChanges: new Subject(), valid: true, value: new Date() } } as any as ProgrammingExerciseTestScheduleDatePickerComponent;
        comp.datePickerComponents = { changes: new Subject(), toArray: () => [datePicker] } as any as QueryList<ProgrammingExerciseTestScheduleDatePickerComponent>;
        comp.ngAfterViewInit();
        (comp.datePickerComponents.changes as Subject<any>).next({ toArray: () => [datePicker] });
        (datePicker.dateInput.valueChanges as Subject<boolean>).next(true);
        tick();
        expect(comp.formValid).toBeTrue();
        expect(comp.formEmpty).toBeTrue();
    }));
});
