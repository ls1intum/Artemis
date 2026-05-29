import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ProgrammingExerciseUpdateTimelineComponent } from './programming-exercise-update-timeline.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { BehaviorSubject } from 'rxjs';
import { ActivatedRoute, UrlSegment, convertToParamMap } from '@angular/router';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { Course } from 'app/course/shared/entities/course.model';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { PROFILE_LOCALCI } from 'app/app.constants';

describe('ProgrammingExerciseUpdateTimelineComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseUpdateTimelineComponent>;
    let component: ProgrammingExerciseUpdateTimelineComponent;
    let activatedRouteUrlSubject: BehaviorSubject<UrlSegment[]>;
    let httpTestingController: HttpTestingController;
    let profileService: ProfileService;

    const startDate = dayjs().add(5, 'days');
    const nextDueDate = dayjs().add(6, 'days');
    const afterDueDate = dayjs().add(7, 'days');
    const exampleSolutionPublicationDate = dayjs().add(9, 'days');
    let exercise: ProgrammingExercise;

    beforeEach(() => {
        activatedRouteUrlSubject = new BehaviorSubject<UrlSegment[]>([{ path: 'programming-exercises' }] as UrlSegment[]);
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule, ProgrammingExerciseUpdateTimelineComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { params: { courseId: '1' }, paramMap: convertToParamMap({ courseId: '1' }) },
                        url: activatedRouteUrlSubject.asObservable(),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                BuildPhasesTemplateService,
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        httpTestingController = TestBed.inject(HttpTestingController);
        profileService = TestBed.inject(ProfileService);

        exercise = {
            id: 42,
            dueDate: nextDueDate,
            startDate,
            buildAndTestStudentSubmissionsAfterDueDate: afterDueDate,
            exampleSolutionPublicationDate,
        } as ProgrammingExercise;
    });

    function createTestComponent() {
        fixture = TestBed.createComponent(ProgrammingExerciseUpdateTimelineComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('complaintsInCourseEnabled', true);
        fixture.componentRef.setInput('exampleSolutionPublicationDateSet', !!exercise.exampleSolutionPublicationDate);
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', undefined);
        fixture.componentRef.setInput('startDate', exercise.startDate);
        fixture.componentRef.setInput('dueDate', exercise.dueDate);
        fixture.componentRef.setInput('buildAndTestStudentSubmissionsAfterDueDate', exercise.buildAndTestStudentSubmissionsAfterDueDate);
        fixture.componentRef.setInput('assessmentDueDate', exercise.assessmentDueDate);
        fixture.componentRef.setInput('exampleSolutionPublicationDate', exercise.exampleSolutionPublicationDate);
        fixture.componentRef.setInput('assessmentType', exercise.assessmentType);
        fixture.componentRef.setInput('allowComplaintsForAutomaticAssessments', exercise.allowComplaintsForAutomaticAssessments);
        fixture.componentRef.setInput('allowFeedbackRequests', exercise.allowFeedbackRequests);
        fixture.componentRef.setInput('feedbackSuggestionModule', exercise.feedbackSuggestionModule);
        fixture.componentRef.setInput('releaseTestsWithExampleSolution', exercise.releaseTestsWithExampleSolution);
        fixture.detectChanges();
    }

    afterEach(() => {
        httpTestingController.verify();
        vi.restoreAllMocks();
    });

    it('should show release date picker if not in exam mode and no current mode record is available', () => {
        createTestComponent();

        expect(component.isDatePickerForReleaseDateVisible()).toBe(true);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.releaseDate')).toBe(true);
    });

    it('should show release date picker if not in exam mode and current mode record allows release date', () => {
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { releaseDate: true } as Record<ProgrammingExerciseInputField, boolean>);
        fixture.detectChanges();

        expect(component.isDatePickerForReleaseDateVisible()).toBe(true);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.releaseDate')).toBe(true);
    });

    it('should not show release date picker if current mode record prohibits release date', () => {
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { releaseDate: false } as Record<ProgrammingExerciseInputField, boolean>);
        fixture.detectChanges();

        expect(component.isDatePickerForReleaseDateVisible()).toBe(false);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.releaseDate')).toBe(false);
    });

    it('should show start date picker if not in exam mode and no current mode record is available', () => {
        createTestComponent();

        expect(component.isDatePickerForStartDateVisible()).toBe(true);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.startDate')).toBe(true);
    });

    it('should show start date picker if not in exam mode and current mode record allows start date', () => {
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { startDate: true } as Record<ProgrammingExerciseInputField, boolean>);
        fixture.detectChanges();

        expect(component.isDatePickerForStartDateVisible()).toBe(true);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.startDate')).toBe(true);
    });

    it('should not show start date picker if current mode record prohibits start date', () => {
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { startDate: false } as Record<ProgrammingExerciseInputField, boolean>);
        fixture.detectChanges();

        expect(component.isDatePickerForStartDateVisible()).toBe(false);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.startDate')).toBe(false);
    });

    it('should show due date picker if not in exam mode and no current mode record is available', () => {
        createTestComponent();

        expect(component.isDatePickerForDueDateVisible()).toBe(true);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dueDate')).toBe(true);
    });

    it('should show due date picker if not in exam mode and current mode record allows due date', () => {
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { dueDate: true } as Record<ProgrammingExerciseInputField, boolean>);
        fixture.detectChanges();

        expect(component.isDatePickerForDueDateVisible()).toBe(true);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dueDate')).toBe(true);
    });

    it('should not show due date picker if current mode record prohibits due date', () => {
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { dueDate: false } as Record<ProgrammingExerciseInputField, boolean>);
        fixture.detectChanges();

        expect(component.isDatePickerForDueDateVisible()).toBe(false);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dueDate')).toBe(false);
    });

    it('should display enabling to run tests after due date toggle if in exam mode and no current mode record is available', () => {
        exercise.dueDate = undefined;
        createTestComponent();
        fixture.componentRef.setInput('isExamMode', true);
        fixture.detectChanges();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate')).not.toBeNull();
    });

    it('should display enabling to run tests after due date toggle if in exam mode and current mode record allows it', () => {
        exercise.dueDate = undefined;
        createTestComponent();
        fixture.componentRef.setInput('isExamMode', true);
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { runTestsAfterDueDate: true } as Record<
            ProgrammingExerciseInputField,
            boolean
        >);
        fixture.detectChanges();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate')).not.toBeNull();
    });

    it('should display enabled enabling to run tests after due date toggle if due date is available and no current mode record is available', () => {
        createTestComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate');

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBe(true);
        expect(component.isEnablingToRunTestsAfterDueDateToggleEnabled()).toBe(true);
        expect(checkbox).not.toBeNull();
        expect(checkbox.disabled).toBe(false);
    });

    it('should display enabling to run tests after due date toggle if due date is available and current mode record allows it', () => {
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { runTestsAfterDueDate: true } as Record<
            ProgrammingExerciseInputField,
            boolean
        >);
        fixture.detectChanges();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate')).not.toBeNull();
    });

    it('should display disabled enabling to run tests after due date toggle if not in exam mode and due date is unavailable', async () => {
        exercise.dueDate = undefined;
        createTestComponent();
        await fixture.whenStable();
        fixture.detectChanges();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate');

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBe(true);
        expect(component.isEnablingToRunTestsAfterDueDateToggleEnabled()).toBe(false);
        expect(checkbox).not.toBeNull();
        expect(checkbox.disabled).toBe(true);
    });

    it('should hide running tests after due date picker and clear its date if enabling toggle becomes disabled', () => {
        exercise.buildAndTestStudentSubmissionsAfterDueDate = afterDueDate;
        createTestComponent();

        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBe(true);
        expect(component.buildAndTestStudentSubmissionsAfterDueDate()).toEqual(afterDueDate);

        component.dueDate.set(undefined);
        fixture.detectChanges();

        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBe(true);
        expect(component.isEnablingToRunTestsAfterDueDateToggleEnabled()).toBe(false);
        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBe(false);
        expect(component.buildAndTestStudentSubmissionsAfterDueDate()).toBeUndefined();
    });

    it('should show and hide running tests after due date picker when toggling it', async () => {
        exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
        createTestComponent();
        expect(component.isEnablingToRunTestsAfterDueDateToggleVisible()).toBe(true);
        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBe(false);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dateForRunningTestsAfterDueDate')).toBe(false);

        fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate').click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBe(true);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dateForRunningTestsAfterDueDate')).toBe(true);

        const enabledCheckbox = fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate');
        enabledCheckbox.checked = true;
        enabledCheckbox.click();
        fixture.detectChanges();

        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBe(false);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.dateForRunningTestsAfterDueDate')).toBe(false);
    });

    it('should preview the run tests after due date in LocalCI mode', () => {
        vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile) => profile === PROFILE_LOCALCI);
        exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
        createTestComponent();

        const req = httpTestingController.expectOne('api/programming/programming-exercises/timeline/automatic-after-due-date-preview');
        expect(req.request.method).toBe('POST');
        expect(req.request.body.programmingExerciseId).toBe(42);
        expect(req.request.body.dueDate).toBeTruthy();

        req.flush(afterDueDate.toISOString());
        fixture.detectChanges();

        expect(component.buildAndTestStudentSubmissionsAfterDueDate()?.toISOString()).toBe(afterDueDate.toISOString());
        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBe(true);
        expect(component.timelineItems().find((item) => item.labelStringKey === 'artemisApp.exercise.dateForRunningTestsAfterDueDate')?.clearable).toBe(false);
        expect(fixture.debugElement.nativeElement.querySelector('#defineDateForRunningTestsAfterDueDate')).toBeNull();
    });

    it('should disable and reset feedback requests in LocalCI mode when the automatically managed run tests after due date exists', async () => {
        vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile) => profile === PROFILE_LOCALCI);
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.allowFeedbackRequests = true;
        createTestComponent();

        const req = httpTestingController.expectOne('api/programming/programming-exercises/timeline/automatic-after-due-date-preview');
        req.flush(afterDueDate.toISOString());
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowFeedbackRequests');

        expect(component.assessmentDueDate()).toBeUndefined();
        expect(component.allowFeedbackRequests()).toBe(false);
        expect(component.buildAndTestStudentSubmissionsAfterDueDate()?.toISOString()).toBe(afterDueDate.toISOString());
        expect(component.isFeedbackRequestsToggleEnabled()).toBe(false);
        expect(checkbox.disabled).toBe(true);

        component.allowFeedbackRequests.set(true);
        fixture.detectChanges();

        expect(component.allowFeedbackRequests()).toBe(false);
    });

    it('should not preview a LocalCI import when the imported build plan has no after due date phase', () => {
        vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile) => profile === PROFILE_LOCALCI);
        activatedRouteUrlSubject.next([{ path: 'import' }] as UrlSegment[]);
        exercise.buildConfig = {
            buildPlanConfiguration: JSON.stringify({
                phases: [{ name: 'test', script: 'echo test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }],
            }),
        } as any;
        createTestComponent();

        httpTestingController.expectNone('api/programming/programming-exercises/timeline/automatic-after-due-date-preview');
        expect(component.buildAndTestStudentSubmissionsAfterDueDate()).toBeUndefined();
        expect(component.isDatePickerForRunningTestsAfterDueDateVisible()).toBe(false);
    });

    it('should display example solution publication date toggle if not in exam mode and no current mode record is available', () => {
        createTestComponent();

        expect(component.isExampleSolutionPublicationDateToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#exampleSolutionPublicationDateEnabled')).not.toBeNull();
    });

    it('should display example solution publication date toggle if not in exam mode and current mode record allows example solution publication date', () => {
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { exampleSolutionPublicationDate: true } as Record<
            ProgrammingExerciseInputField,
            boolean
        >);
        fixture.detectChanges();

        expect(component.isExampleSolutionPublicationDateToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#exampleSolutionPublicationDateEnabled')).not.toBeNull();
    });

    it('should hide example solution publication date picker and clear its date if example solution publication date toggle becomes unavailable', () => {
        createTestComponent();

        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBe(true);
        expect(component.exampleSolutionPublicationDate()).toEqual(exampleSolutionPublicationDate);

        fixture.componentRef.setInput('isExamMode', true);
        fixture.detectChanges();

        expect(component.isExampleSolutionPublicationDateToggleVisible()).toBe(false);
        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBe(false);
        expect(component.exampleSolutionPublicationDate()).toBeUndefined();
    });

    it('should show and hide example solution publication date picker when toggling it', async () => {
        exercise.exampleSolutionPublicationDate = undefined;
        createTestComponent();

        expect(component.isExampleSolutionPublicationDateToggleVisible()).toBe(true);
        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBe(false);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.exampleSolutionPublicationDate')).toBe(false);

        fixture.debugElement.nativeElement.querySelector('#exampleSolutionPublicationDateEnabled').click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBe(true);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.exampleSolutionPublicationDate')).toBe(true);

        const enabledCheckbox = fixture.debugElement.nativeElement.querySelector('#exampleSolutionPublicationDateEnabled');
        enabledCheckbox.checked = true;
        enabledCheckbox.click();
        fixture.detectChanges();

        expect(component.isDatePickerForExampleSolutionPublicationDateVisible()).toBe(false);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.exampleSolutionPublicationDate')).toBe(false);
    });

    it('should display semi-automatic assessment toggle if in exam mode and no current mode record is available', () => {
        exercise.dueDate = undefined;
        createTestComponent();
        fixture.componentRef.setInput('isExamMode', true);
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display semi-automatic assessment toggle if in exam mode and current mode record allows assessment due date', () => {
        exercise.dueDate = undefined;
        createTestComponent();
        fixture.componentRef.setInput('isExamMode', true);
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { assessmentDueDate: true } as Record<
            ProgrammingExerciseInputField,
            boolean
        >);
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display semi-automatic assessment toggle if importing and no current mode record is available', () => {
        exercise.dueDate = undefined;
        activatedRouteUrlSubject.next([{ path: 'import' }] as UrlSegment[]);
        createTestComponent();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display semi-automatic assessment toggle if importing and current mode record allows assessment due date', () => {
        exercise.dueDate = undefined;
        activatedRouteUrlSubject.next([{ path: 'import' }] as UrlSegment[]);
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { assessmentDueDate: true } as Record<
            ProgrammingExerciseInputField,
            boolean
        >);
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display enabled semi-automatic assessment toggle if due date is available and no current mode record is available', () => {
        createTestComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled');

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBe(true);
        expect(component.isSemiAutomaticAssessmentToggleEnabled()).toBe(true);
        expect(checkbox).not.toBeNull();
        expect(checkbox.disabled).toBe(false);
    });

    it('should display semi-automatic assessment toggle if due date is available and current mode record allows assessment due date', () => {
        createTestComponent();
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', { assessmentDueDate: true } as Record<
            ProgrammingExerciseInputField,
            boolean
        >);
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBe(true);
        expect(fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled')).not.toBeNull();
    });

    it('should display disabled semi-automatic assessment toggle if not in exam mode, not importing, and due date is unavailable', () => {
        exercise.dueDate = undefined;
        createTestComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled');

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBe(true);
        expect(component.isSemiAutomaticAssessmentToggleEnabled()).toBe(false);
        expect(checkbox).not.toBeNull();
        expect(checkbox.disabled).toBe(true);
    });

    it('should show semi-automatic assessment due date picker if semi-automatic assessment is enabled for a course exercise without feedback requests', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.allowFeedbackRequests = false;
        createTestComponent();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBe(true);
        expect(component.isDatePickerForSemiAutomaticAssessmentDueDateVisible()).toBe(true);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.assessmentDueDate')).toBe(true);
    });

    it('should not show semi-automatic assessment due date picker in exam mode', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        createTestComponent();
        fixture.componentRef.setInput('isExamMode', true);
        fixture.detectChanges();

        expect(component.isSemiAutomaticAssessmentToggleVisible()).toBe(true);
        expect(component.isDatePickerForSemiAutomaticAssessmentDueDateVisible()).toBe(false);
        expect(component.timelineItems().some((item) => item.labelStringKey === 'artemisApp.exercise.assessmentDueDate')).toBe(false);
    });

    it('should clear assessment due date if semi-automatic assessment due date picker becomes unavailable', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.assessmentDueDate = afterDueDate;
        createTestComponent();

        expect(component.isDatePickerForSemiAutomaticAssessmentDueDateVisible()).toBe(true);
        expect(component.assessmentDueDate()).toEqual(afterDueDate);

        fixture.componentRef.setInput('isExamMode', true);
        fixture.detectChanges();

        expect(component.isDatePickerForSemiAutomaticAssessmentDueDateVisible()).toBe(false);
        expect(component.assessmentDueDate()).toBeUndefined();
    });

    it('should clear assessment due date and running tests after due date if feedback requests are allowed', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.allowFeedbackRequests = false;
        exercise.assessmentDueDate = afterDueDate;
        exercise.buildAndTestStudentSubmissionsAfterDueDate = afterDueDate;
        createTestComponent();

        expect(component.assessmentDueDate()).toEqual(afterDueDate);
        expect(component.buildAndTestStudentSubmissionsAfterDueDate()).toEqual(afterDueDate);

        component.allowFeedbackRequests.set(true);
        fixture.detectChanges();

        expect(component.assessmentDueDate()).toBeUndefined();
        expect(component.buildAndTestStudentSubmissionsAfterDueDate()).toBeUndefined();
    });

    it('should update dependent fields when assessment type changes', () => {
        exercise.assessmentType = undefined;
        createTestComponent();

        component.allowComplaintsForAutomaticAssessments.set(true);
        component.allowFeedbackRequests.set(true);
        component.assessmentType.set(AssessmentType.SEMI_AUTOMATIC);
        fixture.detectChanges();

        expect(component.assessmentType()).toBe(AssessmentType.SEMI_AUTOMATIC);
        expect(component.allowComplaintsForAutomaticAssessments()).toBe(false);
        expect(component.allowFeedbackRequests()).toBe(false);

        component.assessmentDueDate.set(afterDueDate);
        component.allowComplaintsForAutomaticAssessments.set(true);
        component.feedbackSuggestionModule.set('programming_module');
        fixture.detectChanges();

        component.assessmentType.set(AssessmentType.AUTOMATIC);
        fixture.detectChanges();

        expect(component.assessmentType()).toBe(AssessmentType.AUTOMATIC);
        expect(component.assessmentDueDate()).toBeUndefined();
        expect(component.allowComplaintsForAutomaticAssessments()).toBe(false);
        expect(component.feedbackSuggestionModule()).toBeUndefined();
    });

    it('should update form validation status when timeline status changes', () => {
        createTestComponent();
        const formValidChangesSpy = vi.fn();
        component.formValidChanges.subscribe(formValidChangesSpy);

        component.handleTimelineStatusChange({ valid: true, empty: false });

        expect(component.formValid).toBe(true);
        expect(component.formEmpty).toBe(false);
        expect(formValidChangesSpy).toHaveBeenCalledWith(true);

        component.handleTimelineStatusChange({ valid: false, empty: true });

        expect(component.formValid).toBe(false);
        expect(component.formEmpty).toBe(true);
        expect(formValidChangesSpy).toHaveBeenLastCalledWith(false);
    });

    it('should initialize as valid and not empty if no timeline is rendered', () => {
        exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
        exercise.exampleSolutionPublicationDate = undefined;
        fixture = TestBed.createComponent(ProgrammingExerciseUpdateTimelineComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('complaintsInCourseEnabled', true);
        fixture.componentRef.setInput('exampleSolutionPublicationDateSet', false);
        fixture.componentRef.setInput('dueDate', exercise.dueDate);
        fixture.componentRef.setInput('buildAndTestStudentSubmissionsAfterDueDate', exercise.buildAndTestStudentSubmissionsAfterDueDate);
        fixture.componentRef.setInput('assessmentDueDate', exercise.assessmentDueDate);
        fixture.componentRef.setInput('exampleSolutionPublicationDate', exercise.exampleSolutionPublicationDate);
        fixture.componentRef.setInput('assessmentType', exercise.assessmentType);
        fixture.componentRef.setInput('allowFeedbackRequests', exercise.allowFeedbackRequests);
        fixture.componentRef.setInput('isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord', {
            releaseDate: false,
            startDate: false,
            dueDate: false,
            runTestsAfterDueDate: false,
            assessmentDueDate: false,
            exampleSolutionPublicationDate: false,
        } as Record<ProgrammingExerciseInputField, boolean>);
        fixture.detectChanges();

        expect(component.timelineItems()).toHaveLength(0);
        expect(fixture.debugElement.nativeElement.querySelector('jhi-exercise-timeline')).toBeNull();
        expect(component.formValid).toBe(true);
        expect(component.formEmpty).toBe(false);
    });

    it('should change the value for allowing complaints for exercise with automatic assessment', () => {
        exercise.allowComplaintsForAutomaticAssessments = false;
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        createTestComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment');

        expect(component.allowComplaintsForAutomaticAssessments()).toBe(false);
        expect(checkbox.checked).toBe(false);

        checkbox.click();
        fixture.detectChanges();

        expect(component.allowComplaintsForAutomaticAssessments()).toBe(true);
    });

    it('should change feedback request allowed', () => {
        exercise.allowFeedbackRequests = false;
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        createTestComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowFeedbackRequests');

        expect(component.allowFeedbackRequests()).toBe(false);
        expect(checkbox.checked).toBe(false);

        checkbox.click();
        fixture.detectChanges();

        expect(component.allowFeedbackRequests()).toBe(true);
    });

    it('should change assessment type from automatic to semi-automatic', () => {
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        createTestComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled');

        expect(component.assessmentType()).toBe(AssessmentType.AUTOMATIC);
        expect(checkbox.checked).toBe(false);

        checkbox.click();
        fixture.detectChanges();

        expect(component.assessmentType()).toBe(AssessmentType.SEMI_AUTOMATIC);
    });

    it('should change assessment type from semi-automatic to automatic', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.assessmentDueDate = afterDueDate;
        createTestComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled');

        expect(component.assessmentType()).toBe(AssessmentType.SEMI_AUTOMATIC);
        expect(checkbox.checked).toBe(true);

        checkbox.click();
        fixture.detectChanges();

        expect(component.assessmentType()).toBe(AssessmentType.AUTOMATIC);
        expect(component.assessmentDueDate()).toBeUndefined();
    });

    it('should disable feedback suggestions when changing the assessment type to automatic', () => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.feedbackSuggestionModule = 'programming_module';
        createTestComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#manualAssessmentEnabled');

        checkbox.click();
        fixture.detectChanges();

        expect(component.assessmentType()).toBe(AssessmentType.AUTOMATIC);
        expect(component.feedbackSuggestionModule()).toBeUndefined();
    });

    it('should enable release tests with example solution', () => {
        exercise.exampleSolutionPublicationDate = dayjs();
        exercise.releaseTestsWithExampleSolution = false;
        createTestComponent();

        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution');

        expect(component.releaseTestsWithExampleSolution()).toBe(false);
        expect(checkbox.checked).toBe(false);

        checkbox.click();
        fixture.detectChanges();

        expect(component.releaseTestsWithExampleSolution()).toBe(true);
    });

    it('should enable checkbox for complaints on automatic assessments for exam exercises with automatic assessment', () => {
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.dueDate = undefined;
        createTestComponent();
        fixture.componentRef.setInput('isExamMode', true);
        fixture.detectChanges();

        expect(fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment').disabled).toBe(false);
    });

    it.each([true, false])('should disable checkbox for complaints on automatic assessments for exercises without automatic assessment', async (examMode: boolean) => {
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        if (!examMode) {
            exercise.course = new Course();
            exercise.course.complaintsEnabled = true;
        }
        createTestComponent();
        fixture.componentRef.setInput('isExamMode', examMode);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment').disabled).toBe(true);
    });

    it('should disable checkbox for complaints on automatic assessments for course exercises with automatic assessment but without due date', async () => {
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.dueDate = undefined;
        exercise.course = new Course();
        exercise.course.complaintsEnabled = true;
        createTestComponent();

        fixture.componentRef.setInput('isExamMode', false);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.debugElement.nativeElement.querySelector('#allowComplaintsForAutomaticAssessment').disabled).toBe(true);
    });

    it.each([true, false])('should enable checkbox to include tests in example solution', (examMode: boolean) => {
        if (!examMode) {
            exercise.course = new Course();
            exercise.course.complaintsEnabled = true;
        } else {
            exercise.exampleSolutionPublicationDate = undefined;
            exercise.exerciseGroup = { exam: { exampleSolutionPublicationDate } };
        }
        createTestComponent();
        fixture.componentRef.setInput('isExamMode', examMode);
        fixture.detectChanges();

        expect(fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution').disabled).toBe(false);
    });

    it('should disable checkbox to include tests in example solution without example solution publication date', async () => {
        exercise.exampleSolutionPublicationDate = undefined;
        createTestComponent();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.debugElement.nativeElement.querySelector('#releaseTestsWithExampleSolution').disabled).toBe(true);
    });
});
