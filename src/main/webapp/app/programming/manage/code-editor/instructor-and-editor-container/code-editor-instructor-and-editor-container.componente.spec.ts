import { AlertService } from 'app/shared/service/alert.service';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { BehaviorSubject, of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import {
    ProgrammingExerciseSynchronizationMessage,
    ProgrammingExerciseSynchronizationService,
    ProgrammingExerciseSynchronizationTarget,
} from 'app/programming/manage/services/programming-exercise-synchronization.service';

describe('CodeEditorInstructorAndEditorContainerComponent', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let artemisIntelligenceService: ArtemisIntelligenceService;
    let consistencyCheckService: ConsistencyCheckService;
    let alertService: AlertService;
    let browserFingerprintService: BrowserFingerprintService;

    const course = { id: 123, exercises: [] } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 123;
    const error1 = new ConsistencyCheckError();
    error1.programmingExercise = programmingExercise;
    error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;
    const synchronizationServiceMock = {
        getSynchronizationUpdates: jest.fn().mockReturnValue(of()),
    } as unknown as ProgrammingExerciseSynchronizationService;

    beforeEach(waitForAsync(async () => {
        await TestBed.configureTestingModule({
            providers: [
                MockProvider(ArtemisIntelligenceService),
                MockProvider(ConsistencyCheckService),
                MockProvider(ProfileService),
                MockProvider(AlertService),
                MockProvider(ParticipationService),
                MockProvider(ProgrammingExerciseService),
                MockProvider(CourseExerciseService),
                { provide: ProgrammingExerciseSynchronizationService, useValue: synchronizationServiceMock },
                {
                    provide: BrowserFingerprintService,
                    useValue: { instanceIdentifier: new BehaviorSubject<string | undefined>(undefined) },
                },
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            imports: [CodeEditorInstructorAndEditorContainerComponent],
            declarations: [],
        }).compileComponents();
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        artemisIntelligenceService = TestBed.inject(ArtemisIntelligenceService);
        consistencyCheckService = TestBed.inject(ConsistencyCheckService);
        alertService = TestBed.inject(AlertService);
        browserFingerprintService = TestBed.inject(BrowserFingerprintService);
    }));

    afterEach(() => {
        jest.restoreAllMocks();
        synchronizationServiceMock.getSynchronizationUpdates.mockClear();
    });

    it('runs full consistency check and shows success when no issues', () => {
        const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
        const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
        const successSpy = jest.spyOn(alertService, 'success');

        comp.checkConsistencies(programmingExercise);
        expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(123);
        expect(artemisIntelligenceService.consistencyCheck).toHaveBeenCalledWith(123);

        expect(check1Spy).toHaveBeenCalledOnce();
        expect(check2Spy).toHaveBeenCalledOnce();
        expect(successSpy).toHaveBeenCalledOnce();
    });

    it('error when first consistency check fails', () => {
        const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
        const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
        const failSpy = jest.spyOn(alertService, 'error');

        comp.checkConsistencies(programmingExercise);
        expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(123);

        expect(check1Spy).toHaveBeenCalledOnce();
        expect(check2Spy).not.toHaveBeenCalled();
        expect(failSpy).toHaveBeenCalledOnce();
    });

    it('error when exercise id undefined', () => {
        const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
        const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
        const failSpy = jest.spyOn(alertService, 'error');

        comp.checkConsistencies({ id: undefined } as any);

        expect(check1Spy).not.toHaveBeenCalled();
        expect(check2Spy).not.toHaveBeenCalled();
        expect(failSpy).toHaveBeenCalledOnce();
    });

    it('check isLoading propagates correctly', () => {
        (artemisIntelligenceService as any).isLoading = () => true;
        expect(comp.isCheckingConsistency()).toBeTrue();

        (artemisIntelligenceService as any).isLoading = () => false;
        expect(comp.isCheckingConsistency()).toBeFalse();
    });

    it('ignores synchronization updates from same browser instance', () => {
        const alertSpy = jest.spyOn(alertService, 'addAlert');
        browserFingerprintService.instanceIdentifier.next('client-1');

        (comp as any).handleSynchronizationUpdate({
            target: ProgrammingExerciseSynchronizationTarget.PROBLEM_STATEMENT,
            clientInstanceId: 'client-1',
        } as ProgrammingExerciseSynchronizationMessage);

        expect(alertSpy).not.toHaveBeenCalled();
    });

    it('shows warning when relevant repository changes in another session', () => {
        const alertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.TEMPLATE;

        (comp as any).handleSynchronizationUpdate({
            target: ProgrammingExerciseSynchronizationTarget.TEMPLATE_REPOSITORY,
            clientInstanceId: 'other-client',
        } as ProgrammingExerciseSynchronizationMessage);

        expect(alertSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                message: 'artemisApp.editor.synchronization.repositoryChanged',
            }),
        );
    });

    it('shows problem statement warning when change is relevant', () => {
        const alertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.TEMPLATE;

        (comp as any).handleSynchronizationUpdate({
            target: ProgrammingExerciseSynchronizationTarget.PROBLEM_STATEMENT,
            clientInstanceId: 'another-client',
        } as ProgrammingExerciseSynchronizationMessage);

        expect(alertSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                message: 'artemisApp.editor.synchronization.problemStatementChanged',
            }),
        );
    });
});
