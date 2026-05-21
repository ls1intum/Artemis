import { Component } from '@angular/core';
import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseUpdateTimelineComponent } from './programming-exercise-update-timeline.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { BehaviorSubject } from 'rxjs';
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

@Component({
    imports: [ProgrammingExerciseUpdateTimelineComponent],
    template: `
        <jhi-programming-exercise-update-timeline
            [exercise]="exercise"
            [isExamMode]="isExamMode"
            [complaintsInCourseEnabled]="true"
            [exampleSolutionPublicationDateSet]="!!exercise.exampleSolutionPublicationDate"
            [isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord]="isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord"
            [(dueDate)]="exercise.dueDate"
            [(buildAndTestStudentSubmissionsAfterDueDate)]="exercise.buildAndTestStudentSubmissionsAfterDueDate"
            [(assessmentDueDate)]="exercise.assessmentDueDate"
            [(exampleSolutionPublicationDate)]="exercise.exampleSolutionPublicationDate"
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

    isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord: Record<ProgrammingExerciseInputField, boolean> | undefined = undefined;
}

describe('ProgrammingExerciseLifecycleComponent', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let component: ProgrammingExerciseUpdateTimelineComponent;
    let activatedRouteUrlSubject: BehaviorSubject<UrlSegment[]>;

    const startDate = dayjs().add(5, 'days');
    const nextDueDate = dayjs().add(6, 'days');
    const afterDueDate = dayjs().add(7, 'days');
    const exampleSolutionPublicationDate = dayjs().add(9, 'days');
    let exercise: ProgrammingExercise;

    beforeEach(() => {
        activatedRouteUrlSubject = new BehaviorSubject<UrlSegment[]>([{ path: 'programming-exercises' }] as UrlSegment[]);
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule, TestHostComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId: '1' }) },
                        url: activatedRouteUrlSubject.asObservable(),
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

    function createHostComponent() {
        fixture = TestBed.createComponent(TestHostComponent);
        fixture.componentInstance.exercise = exercise;
        fixture.detectChanges();
        component = fixture.debugElement.children[0].componentInstance as ProgrammingExerciseUpdateTimelineComponent;
    }

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should show release date picker if not in exam mode and no current mode record is available', () => {
        createHostComponent();

        expect(component.isDatePickerForReleaseDateVisible()).toBeTrue();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.releaseDate')).toBeTrue();
    });

    it('should show release date picker if not in exam mode and current mode record allows release date', () => {
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { releaseDate: true } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isDatePickerForReleaseDateVisible()).toBeTrue();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.releaseDate')).toBeTrue();
    });

    it('should not show release date picker if current mode record prohibits release date', () => {
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { releaseDate: false } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isDatePickerForReleaseDateVisible()).toBeFalse();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.releaseDate')).toBeFalse();
    });

    it('should show start date picker if not in exam mode and no current mode record is available', () => {
        createHostComponent();

        expect(component.isDatePickerForStartDateVisible()).toBeTrue();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.startDate')).toBeTrue();
    });

    it('should show start date picker if not in exam mode and current mode record allows start date', () => {
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { startDate: true } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isDatePickerForStartDateVisible()).toBeTrue();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.startDate')).toBeTrue();
    });

    it('should not show start date picker if current mode record prohibits start date', () => {
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { startDate: false } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isDatePickerForStartDateVisible()).toBeFalse();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.startDate')).toBeFalse();
    });

    it('should show due date picker if not in exam mode and no current mode record is available', () => {
        createHostComponent();

        expect(component.isDatePickerForDueDateVisible()).toBeTrue();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dueDate')).toBeTrue();
    });

    it('should show due date picker if not in exam mode and current mode record allows due date', () => {
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { dueDate: true } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isDatePickerForDueDateVisible()).toBeTrue();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dueDate')).toBeTrue();
    });

    it('should not show due date picker if current mode record prohibits due date', () => {
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { dueDate: false } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isDatePickerForDueDateVisible()).toBeFalse();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dueDate')).toBeFalse();
    });

    it('should display enabling to run tests after due date toggle if in exam mode and no current mode record is available', () => {
        exercise.dueDate = undefined;
        createHostComponent();
        fixture.componentInstance.isExamMode = true;
        fixture.detectChanges();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate')).not.toBeNull();
    });

    it('should display enabling to run tests after due date toggle if in exam mode and current mode record allows it', () => {
        exercise.dueDate = undefined;
        createHostComponent();
        fixture.componentInstance.isExamMode = true;
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { runTestsAfterDueDate: true } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate')).not.toBeNull();
    });

    it('should display enabling to run tests after due date toggle if due date is available and no current mode record is available', () => {
        createHostComponent();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate')).not.toBeNull();
    });

    it('should display enabling to run tests after due date toggle if due date is available and current mode record allows it', () => {
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { runTestsAfterDueDate: true } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate')).not.toBeNull();
    });

    it('should not display enabling to run tests after due date toggle if not in exam mode and due date is unavailable', () => {
        exercise.dueDate = undefined;
        createHostComponent();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBeFalse();
        expect(fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate')).toBeNull();
    });

    it('should hide running tests after due date picker and clear its date if enabling toggle becomes unavailable', () => {
        exercise.buildAndTestStudentSubmissionsAfterDueDate = afterDueDate;
        createHostComponent();

        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBeTrue();
        expect(exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);

        exercise.dueDate = undefined;
        fixture.detectChanges();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBeFalse();
        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBeFalse();
        expect(exercise.buildAndTestStudentSubmissionsAfterDueDate).toBeUndefined();
    });

    it('should show and hide running tests after due date picker when toggling it', async () => {
        exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
        createHostComponent();
        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBeTrue();
        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBeFalse();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dateForRunningTestsAfterDueDate')).toBeFalse();

        fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate').click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBeTrue();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dateForRunningTestsAfterDueDate')).toBeTrue();

        const enabledCheckbox = fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate');
        enabledCheckbox.checked = true;
        enabledCheckbox.click();
        fixture.detectChanges();

        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBeFalse();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dateForRunningTestsAfterDueDate')).toBeFalse();
    });

    it('should display example solution publication date toggle if not in exam mode and no current mode record is available', () => {
        createHostComponent();

        expect(component.isExampleSolutionPublicationDateToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#exampleSolutionPublicationDateEnabled')).not.toBeNull();
    });

    it('should display example solution publication date toggle if not in exam mode and current mode record allows example solution publication date', () => {
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { exampleSolutionPublicationDate: true } as Record<
            ProgrammingExerciseInputField,
            boolean
        >;
        fixture.detectChanges();

        expect(component.isExampleSolutionPublicationDateToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#exampleSolutionPublicationDateEnabled')).not.toBeNull();
    });

    it('should hide example solution publication date picker and clear its date if example solution publication date toggle becomes unavailable', () => {
        createHostComponent();

        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBeTrue();
        expect(exercise.exampleSolutionPublicationDate).toEqual(exampleSolutionPublicationDate);

        fixture.componentInstance.isExamMode = true;
        fixture.detectChanges();

        expect(component.isExampleSolutionPublicationDateToggleVisible()).toBeFalse();
        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBeFalse();
        expect(exercise.exampleSolutionPublicationDate).toBeUndefined();
    });

    it('should show and hide example solution publication date picker when toggling it', async () => {
        exercise.exampleSolutionPublicationDate = undefined;
        createHostComponent();

        expect(component.isExampleSolutionPublicationDateToggleVisible()).toBeTrue();
        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBeFalse();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.exampleSolutionPublicationDate')).toBeFalse();

        fixture.debugElement.nativeElement.querySelector('#exampleSolutionPublicationDateEnabled').click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBeTrue();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.exampleSolutionPublicationDate')).toBeTrue();

        const enabledCheckbox = fixture.debugElement.nativeElement.querySelector('#exampleSolutionPublicationDateEnabled');
        enabledCheckbox.checked = true;
        enabledCheckbox.click();
        fixture.detectChanges();

        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBeFalse();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.exampleSolutionPublicationDate')).toBeFalse();
    });

    it('should display semi-automatic assessment toggle if in exam mode and no current mode record is available', () => {
        exercise.dueDate = undefined;
        createHostComponent();
        fixture.componentInstance.isExamMode = true;
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display semi-automatic assessment toggle if in exam mode and current mode record allows assessment due date', () => {
        exercise.dueDate = undefined;
        createHostComponent();
        fixture.componentInstance.isExamMode = true;
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { assessmentDueDate: true } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display semi-automatic assessment toggle if importing and no current mode record is available', () => {
        exercise.dueDate = undefined;
        activatedRouteUrlSubject.next([{ path: 'import' }] as UrlSegment[]);
        createHostComponent();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display semi-automatic assessment toggle if importing and current mode record allows assessment due date', () => {
        exercise.dueDate = undefined;
        activatedRouteUrlSubject.next([{ path: 'import' }] as UrlSegment[]);
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { assessmentDueDate: true } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display semi-automatic assessment toggle if due date is available and no current mode record is available', () => {
        createHostComponent();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display semi-automatic assessment toggle if due date is available and current mode record allows assessment due date', () => {
        createHostComponent();
        fixture.componentInstance.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = { assessmentDueDate: true } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBeTrue();
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should not display semi-automatic assessment toggle if not in exam mode, not importing, and due date is unavailable', () => {
        exercise.dueDate = undefined;
        createHostComponent();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBeFalse();
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).toBeNull();
    });

    it('should show semi-automatic assessment due date picker if semi-automatic assessment is enabled for a course exercise without feedback requests', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.allowFeedbackRequests = false;
        createHostComponent();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBeTrue();
        expect(component.isDatePickerForSemiAutomaticAssessmentDueDateVisible()).toBeTrue();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.assessmentDueDate')).toBeTrue();
    });

    it('should not show semi-automatic assessment due date picker in exam mode', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        createHostComponent();
        fixture.componentInstance.isExamMode = true;
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBeTrue();
        expect(component.isDatePickerForSemiAutomaticAssessmentDueDateVisible()).toBeFalse();
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.assessmentDueDate')).toBeFalse();
    });

    it('should clear assessment due date if semi-automatic assessment due date picker becomes unavailable', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.assessmentDueDate = afterDueDate;
        createHostComponent();

        expect(component.isDatePickerForSemiAutomaticAssessmentDueDateVisible()).toBeTrue();
        expect(exercise.assessmentDueDate).toEqual(afterDueDate);

        fixture.componentInstance.isExamMode = true;
        fixture.detectChanges();

        expect(component.isDatePickerForSemiAutomaticAssessmentDueDateVisible()).toBeFalse();
        expect(exercise.assessmentDueDate).toBeUndefined();
    });

    it('should clear assessment due date and running tests after due date if feedback requests are allowed', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.allowFeedbackRequests = false;
        exercise.assessmentDueDate = afterDueDate;
        exercise.buildAndTestStudentSubmissionsAfterDueDate = afterDueDate;
        createHostComponent();

        expect(exercise.assessmentDueDate).toEqual(afterDueDate);
        expect(exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);

        component.allowFeedbackRequests.set(true);
        fixture.detectChanges();

        expect(exercise.assessmentDueDate).toBeUndefined();
        expect(exercise.buildAndTestStudentSubmissionsAfterDueDate).toBeUndefined();
    });

    it('should update dependent fields when assessment type changes', () => {
        exercise.assessmentType = undefined;
        createHostComponent();

        component.allowComplaintsForAutomaticAssessments.set(true);
        component.allowFeedbackRequests.set(true);
        component.assessmentType.set(AssessmentType.SEMI_AUTOMATIC);
        fixture.detectChanges();

        expect(exercise.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
        expect(exercise.allowComplaintsForAutomaticAssessments).toBeFalse();
        expect(exercise.allowFeedbackRequests).toBeFalse();

        component.assessmentDueDate.set(afterDueDate);
        component.allowComplaintsForAutomaticAssessments.set(true);
        component.feedbackSuggestionModule.set('programming_module');
        fixture.detectChanges();

        component.assessmentType.set(AssessmentType.AUTOMATIC);
        fixture.detectChanges();

        expect(exercise.assessmentType).toBe(AssessmentType.AUTOMATIC);
        expect(exercise.assessmentDueDate).toBeUndefined();
        expect(exercise.allowComplaintsForAutomaticAssessments).toBeFalse();
        expect(exercise.feedbackSuggestionModule).toBeUndefined();
    });

    it('should update form validation status when timeline status changes', () => {
        createHostComponent();
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

        expect(fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment').disabled).toBeFalse();
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

        expect(fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment').disabled).toBeTrue();
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

        expect(fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment').disabled).toBeTrue();
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

        expect(fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution').disabled).toBeFalse();
    });

    it('should disable checkbox to include tests in example solution without example solution publication date', async () => {
        exercise.exampleSolutionPublicationDate = undefined;
        createHostComponent();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution').disabled).toBeTrue();
    });
});
