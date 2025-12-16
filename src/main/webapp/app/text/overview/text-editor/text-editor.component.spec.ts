import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { input } from '@angular/core';
import dayjs from 'dayjs/esm';
import { ActivatedRoute, RouterModule, convertToParamMap } from '@angular/router';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTextEditorService } from 'test/helpers/mocks/service/mock-text-editor.service';
import { TextEditorService } from 'app/text/overview/service/text-editor.service';
import { BehaviorSubject, of } from 'rxjs';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TextResultComponent } from 'app/text/overview/text-result/text-result.component';
import { SubmissionResultStatusComponent } from 'app/core/course/overview/submission-result-status/submission-result-status.component';
import { TextEditorComponent } from 'app/text/overview/text-editor/text-editor.component';
import { textEditorRoute } from 'app/text/overview/text-editor.route';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ComplaintsFormComponent } from 'app/assessment/overview/complaint-form/complaints-form.component';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { MockTextSubmissionService } from 'test/helpers/mocks/service/mock-text-submission.service';
import { Language } from 'app/core/course/shared/entities/course.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TeamParticipateInfoBoxComponent } from 'app/exercise/team/team-participate/team-participate-info-box.component';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { By } from '@angular/platform-browser';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('TextEditorComponent', () => {
    let comp: TextEditorComponent;
    let fixture: ComponentFixture<TextEditorComponent>;
    let textService: TextEditorService;
    let textSubmissionService: TextSubmissionService;
    let getTextForParticipationStub: jest.SpyInstance;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;

    const route = { snapshot: { paramMap: convertToParamMap({ participationId: 42 }) } } as ActivatedRoute;
    const textExercise = { id: 1 } as TextExercise;
    const participation = new StudentParticipation();
    const result = new Result();

    beforeAll(() => {
        participation.id = 42;
        participation.exercise = textExercise;
        participation.submissions = [new TextSubmission()];
        result.id = 1;
    });

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([textEditorRoute[0]]), FaIconComponent],
            declarations: [
                TextEditorComponent,
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ButtonComponent),
                MockComponent(TextResultComponent),
                MockComponent(ComplaintsFormComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(HeaderParticipationPageComponent),
                MockComponent(ResizeableContainerComponent),
                MockComponent(TeamParticipateInfoBoxComponent),
                MockComponent(TeamSubmissionSyncComponent),
                MockComponent(AdditionalFeedbackComponent),
                MockComponent(RatingComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                AlertService,
                { provide: ActivatedRoute, useValue: route },
                { provide: TextEditorService, useClass: MockTextEditorService },
                LocalStorageService,
                SessionStorageService,
                { provide: TextSubmissionService, useClass: MockTextSubmissionService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(IrisSettingsService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextEditorComponent);
                comp = fixture.componentInstance;
                textService = TestBed.inject(TextEditorService);
                textSubmissionService = TestBed.inject(TextSubmissionService);
                profileService = TestBed.inject(ProfileService);
                irisSettingsService = TestBed.inject(IrisSettingsService);
                getTextForParticipationStub = jest.spyOn(textService, 'get');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should use inputValues if present instead of loading new details', fakeAsync(() => {
        TestBed.runInInjectionContext(() => {
            comp.inputExercise = input<TextExercise>(textExercise);
            comp.inputParticipation = input<StudentParticipation>(participation);
            comp.inputSubmission = input<TextSubmission>({ id: 1, text: 'test' });
        });
        // @ts-ignore updateParticipation is private
        const updateParticipationSpy = jest.spyOn(comp, 'updateParticipation');
        // @ts-ignore setupComponentWithInputValuesSpy is private
        const setupComponentWithInputValuesSpy = jest.spyOn(comp, 'setupComponentWithInputValues');

        fixture.detectChanges();

        expect(getTextForParticipationStub).not.toHaveBeenCalled();
        expect(updateParticipationSpy).not.toHaveBeenCalled();
        expect(setupComponentWithInputValuesSpy).toHaveBeenCalled();
        expect(comp.answer).toBeDefined();
    }));

    it('should not allow to submit after the due date if there is no due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDueDate).toBeFalsy();
        expect(comp.isAlwaysActive).toBeTruthy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit after the due date if the initialization date is before the due date', fakeAsync(() => {
        participation.initializationDate = dayjs();
        textExercise.dueDate = dayjs().add(1, 'days');
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDueDate).toBeFalsy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should allow to submit after the due date if the initialization date is after the due date', fakeAsync(() => {
        participation.initializationDate = dayjs().add(1, 'days');
        textExercise.dueDate = dayjs();
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDueDate).toBeTruthy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not be always active if there is a result and no due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.result = result;
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAlwaysActive).toBeFalsy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should be always active if there is no result and the initialization date is after the due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;
        comp.textExercise.dueDate = dayjs();
        participation.initializationDate = dayjs().add(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isAlwaysActive).toBeTruthy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should get inactive as soon as the due date passes the current date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        textExercise.dueDate = dayjs().add(1, 'days');
        participation.initializationDate = dayjs();

        fixture.detectChanges();
        tick();

        expect(comp.isActive).toBeTruthy();

        comp.textExercise.dueDate = dayjs().subtract(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isActive).toBeFalsy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not submit while saving', () => {
        comp.isSaving = true;
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });

    it('should not submit without submission', () => {
        // @ts-ignore
        delete comp.submission;
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });

    it('should submit', () => {
        comp.participation = { id: 1 };
        comp.submission = { id: 1, participation: { id: 1 } as Participation } as TextSubmission;
        comp.textExercise = { id: 1 } as TextExercise;
        comp.answer = 'abc';
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(comp.isSaving).toBeFalsy();
    });

    it('should alert successful on submit if not isAllowedToSubmitAfterDueDate', () => {
        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'success');
        comp.participation = { id: 1 };
        comp.submission = { id: 1, participation: { id: 1 } as Participation } as TextSubmission;
        comp.textExercise = { id: 1 } as TextExercise;
        comp.answer = 'abc';
        comp.isAllowedToSubmitAfterDueDate = false;
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should warn alert on submit if submitDueDateMissedAlert', () => {
        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'warning');
        comp.participation = { id: 1 };
        comp.submission = { id: 1, participation: { id: 1 } as Participation } as TextSubmission;
        comp.textExercise = { id: 1 } as TextExercise;
        comp.answer = 'abc';
        comp.isAllowedToSubmitAfterDueDate = true;
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should return submission for answer', () => {
        jest.spyOn(textService, 'predictLanguage');
        const submissionForAnswer = comp['submissionForAnswer']('abc');
        expect(submissionForAnswer.text).toBe('abc');
        expect(submissionForAnswer.language).toEqual(Language.ENGLISH);
    });

    it('should return unreferenced feedback', () => {
        comp.result = {
            id: 1,
            feedbacks: [
                {
                    id: 1,
                    reference: undefined,
                    type: FeedbackType.MANUAL_UNREFERENCED,
                } as Feedback,
            ],
        } as Result;
        const unreferencedFeedback = comp.unreferencedFeedback;
        expect(unreferencedFeedback?.length).toBe(1);
    });

    it('should receive submission from team', () => {
        comp.participation = { id: 1, team: { id: 1 } } as StudentParticipation;
        comp.textExercise = {
            id: 1,
            studentParticipations: [] as StudentParticipation[],
        } as TextExercise;
        const submission = {
            id: 1,
            participation: {
                id: 1,
                exercise: { id: 1 } as Exercise,
                submissions: [] as Submission[],
            } as Participation,
            text: 'abc',
        } as TextSubmission;
        // @ts-ignore
        jest.spyOn(comp, 'updateParticipation');
        comp.onReceiveSubmissionFromTeam(submission);
        expect(comp['updateParticipation']).toHaveBeenCalledOnce();
        expect(comp.answer).toBe('abc');
    });

    it('should receive empty submission from team', () => {
        comp.participation = { id: 1, team: { id: 1 } } as StudentParticipation;
        comp.textExercise = {
            id: 1,
            studentParticipations: [] as StudentParticipation[],
        } as TextExercise;
        const submission = {
            id: 1,
            participation: {
                id: 1,
                exercise: { id: 1 } as Exercise,
                submissions: [] as Submission[],
            } as Participation,
        } as TextSubmission;
        // @ts-ignore
        jest.spyOn(comp, 'updateParticipation');
        comp.onReceiveSubmissionFromTeam(submission);
        expect(comp['updateParticipation']).toHaveBeenCalledOnce();
        expect(comp.answer).toBe('');
    });

    it('should set latest submission if submissionId is undefined in updateParticipation', () => {
        const submissionList = [{ id: 1 }, { id: 2 }, { id: 3 }];

        const exGroup = {
            id: 1,
        };
        const textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
            exerciseGroup: exGroup,
        } as TextExercise;
        comp.participation = {
            id: 2,
            submissions: submissionList,
            exercise: textExercise,
        } as StudentParticipation;
        comp['updateParticipation'](comp.participation, undefined);
        expect(comp.submission.id).toEqual(submissionList[submissionList.length - 1].id);
    });

    it('should set the correct submission if updateParticipation is called with submission id', () => {
        const submissionList = [{ id: 1 }, { id: 2 }, { id: 3 }];

        const exGroup = {
            id: 1,
        };
        const textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
            exerciseGroup: exGroup,
        } as TextExercise;
        comp.participation = {
            id: 2,
            submissions: submissionList,
            exercise: textExercise,
        } as StudentParticipation;
        comp['updateParticipation'](comp.participation, 2);
        expect(comp.submission.id).toBe(2);
    });

    it('should set the latest submission if updateParticipation is called with submission id that does not exist', () => {
        const submissionList = [{ id: 1 }, { id: 3 }, { id: 4, results: [{ id: 1, assessmentType: AssessmentType.MANUAL }] }];

        const exGroup = {
            id: 1,
        };
        const textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
            exerciseGroup: exGroup,
            course: { id: 1 },
            assessmentDueDate: dayjs().add(6, 'minutes'),
        } as TextExercise;
        comp.participation = {
            id: 2,
            submissions: submissionList,
            exercise: textExercise,
            results: [{ id: 1 }, { id: 2 }],
        } as StudentParticipation;
        comp['updateParticipation'](comp.participation, 2);
        expect(comp.submission.id).toBe(4);
    });

    it('should not render the submit button when isReadOnlyWithShowResult is true', () => {
        comp.isReadOnlyWithShowResult = true;
        comp.textExercise = textExercise;
        fixture.detectChanges();

        const submitButton = fixture.debugElement.query(By.css('#submit'));
        expect(submitButton).toBeFalsy();
    });

    it('should render the submit button when isReadOnlyWithShowResult is false', () => {
        comp.isOwnerOfParticipation = true;
        comp.isReadOnlyWithShowResult = false;
        comp.isAlwaysActive = true;
        comp.textExercise = textExercise;
        comp.submission = { id: 5, submitted: true };

        fixture.detectChanges();

        const submitButton = fixture.debugElement.query(By.css('#submit'));
        expect(submitButton).toBeTruthy();
    });

    it('should destroy', () => {
        comp.submission = { text: 'abc' } as TextSubmission;
        comp.answer = 'def';
        comp.textExercise = { id: 1 } as TextExercise;
        jest.spyOn(textSubmissionService, 'update');
        comp.ngOnDestroy();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });

    it('should destroy and call submission service when submission id', () => {
        comp.submission = { id: 1, text: 'abc' } as TextSubmission;
        comp.answer = 'def';
        comp.textExercise = { id: 1 } as TextExercise;
        jest.spyOn(textSubmissionService, 'update');
        comp.ngOnDestroy();
        expect(textSubmissionService.update).toHaveBeenCalled();
    });

    it('should load Iris settings when Iris profile is active and not in exam mode', fakeAsync(() => {
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        // Set up course with ID
        comp.course = { id: 123 } as any;

        const mockIrisSettings = {
            courseId: 123,
            settings: {
                enabled: true,
                customInstructions: '',
                variant: 'default',
                rateLimit: { requests: 100, timeframeHours: 24 },
            },
            effectiveRateLimit: { requests: 100, timeframeHours: 24 },
            applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
        } as IrisCourseSettingsWithRateLimitDTO;
        jest.spyOn(irisSettingsService, 'getCourseSettings').mockReturnValue(of(mockIrisSettings));

        route.params = of({ exerciseId: '456' });

        comp.examMode = false;

        comp['loadIrisSettings']();
        tick();

        expect(profileService.isProfileActive).toHaveBeenCalledWith(PROFILE_IRIS);
        expect(irisSettingsService.getCourseSettings).toHaveBeenCalledWith(123);
        expect(comp.irisSettings).toEqual(mockIrisSettings);

        flush();
    }));

    it('should not load Iris settings when in exam mode', fakeAsync(() => {
        const profileInfo = { activeProfiles: [PROFILE_IRIS] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);

        jest.spyOn(irisSettingsService, 'getCourseSettings');

        route.params = of({ exerciseId: '456' });

        comp.examMode = true;

        comp['loadIrisSettings']();
        tick();

        expect(profileService.getProfileInfo).toHaveBeenCalled();
        expect(irisSettingsService.getCourseSettings).not.toHaveBeenCalled();
        expect(comp.irisSettings).toBeUndefined();

        flush();
    }));

    it('should not load Iris settings when Iris profile is not active', fakeAsync(() => {
        const profileInfo = { activeProfiles: ['no-iris'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);

        jest.spyOn(irisSettingsService, 'getCourseSettings');

        route.params = of({ exerciseId: '456' });

        comp.examMode = false;

        comp['loadIrisSettings']();
        tick();

        expect(profileService.getProfileInfo).toHaveBeenCalled();
        expect(irisSettingsService.getCourseSettings).not.toHaveBeenCalled();
        expect(comp.irisSettings).toBeUndefined();

        flush();
    }));
});
