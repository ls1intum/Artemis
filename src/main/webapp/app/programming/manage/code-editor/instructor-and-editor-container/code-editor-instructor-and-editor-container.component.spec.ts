import { AlertService } from 'app/shared/service/alert.service';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
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
import {
    ProgrammingExerciseEditorFileChangeType,
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import { FileOperation, RepositoryFileSyncService } from 'app/programming/manage/services/repository-file-sync.service';
import { FileType, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

describe('CodeEditorInstructorAndEditorContainerComponent', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let artemisIntelligenceService: ArtemisIntelligenceService;
    let consistencyCheckService: ConsistencyCheckService;
    let alertService: AlertService;
    let synchronizationService: ProgrammingExerciseEditorSyncService;

    const course = { id: 123, exercises: [] } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 123;
    const error1 = new ConsistencyCheckError();
    error1.programmingExercise = programmingExercise;
    error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;

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
                MockProvider(ProgrammingExerciseEditorSyncService),
                MockProvider(RepositoryFileSyncService, {
                    applyRemoteOperation: jest.fn(),
                    reset: jest.fn(),
                    registerBaseline: jest.fn(),
                    requestFullFile: jest.fn(),
                    handleLocalFileOperation: jest.fn(),
                    init: jest.fn().mockReturnValue(of()),
                } as any),
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
        synchronizationService = TestBed.inject(ProgrammingExerciseEditorSyncService);
        jest.spyOn(synchronizationService, 'subscribeToUpdates').mockReturnValue(of());
    }));

    afterEach(() => {
        jest.restoreAllMocks();
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

    it('shows new commit alert and skips applying remote operations', () => {
        const infoSpy = jest.spyOn(alertService, 'info');
        const repositorySyncService = TestBed.inject(RepositoryFileSyncService) as jest.Mocked<RepositoryFileSyncService>;

        (comp as any).applyRemoteFileOperation({ type: 'NEW_COMMIT_ALERT' } as FileOperation);

        expect(infoSpy).toHaveBeenCalledWith('artemisApp.editor.synchronization.newCommitAlert');
        expect(repositorySyncService.applyRemoteOperation).not.toHaveBeenCalled();
    });

    it('registers baselines and requests file content on load', () => {
        const repositorySyncService = TestBed.inject(RepositoryFileSyncService) as jest.Mocked<RepositoryFileSyncService>;
        comp.selectedRepository = RepositoryType.AUXILIARY;
        comp.selectedRepositoryId = 99;
        comp.codeEditorContainer = { getFileContent: jest.fn().mockReturnValue('content') } as any;

        comp.onFileLoaded('src/Main.java');

        expect(repositorySyncService.registerBaseline).toHaveBeenCalledWith(RepositoryType.AUXILIARY, 'src/Main.java', 'content', 99);
        expect(repositorySyncService.requestFullFile).toHaveBeenCalledWith(RepositoryType.AUXILIARY, 'src/Main.java', 99);
    });

    it('requests full content without baseline when file not available locally', () => {
        const repositorySyncService = TestBed.inject(RepositoryFileSyncService) as jest.Mocked<RepositoryFileSyncService>;
        comp.selectedRepository = RepositoryType.TESTS;
        comp.codeEditorContainer = { getFileContent: jest.fn().mockReturnValue(undefined) } as any;

        comp.onFileLoaded('src/Main.java');

        expect(repositorySyncService.registerBaseline).not.toHaveBeenCalled();
        expect(repositorySyncService.requestFullFile).toHaveBeenCalledWith(RepositoryType.TESTS, 'src/Main.java', undefined);
    });

    it('handles local operations only when exercise exists', () => {
        const repositorySyncService = TestBed.inject(RepositoryFileSyncService) as jest.Mocked<RepositoryFileSyncService>;
        comp.selectedRepository = RepositoryType.TEMPLATE;

        comp.onLocalFileOperationSync({ type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: 'toDelete.java' });
        expect(repositorySyncService.handleLocalFileOperation).not.toHaveBeenCalled();

        comp.exercise = { id: 5 } as ProgrammingExercise;
        comp.selectedRepository = RepositoryType.AUXILIARY;
        comp.selectedRepositoryId = 7;

        comp.onLocalFileOperationSync({ type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: 'new.java', content: '', fileType: FileType.FILE });
        expect(repositorySyncService.handleLocalFileOperation).toHaveBeenCalledWith(
            { type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: 'new.java', content: '', fileType: FileType.FILE },
            RepositoryType.AUXILIARY,
            7,
        );
    });

    it('clears selected file on rename and forwards operation with repository context', () => {
        const repositorySyncService = TestBed.inject(RepositoryFileSyncService) as jest.Mocked<RepositoryFileSyncService>;
        comp.exercise = { id: 99 } as ProgrammingExercise;
        comp.selectedRepository = RepositoryType.TEMPLATE;
        comp.codeEditorContainer = { selectedFile: 'old.java' } as any;

        const operation = {
            type: ProgrammingExerciseEditorFileChangeType.RENAME,
            fileName: 'old.java',
            newFileName: 'new.java',
            content: 'content',
            fileType: FileType.FILE,
        } as const;

        comp.onLocalFileOperationSync(operation);

        expect(comp.codeEditorContainer.selectedFile).toBeUndefined();
        expect(repositorySyncService.handleLocalFileOperation).toHaveBeenCalledWith(operation, RepositoryType.TEMPLATE, undefined);
    });

    it('applies remote operations through sync service', () => {
        const repositorySyncService = TestBed.inject(RepositoryFileSyncService) as jest.Mocked<RepositoryFileSyncService>;
        comp.codeEditorContainer = {} as any;
        const operation = { type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: 'new', content: '', fileType: FileType.FILE } as const;

        (comp as any).applyRemoteFileOperation(operation as any);

        expect(repositorySyncService.applyRemoteOperation).toHaveBeenCalledWith(operation, comp.codeEditorContainer);
    });

    it('applies domain changes for participations and tests', () => {
        const exercise = {
            templateParticipation: { id: 1, programmingExercise: undefined } as any,
            solutionParticipation: { id: 2, programmingExercise: undefined } as any,
            studentParticipations: [{ id: 3, exercise: undefined }] as any,
        } as ProgrammingExercise;
        comp.exercise = exercise;
        comp.codeEditorContainer = { initializeProperties: jest.fn(), selectedRepository: undefined } as any;

        (comp as any).applyDomainChange(DomainType.PARTICIPATION, { id: 2 } as any);
        expect(comp.selectedRepository).toBe(RepositoryType.SOLUTION);
        expect((comp.selectedParticipation as any).programmingExercise).toBe(exercise);

        (comp as any).applyDomainChange(DomainType.TEST_REPOSITORY, exercise);
        expect(comp.selectedRepository).toBe(RepositoryType.TESTS);
    });

    it('reuses loaded exercise when matching id', () => {
        const programmingExerciseService = TestBed.inject(ProgrammingExerciseService) as jest.Mocked<ProgrammingExerciseService>;
        programmingExerciseService.findWithTemplateAndSolutionParticipationAndResults = jest.fn();
        const existingExercise = { id: 55 } as ProgrammingExercise;
        comp.exercise = existingExercise;

        const result$ = comp.loadExercise(55);
        let loaded: ProgrammingExercise | undefined;
        result$.subscribe((exercise) => (loaded = exercise));

        expect(loaded).toBe(existingExercise);
        expect(programmingExerciseService.findWithTemplateAndSolutionParticipationAndResults).not.toHaveBeenCalled();
    });

    it('filters synchronization messages by repository selection', () => {
        comp.selectedRepository = RepositoryType.TEMPLATE;
        expect((comp as any).isChangeRelevant({} as ProgrammingExerciseEditorSyncMessage)).toBeFalse();
        expect((comp as any).isChangeRelevant({ target: ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY } as ProgrammingExerciseEditorSyncMessage)).toBeFalse();
        expect((comp as any).isChangeRelevant({ target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY } as ProgrammingExerciseEditorSyncMessage)).toBeTrue();

        comp.selectedRepository = RepositoryType.AUXILIARY;
        comp.selectedRepositoryId = 3;
        expect(
            (comp as any).isChangeRelevant({
                target: ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY,
                auxiliaryRepositoryId: 99,
            } as ProgrammingExerciseEditorSyncMessage),
        ).toBeFalse();
        expect(
            (comp as any).isChangeRelevant({
                target: ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY,
                auxiliaryRepositoryId: 3,
            } as ProgrammingExerciseEditorSyncMessage),
        ).toBeTrue();
    });
});
