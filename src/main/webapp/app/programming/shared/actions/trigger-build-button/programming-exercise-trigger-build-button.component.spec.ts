import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import dayjs from 'dayjs/esm';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { Subject, of } from 'rxjs';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming/shared/services/programming-submission.service';
import { triggerChanges } from 'test/helpers/utils/general-test.utils';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('TriggerBuildButtonSpec', () => {
    let comp: ProgrammingExerciseStudentTriggerBuildButtonComponent;
    let fixture: ComponentFixture<ProgrammingExerciseStudentTriggerBuildButtonComponent>;
    let debugElement: DebugElement;
    let submissionService: ProgrammingSubmissionService;

    let getLatestPendingSubmissionSubject: Subject<ProgrammingSubmissionStateObj>;

    let triggerBuildSpy: jest.SpyInstance;
    let triggerFailedBuildSpy: jest.SpyInstance;

    const exercise = { id: 20 } as Exercise;
    const student = { id: 99 };
    const gradedResult1 = { id: 10, rated: true, completionDate: dayjs('2019-06-06T22:15:29.203+02:00') } as Result;
    const gradedResult2 = { id: 11, rated: true, completionDate: dayjs('2019-06-06T22:17:29.203+02:00') } as Result;
    const ungradedResult1 = { id: 12, rated: false, completionDate: dayjs('2019-06-06T22:25:29.203+02:00') } as Result;
    const ungradedResult2 = { id: 13, rated: false, completionDate: dayjs('2019-06-06T22:32:29.203+02:00') } as Result;
    const results = [gradedResult2, ungradedResult1, gradedResult1, ungradedResult2] as Result[];
    const participation = { id: 1, exercise, submissions: [{ results }], student } as any;

    const submission = { id: 1 } as any;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            providers: [
                JhiLanguageHelper,
                ChangeDetectorRef,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                submissionService = TestBed.inject(ProgrammingSubmissionService);

                getLatestPendingSubmissionSubject = new Subject<ProgrammingSubmissionStateObj>();
                jest.spyOn(submissionService, 'getLatestPendingSubmissionByParticipationId').mockReturnValue(getLatestPendingSubmissionSubject);

                triggerBuildSpy = jest.spyOn(submissionService, 'triggerBuild').mockReturnValue(of());
                triggerFailedBuildSpy = jest.spyOn(submissionService, 'triggerFailedBuild').mockReturnValue(of());
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const getTriggerButton = () => {
        const triggerButton = debugElement.query(By.css('button'));
        return triggerButton ? triggerButton.nativeElement : null;
    };

    it('should not show the trigger button if there is no pending submission and no build is running', () => {
        comp.participation = { ...participation, results: [gradedResult1], initializationState: InitializationState.INITIALIZED };
        comp.exercise = { id: 4 } as ProgrammingExercise;

        triggerChanges(comp, { property: 'participation', currentValue: comp.participation });
        fixture.detectChanges();

        // Button should not show if there is no failed submission.
        let triggerButton = getTriggerButton();
        expect(triggerButton).toBeNull();

        // After a failed submission is sent, the button should be displayed.
        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION,
            submission: undefined,
            participationId: comp.participation.id!,
        });

        fixture.detectChanges();
        triggerButton = getTriggerButton();
        expect(triggerButton).toBeDefined();
    });

    it('should be enabled and trigger the build on click if it is provided with a participation including results', () => {
        comp.participation = { ...participation, results: [gradedResult1], initializationState: InitializationState.INITIALIZED };
        comp.exercise = { id: 5 } as ProgrammingExercise;

        triggerChanges(comp, { property: 'participation', currentValue: comp.participation });
        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION,
            submission: undefined,
            participationId: comp.participation.id!,
        });
        fixture.detectChanges();

        let triggerButton = getTriggerButton();
        expect(triggerButton.disabled).toBeFalse();

        // Click the button to start a build.
        triggerButton.click();
        expect(triggerFailedBuildSpy).toHaveBeenCalledOnce();
        expect(triggerBuildSpy).not.toHaveBeenCalled();

        // After some time the created submission comes through the websocket, button is disabled until the build is done.
        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
            submission,
            participationId: comp.participation.id!,
        });
        expect(comp.isBuilding).toBeTrue();
        fixture.detectChanges();
        expect(triggerButton.disabled).toBeTrue();

        // Now the server signals that the build is done, the button should now be removed.
        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            submission: undefined,
            participationId: comp.participation.id!,
        });
        expect(comp.isBuilding).toBeFalse();
        fixture.detectChanges();
        triggerButton = getTriggerButton();
        expect(triggerButton).toBeNull();
    });

    it('should set the latest result correctly to manual', () => {
        gradedResult1.assessmentType = AssessmentType.AUTOMATIC;
        gradedResult2.assessmentType = AssessmentType.MANUAL;

        comp.participation = { ...participation, results: [gradedResult1, gradedResult2], initializationState: InitializationState.INITIALIZED };
        comp.exercise = { id: 5, dueDate: dayjs().subtract(1, 'day') } as ProgrammingExercise;

        triggerChanges(comp, { property: 'participation', currentValue: comp.participation });
        fixture.detectChanges();

        expect(comp.lastResultIsManual).toBeTrue();
    });
});
