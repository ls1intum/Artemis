import { Component } from '@angular/core';
import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseUpdateTimelineComponent } from './programming-exercise-update-timeline.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { of } from 'rxjs';
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
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { Course } from 'app/core/course/shared/entities/course.model';
import { By } from '@angular/platform-browser';

@Component({
    imports: [ProgrammingExerciseUpdateTimelineComponent],
    template: `
        <jhi-programming-exercise-update-timeline
            [exercise]="exercise"
            [isExamMode]="isExamMode"
            [complaintsInCourseDisabled]="true"
            [exampleSolutionPublicationDateSet]="!!exercise.exampleSolutionPublicationDate"
            [isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord]="isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord"
            [(dueDate)]="exercise.dueDate"
            [(assessmentDueDate)]="exercise.assessmentDueDate"
            [(assessmentType)]="exercise.assessmentType"
            [(allowComplaintsForAutomaticAssessments)]="exercise.allowComplaintsForAutomaticAssessments"
            [(allowFeedbackRequests)]="exercise.allowFeedbackRequests"
            [(feedbackSuggestionModule)]="exercise.feedbackSuggestionModule"
            [(releaseTestsWithExampleSolution)]="exercise.releaseTestsWithExampleSolution"
        />
    `,
})
class TestHostComponent {
    exercise = {} as ProgrammingExercise;
    isExamMode = false;

    isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = {
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
    } as Record<ProgrammingExerciseInputField, boolean>;
}

describe('ProgrammingExerciseLifecycleComponent', () => {
    let fixture: ComponentFixture<TestHostComponent>;

    const startDate = dayjs().add(5, 'days');
    const nextDueDate = dayjs().add(6, 'days');
    const afterDueDate = dayjs().add(7, 'days');
    const exampleSolutionPublicationDate = dayjs().add(9, 'days');
    let exercise: ProgrammingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule, TestHostComponent],
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

        exercise = {
            id: 42,
            dueDate: nextDueDate,
            startDate,
            buildAndTestStudentSubmissionsAfterDueDate: afterDueDate,
            exampleSolutionPublicationDate,
        } as ProgrammingExercise;
    });

    const createHostComponent = () => {
        fixture = TestBed.createComponent(TestHostComponent);
        fixture.componentInstance.exercise = exercise;
        fixture.detectChanges();
    };

    const getCheckbox = (id: string): HTMLInputElement => fixture.debugElement.nativeElement.querySelector(`#${id}`);

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should change the value for allowing complaints for exercise with automatic assessment', () => {
        exercise.allowComplaintsForAutomaticAssessments = false;
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        createHostComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment');

        expect(exercise.allowComplaintsForAutomaticAssessments).toBeFalse();
        expect(checkbox.checked).toBeFalse();

        checkbox.click();
        fixture.detectChanges();

        expect(exercise.allowComplaintsForAutomaticAssessments).toBeTrue();
    });

    it('should change feedback request allowed', () => {
        exercise.allowFeedbackRequests = false;
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        createHostComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowFeedbackRequests');

        expect(exercise.allowFeedbackRequests).toBeFalse();
        expect(checkbox.checked).toBeFalse();

        checkbox.click();
        fixture.detectChanges();

        expect(exercise.allowFeedbackRequests).toBeTrue();
    });

    it('should change assessment type from automatic to semi-automatic', () => {
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        createHostComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled');

        expect(exercise.assessmentType).toBe(AssessmentType.AUTOMATIC);
        expect(checkbox.checked).toBeFalse();

        checkbox.click();
        fixture.detectChanges();

        expect(exercise.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
    });

    it('should change assessment type from semi-automatic to automatic', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.assessmentDueDate = afterDueDate;
        createHostComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled');

        expect(exercise.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
        expect(checkbox.checked).toBeTrue();

        checkbox.click();
        fixture.detectChanges();

        expect(exercise.assessmentType).toBe(AssessmentType.AUTOMATIC);
        expect(exercise.assessmentDueDate).toBeUndefined();
    });

    it('should disable feedback suggestions when changing the assessment type to automatic', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.feedbackSuggestionModule = 'programming_module';
        createHostComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled');

        checkbox.click();
        fixture.detectChanges();

        expect(exercise.assessmentType).toBe(AssessmentType.AUTOMATIC);
        expect(exercise.feedbackSuggestionModule).toBeUndefined();
    });

    it('should enable release tests with example solution', () => {
        exercise.exampleSolutionPublicationDate = dayjs();
        exercise.releaseTestsWithExampleSolution = false;
        createHostComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution');

        expect(exercise.releaseTestsWithExampleSolution).toBeFalse();
        expect(checkbox.checked).toBeFalse();

        checkbox.click();
        fixture.detectChanges();

        expect(exercise.releaseTestsWithExampleSolution).toBeTrue();
    });

    it('should enable checkbox for complaints on automatic assessments for exam exercises with automatic assessment', () => {
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.dueDate = undefined;
        createHostComponent();
        fixture.componentInstance.isExamMode = true;
        fixture.detectChanges();

        expect(getCheckbox('allowComplaintsForAutomaticAssessment').disabled).toBeFalse();
    });

    it.each([true, false])('should disable checkbox for complaints on automatic assessments for exercises without automatic assessment', async (examMode: boolean) => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        if (!examMode) {
            exercise.course = new Course();
            exercise.course.complaintsEnabled = true;
        }
        createHostComponent();
        fixture.componentInstance.isExamMode = examMode;
        await fixture.whenStable();
        fixture.detectChanges();

        expect(getCheckbox('allowComplaintsForAutomaticAssessment').disabled).toBeTrue();
    });

    it('should disable checkbox for complaints on automatic assessments for course exercises with automatic assessment but without due date', async () => {
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.dueDate = undefined;
        exercise.course = new Course();
        exercise.course.complaintsEnabled = true;
        createHostComponent();

        fixture.componentInstance.isExamMode = false;
        await fixture.whenStable();
        fixture.detectChanges();

        expect(getCheckbox('allowComplaintsForAutomaticAssessment').disabled).toBeTrue();
    });

    it.each([true, false])('should enable checkbox to include tests in example solution', (examMode: boolean) => {
        if (!examMode) {
            exercise.course = new Course();
            exercise.course.complaintsEnabled = true;
        } else {
            exercise.exampleSolutionPublicationDate = undefined;
            exercise.exerciseGroup = { exam: { exampleSolutionPublicationDate } };
        }
        createHostComponent();
        fixture.componentInstance.isExamMode = examMode;
        fixture.detectChanges();

        expect(getCheckbox('releaseTestsWithExampleSolution').disabled).toBeFalse();
    });

    it('should disable checkbox to include tests in example solution without example solution publication date', async () => {
        exercise.exampleSolutionPublicationDate = undefined;
        createHostComponent();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(getCheckbox('releaseTestsWithExampleSolution').disabled).toBeTrue();
    });

    it('should update form validation status when timeline status changes', () => {
        createHostComponent();
        const component: ProgrammingExerciseUpdateTimelineComponent = fixture.debugElement.query(By.directive(ProgrammingExerciseUpdateTimelineComponent)).componentInstance;
        const formValidChangesSpy = jest.fn();
        component.formValidChanges.subscribe(formValidChangesSpy);

        component.handleTimelineStatusChange({ valid: true, empty: false });

        expect(component.formValid).toBeTrue();
        expect(component.formEmpty).toBeFalse();
        expect(formValidChangesSpy).toHaveBeenCalledWith(true);

        component.handleTimelineStatusChange({ valid: false, empty: true });

        expect(component.formValid).toBeFalse();
        expect(component.formEmpty).toBeTrue();
        expect(formValidChangesSpy).toHaveBeenLastCalledWith(false);
    });
});
