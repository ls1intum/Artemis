import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseLifecycleComponent } from 'app/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/programming/shared/lifecycle/test-schedule-date-picker/programming-exercise-test-schedule-date-picker.component';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
const expectElementToBeEnabled = (element: null | any) => {
    expect(element).not.toBeNull();
    expect(element.disabled).toBe(false);
};
const expectElementToBeDisabled = (element: null | any) => {
    expect(element).not.toBeNull();
    expect(element.disabled).toBe(true);
};
import { Course } from 'app/core/course/shared/entities/course.model';
import { Subject, of } from 'rxjs';
import { ActivatedRoute, UrlSegment, convertToParamMap } from '@angular/router';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('ProgrammingExerciseLifecycleComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ProgrammingExerciseLifecycleComponent;
    let fixture: ComponentFixture<ProgrammingExerciseLifecycleComponent>;

    const startDate = dayjs().add(5, 'days');
    const nextDueDate = dayjs().add(6, 'days');
    const afterDueDate = dayjs().add(7, 'days');
    const exampleSolutionPublicationDate = dayjs().add(9, 'days');
    let exercise: ProgrammingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId: '1' }) },
                        url: of([{ path: 'programming-exercises' }] as UrlSegment[]),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseLifecycleComponent);
        comp = fixture.componentInstance;

        exercise = {
            id: 42,
            dueDate: nextDueDate,
            startDate,
            buildAndTestStudentSubmissionsAfterDueDate: afterDueDate,
            exampleSolutionPublicationDate,
        } as ProgrammingExercise;

        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            releaseDate: true,
            startDate: true,
            dueDate: true,
            runTestsAfterDueDate: true,
            assessmentDueDate: true,
            exampleSolutionPublicationDate: true,
            complaintOnAutomaticAssessment: true,
            manualFeedbackRequests: true,
            showTestNamesToStudents: true,
            includeTestsIntoExampleSolution: true,
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should do nothing if the release date is set to null', () => {
        fixture.componentRef.setInput('exercise', exercise);
        comp.updateReleaseDate(undefined);

        expect(comp.exercise()!.startDate).toEqual(startDate);
        expect(comp.exercise()!.dueDate).toEqual(nextDueDate);
        expect(comp.exercise()!.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);
        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(exampleSolutionPublicationDate);
    });

    it('should only reset the due date if the release date is between the due date and the after due date', () => {
        fixture.componentRef.setInput('exercise', exercise);
        const newRelease = dayjs().add(6, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise()!.startDate).toEqual(newRelease);
        expect(comp.exercise()!.dueDate).toEqual(newRelease);
        expect(comp.exercise()!.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);
        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(exampleSolutionPublicationDate);
    });

    it('should reset the due date, the after due date and the example solution publication date if the new release is after all dates', () => {
        fixture.componentRef.setInput('exercise', exercise);
        const newRelease = dayjs().add(10, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise()!.releaseDate).toEqual(newRelease);
        expect(comp.exercise()!.startDate).toEqual(newRelease);
        expect(comp.exercise()!.dueDate).toEqual(newRelease);
        expect(comp.exercise()!.buildAndTestStudentSubmissionsAfterDueDate).toEqual(newRelease);
        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(newRelease);
    });

    it('should only reset the due date if the start date is between the due date and the after due date', () => {
        fixture.componentRef.setInput('exercise', exercise);
        const newStart = dayjs().add(6, 'days');
        comp.updateStartDate(newStart);

        expect(comp.exercise()!.dueDate).toEqual(newStart);
        expect(comp.exercise()!.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);
        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(exampleSolutionPublicationDate);
    });

    it('should reset the due date, the after due date and the example solution publication date if the new start date is after all dates', () => {
        fixture.componentRef.setInput('exercise', exercise);
        const newStart = dayjs().add(10, 'days');
        comp.updateStartDate(newStart);

        expect(comp.exercise()!.dueDate).toEqual(newStart);
        expect(comp.exercise()!.buildAndTestStudentSubmissionsAfterDueDate).toEqual(newStart);
        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(newStart);
    });

    it('should reset the example solution publication date if the new due date is later', () => {
        const newDueDate = dayjs().add(10, 'days');
        fixture.componentRef.setInput('exercise', exercise);
        exercise.dueDate = newDueDate;
        comp.updateExampleSolutionPublicationDate(newDueDate);

        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(newDueDate);
    });

    it('should change the value for allowing complaints for exercise with automatic assessment after toggling', () => {
        fixture.componentRef.setInput('exercise', exercise);
        comp.exercise()!.allowComplaintsForAutomaticAssessments = false;
        comp.exercise()!.assessmentType = AssessmentType.AUTOMATIC;
        comp.toggleComplaintsType();

        expect(comp.exercise()!.allowComplaintsForAutomaticAssessments).toBe(true);
    });

    it('should change feedback request allowed after toggling', () => {
        fixture.componentRef.setInput('exercise', { ...exercise, allowFeedbackRequests: false });
        expect(comp.exercise()!.allowFeedbackRequests).toBe(false);

        comp.toggleFeedbackRequests();

        expect(comp.exercise()!.allowFeedbackRequests).toBe(true);
    });

    it('should change assessment type from automatic to semi-automatic after toggling', () => {
        fixture.componentRef.setInput('exercise', exercise);
        comp.exercise()!.assessmentType = AssessmentType.AUTOMATIC;
        comp.toggleAssessmentType();

        expect(comp.exercise()!.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
        expect(comp.exercise()!.allowComplaintsForAutomaticAssessments).toBe(false);
    });

    it('should change assessment type from semi-automatic to automatic after toggling', () => {
        fixture.componentRef.setInput('exercise', exercise);
        comp.exercise()!.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        comp.toggleAssessmentType();

        expect(comp.exercise()!.assessmentType).toBe(AssessmentType.AUTOMATIC);
        expect(comp.exercise()!.assessmentDueDate).toBeUndefined();
    });

    it('should disable feedback suggestions when changing the assessment type to automatic', () => {
        fixture.componentRef.setInput('exercise', exercise);
        comp.exercise()!.id = undefined;
        comp.ngOnInit();

        expect(comp.exercise()!.assessmentType).toBe(AssessmentType.AUTOMATIC);

        comp.exercise()!.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        comp.exercise()!.feedbackSuggestionModule = 'programming_module';
        comp.toggleAssessmentType(); // toggle to AUTOMATIC

        expect(comp.exercise()!.feedbackSuggestionModule).toBeUndefined();
    });

    it('should change publication of tests for programming exercise with published solution', () => {
        fixture.componentRef.setInput('exercise', { ...exercise, exampleSolutionPublicationDate: dayjs() });
        expect(comp.exercise()!.releaseTestsWithExampleSolution).toBeFalsy();
        comp.toggleReleaseTests();
        expect(comp.exercise()!.releaseTestsWithExampleSolution).toBe(true);
    });

    it('should not cascade date changes when updateReleaseDate is called when readOnly is true', () => {
        fixture.componentRef.setInput('exercise', exercise);
        const oldRelease = dayjs().add(10, 'days');
        comp.updateReleaseDate(oldRelease);

        expect(comp.exercise()!.releaseDate).toEqual(oldRelease);
        expect(comp.exercise()!.dueDate).toEqual(oldRelease);
        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(oldRelease);

        fixture.componentRef.setInput('readOnly', true);

        const newRelease = dayjs().add(20, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise()!.releaseDate).toEqual(newRelease);
        expect(comp.exercise()!.dueDate).toEqual(oldRelease);
        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(oldRelease);
    });

    it('should not cascade date changes when updateExampleSolutionPublicationDate is called when readOnly is true', () => {
        const oldDueDate = dayjs().add(10, 'days');
        fixture.componentRef.setInput('exercise', exercise);
        exercise.dueDate = oldDueDate;
        comp.updateExampleSolutionPublicationDate(oldDueDate);

        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(oldDueDate);

        fixture.componentRef.setInput('readOnly', true);

        const newDueDate = dayjs().add(20, 'days');
        exercise.dueDate = newDueDate;
        comp.updateExampleSolutionPublicationDate(newDueDate);

        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(oldDueDate);
    });

    it('should alert correct date when exampleSolutionPublicationDate is updated automatically', () => {
        const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});

        const now = dayjs();
        exercise.dueDate = now.add(10, 'days');
        exercise.releaseDate = now.add(20, 'days');
        fixture.componentRef.setInput('exercise', exercise);

        comp.updateExampleSolutionPublicationDate(exercise.releaseDate);

        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(exercise.releaseDate);
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenLastCalledWith('artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsReleaseDate');

        exercise.dueDate = now.add(40, 'days');
        exercise.releaseDate = now.add(30, 'days');
        comp.updateExampleSolutionPublicationDate(exercise.dueDate);

        expect(comp.exercise()!.exampleSolutionPublicationDate).toEqual(exercise.dueDate);
        expect(alertSpy).toHaveBeenCalledTimes(2);
        expect(alertSpy).toHaveBeenLastCalledWith('artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsDueDate');
    });

    it('should alert each distinct string only once', () => {
        const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});

        const newExercise = { ...exercise, includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY };

        const now = dayjs();
        newExercise.dueDate = now.add(10, 'days');
        newExercise.releaseDate = now.add(20, 'days');
        newExercise.startDate = now.add(21, 'days');
        fixture.componentRef.setInput('exercise', newExercise);
        fixture.detectChanges(); // runs ngOnInit + initial date cascade

        expect(alertSpy).toHaveBeenCalledTimes(3);
        let nthCall = 0;
        expect(alertSpy).toHaveBeenNthCalledWith(++nthCall, 'artemisApp.programmingExercise.timeline.alertNewDueDate');
        expect(alertSpy).toHaveBeenNthCalledWith(++nthCall, 'artemisApp.programmingExercise.timeline.alertNewAfterDueDate');
        expect(alertSpy).toHaveBeenNthCalledWith(++nthCall, 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsDueDate');

        const newerExercise = { ...newExercise };
        newerExercise.dueDate = now.add(40, 'days');
        fixture.componentRef.setInput('exercise', newerExercise);
        fixture.detectChanges(); // flushes the effect for the new exercise reference

        expect(alertSpy).toHaveBeenCalledTimes(nthCall + 1);
        expect(alertSpy).toHaveBeenNthCalledWith(nthCall + 1, 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsDueDate');
    });

    it('should enable checkbox for complaints on automatic assessments for automatically assessed exam exercises', () => {
        fixture.componentRef.setInput('exercise', exercise);
        comp.exercise()!.assessmentType = AssessmentType.AUTOMATIC;
        fixture.componentRef.setInput('isExamMode', true);
        exercise.dueDate = undefined;
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment');
        expectElementToBeEnabled(checkbox);
    });

    it.each([true, false])('should disable checkbox for complaints on automatic assessments for exercises without automatic assessment', (examMode: boolean) => {
        fixture.componentRef.setInput('exercise', exercise);
        comp.exercise()!.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        fixture.componentRef.setInput('isExamMode', examMode);
        if (!examMode) {
            exercise.course = new Course();
            exercise.course.complaintsEnabled = true;
        }
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment');
        expectElementToBeDisabled(checkbox);
    });

    it('should disable checkbox for complaints on automatic assessments for automatically assessed course exercises without due date', () => {
        fixture.componentRef.setInput('exercise', exercise);
        comp.exercise()!.assessmentType = AssessmentType.AUTOMATIC;
        fixture.componentRef.setInput('isExamMode', false);
        exercise.dueDate = undefined;
        exercise.course = new Course();
        exercise.course.complaintsEnabled = true;
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment');
        expectElementToBeDisabled(checkbox);
    });

    it.each([true, false])('should enable checkbox to include tests in example solution', (examMode: boolean) => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('isExamMode', examMode);
        if (!examMode) {
            exercise.course = new Course();
            exercise.course.complaintsEnabled = true;
        } else {
            comp.exercise()!.exampleSolutionPublicationDate = undefined;
            comp.exercise()!.exerciseGroup = { exam: { exampleSolutionPublicationDate } };
        }
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution');
        expectElementToBeEnabled(checkbox);
    });

    it('should disable checkbox to include tests in example solution without example solution publication date', () => {
        fixture.componentRef.setInput('exercise', exercise);
        comp.exercise()!.exampleSolutionPublicationDate = undefined;
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution');
        expectElementToBeDisabled(checkbox);
    });

    it('should calculate form validation status', async () => {
        // Component template would read exercise.* during the implicit change-detection cycle in
        // zoneless mode; seed the input so detectChanges doesn't throw.
        fixture.componentRef.setInput('exercise', exercise);
        const valueChanges = new Subject<boolean>();
        const datePicker = {
            dateInput: () => ({
                valueChanges,
                valid: true,
                value: new Date(),
            }),
        } as any as ProgrammingExerciseTestScheduleDatePickerComponent;
        // viewChildren() is now a signal; override the property directly with a fake signal that
        // returns the in-test picker array. The component only reads via `this.datePickerComponents()`.
        Object.defineProperty(comp, 'datePickerComponents', { value: () => [datePicker], writable: true });
        comp.ngAfterViewInit();
        valueChanges.next(true);
        await new Promise((resolve) => setTimeout(resolve, 10));
        expect(comp.formValid).toBe(true);
        expect(comp.formEmpty).toBe(true);
    });
});
