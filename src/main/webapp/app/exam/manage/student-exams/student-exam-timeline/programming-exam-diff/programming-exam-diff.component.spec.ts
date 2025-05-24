import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ProgrammingExerciseExamDiffComponent } from 'app/exam/manage/student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';
import { CommitsInfoComponent } from 'app/programming/shared/commits-info/commits-info.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { CachedRepositoryFilesService } from 'app/programming/manage/services/cached-repository-files.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';

// Mock the diff.utils module to avoid Monaco Editor issues in tests
jest.mock('app/programming/shared/utils/diff.utils', () => ({
    ...jest.requireActual('app/programming/shared/utils/diff.utils'),
    processRepositoryDiff: jest.fn().mockImplementation((templateFiles, solutionFiles) => {
        // Handle the case where files are undefined (when repository fetch fails)
        if (!templateFiles || !solutionFiles) {
            return Promise.resolve(undefined);
        }
        return Promise.resolve({
            diffInformations: [
                {
                    originalFileContent: 'testing line differences',
                    modifiedFileContent: 'testing line diff\nnew line',
                    originalPath: 'Example.java',
                    modifiedPath: 'Example.java',
                    diffReady: true,
                    fileStatus: 'unchanged',
                    lineChange: {
                        addedLineCount: 2,
                        removedLineCount: 1,
                    },
                    title: 'Example.java',
                },
            ],
            totalLineChange: {
                addedLineCount: 2,
                removedLineCount: 1,
            },
        } as RepositoryDiffInformation);
    }),
}));

describe('ProgrammingExerciseExamDiffComponent', () => {
    let component: ProgrammingExerciseExamDiffComponent;
    let fixture: ComponentFixture<ProgrammingExerciseExamDiffComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let cachedRepositoryFilesService: CachedRepositoryFilesService;
    let modal: NgbModal;

    const mockDiffInformation = {
        diffInformations: [
            {
                originalFileContent: 'testing line differences',
                modifiedFileContent: 'testing line diff\nnew line',
                originalPath: 'Example.java',
                modifiedPath: 'Example.java',
                diffReady: false,
                fileStatus: 'unchanged',
                lineChange: {
                    addedLineCount: 2,
                    removedLineCount: 1,
                },
                title: 'Example.java',
            },
        ],
        totalLineChange: {
            addedLineCount: 2,
            removedLineCount: 1,
        },
    } as unknown as RepositoryDiffInformation;

    beforeEach(() => {
        // Mock the ResizeObserver, which is not available in the test environment
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

        TestBed.configureTestingModule({
            declarations: [ProgrammingExerciseExamDiffComponent, MockComponent(CommitsInfoComponent), MockPipe(ArtemisTranslatePipe), MockComponent(IncludedInScoreBadgeComponent)],
            providers: [
                { provide: NgbModal, useValue: new MockNgbModalService() },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseExamDiffComponent);
        component = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        modal = TestBed.inject(NgbModal);
        cachedRepositoryFilesService = TestBed.inject(CachedRepositoryFilesService);
        //cachedRepositoryFilesService.emitCachedRepositoryFiles(new Map<string, Map<string, string>>());
        const exercise = { id: 3, title: 'programming exercise' } as ProgrammingExercise;
        const studentParticipation = {} as StudentParticipation;
        component.exercise.set(exercise);
        component.studentParticipation.set(studentParticipation);
        fixture.detectChanges();
    });

    it('should call getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView when fetching repository if previous submission is defined', fakeAsync(() => {
        const getRepositoryFilesSpy = jest
            .spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView')
            .mockReturnValueOnce(of(new Map([[mockDiffInformation.diffInformations[0].originalPath, mockDiffInformation.diffInformations[0].originalFileContent || '']])))
            .mockReturnValueOnce(of(new Map([[mockDiffInformation.diffInformations[0].modifiedPath, mockDiffInformation.diffInformations[0].modifiedFileContent || '']])));
        const getTemplateRepositorySpy = jest.spyOn(programmingExerciseService, 'getTemplateRepositoryTestFilesWithContent');

        // Spy on the processRepositoryDiff method to ensure it sets the diff information
        const processRepositoryDiffSpy = jest.spyOn(component, 'processRepositoryDiff').mockImplementation(async (left, right) => {
            component.diffInformation.set(mockDiffInformation);
        });

        const previousSubmission = { commitHash: 'abc', participation: { id: 1 } };
        const currentSubmission = { commitHash: 'def', participation: { id: 2 } };
        component.previousSubmission.update(() => previousSubmission);
        component.currentSubmission.update(() => currentSubmission);
        component.fetchRepositoriesAndProcessDiff();

        // Wait for async operations to complete
        tick();

        expect(getRepositoryFilesSpy).toHaveBeenCalledTimes(2);
        expect(getRepositoryFilesSpy).toHaveBeenNthCalledWith(1, 3, 1, 'abc', RepositoryType.USER);
        expect(getRepositoryFilesSpy).toHaveBeenNthCalledWith(2, 3, 2, 'def', RepositoryType.USER);
        expect(getTemplateRepositorySpy).not.toHaveBeenCalled();
        expect(processRepositoryDiffSpy).toHaveBeenCalledOnce();
        expect(component.diffInformation()?.totalLineChange?.addedLineCount).toBe(mockDiffInformation.totalLineChange.addedLineCount);
        expect(component.diffInformation()?.totalLineChange?.removedLineCount).toBe(mockDiffInformation.totalLineChange.removedLineCount);
    }));

    it('should call getTemplateRepositoryTestFilesWithContent when loading diff report if previous submission is undefined', () => {
        const getRepositoryFilesSpy = jest
            .spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView')
            .mockReturnValue(of(new Map<string, string>()));
        const getTemplateRepositorySpy = jest.spyOn(programmingExerciseService, 'getTemplateRepositoryTestFilesWithContent').mockReturnValue(of(new Map<string, string>()));
        component.previousSubmission.update(() => undefined);
        const currentSubmission = { commitHash: 'def', participation: { id: 2 } };
        component.currentSubmission.update(() => currentSubmission);
        component.fetchRepositoriesAndProcessDiff();
        expect(getRepositoryFilesSpy).toHaveBeenCalledOnce();
        expect(getRepositoryFilesSpy).toHaveBeenCalledWith(3, 2, 'def', RepositoryType.USER);
        expect(getTemplateRepositorySpy).toHaveBeenCalledOnce();
        expect(getTemplateRepositorySpy).toHaveBeenCalledWith(3);
    });

    it('should open the modal when showGitDiff is called', () => {
        const modalServiceSpy = jest.spyOn(modal, 'open');
        const exercise = { id: 1 } as ProgrammingExercise;
        component.exercise.update(() => exercise);

        // Set up cached diff information
        const previousSubmission = { id: 1, commitHash: 'abc' };
        const currentSubmission = { id: 2, commitHash: 'def' };
        component.previousSubmission.update(() => previousSubmission);
        component.currentSubmission.update(() => currentSubmission);

        const cachedDiffInfo = new Map<string, any>();
        const key = JSON.stringify([previousSubmission.id, currentSubmission.id]);
        cachedDiffInfo.set(key, { someDiffInfo: 'test' });

        // Directly set the cached diff information instead of using input()
        (component as any).cachedDiffInformation = jest.fn().mockReturnValue(cachedDiffInfo);

        component.fetchRepositoriesAndProcessDiff();
        component.showGitDiff();
        expect(modalServiceSpy).toHaveBeenCalledWith(GitDiffReportModalComponent, { windowClass: GitDiffReportModalComponent.WINDOW_CLASS });
    });

    it('should use diffInformation from cache if available', fakeAsync(() => {
        const getRepositoryFilesSpy = jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView');
        const getTemplateRepositorySpy = jest.spyOn(programmingExerciseService, 'getTemplateRepositoryTestFilesWithContent');
        const previousSubmission = { id: 1, commitHash: 'abc' };
        component.previousSubmission.update(() => previousSubmission);
        const currentSubmission = { id: 2, commitHash: 'def' };
        component.currentSubmission.update(() => currentSubmission);
        const exercise = { id: 3 } as ProgrammingExercise;
        component.exercise.update(() => exercise);
        const cachedDiffInformation = new Map<string, RepositoryDiffInformation>();
        cachedDiffInformation.set(JSON.stringify([1, 2]), mockDiffInformation);

        // Directly set the cached diff information instead of using input()
        (component as any).cachedDiffInformation = jest.fn().mockReturnValue(cachedDiffInformation);

        component.ngOnInit();
        component.exerciseIdSubject.update((subject) => {
            subject.next(1);
            return subject;
        });
        // tick 200 is needed because the observable uses debounceTime(200)
        tick(200);
        expect(component.diffInformation()).toEqual(mockDiffInformation);
        expect(getRepositoryFilesSpy).not.toHaveBeenCalled();
        expect(getTemplateRepositorySpy).not.toHaveBeenCalled();
    }));

    it('should load report if not in cache', fakeAsync(() => {
        const getRepositoryFilesSpy = jest
            .spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView')
            .mockReturnValueOnce(of(new Map([[mockDiffInformation.diffInformations[0].originalPath, mockDiffInformation.diffInformations[0].originalFileContent || '']])))
            .mockReturnValueOnce(of(new Map([[mockDiffInformation.diffInformations[0].modifiedPath, mockDiffInformation.diffInformations[0].modifiedFileContent || '']])));

        // Spy on the processRepositoryDiff method to ensure it sets the diff information
        const processRepositoryDiffSpy = jest.spyOn(component, 'processRepositoryDiff').mockImplementation(async (left, right) => {
            component.diffInformation.set(mockDiffInformation);
        });

        const previousSubmission = { id: 1, commitHash: 'abc', participation: { id: 1 } };
        component.previousSubmission.update(() => previousSubmission);
        const currentSubmission = { id: 2, commitHash: 'def', participation: { id: 2 } };
        component.currentSubmission.update(() => currentSubmission);
        const exercise = { id: 3 } as ProgrammingExercise;
        component.exercise.update(() => exercise);
        const cachedDiffInformation = new Map<string, RepositoryDiffInformation>();

        // Directly set the cached diff information instead of using input()
        (component as any).cachedDiffInformation = jest.fn().mockReturnValue(cachedDiffInformation);

        // Don't call ngOnInit, just test the direct method call
        component.fetchRepositoriesAndProcessDiff();

        // Wait for async operations to complete
        tick();

        expect(component.diffInformation()).toEqual(mockDiffInformation);
        expect(getRepositoryFilesSpy).toHaveBeenCalledTimes(2);
        expect(getRepositoryFilesSpy).toHaveBeenNthCalledWith(1, 3, 1, 'abc', RepositoryType.USER);
        expect(getRepositoryFilesSpy).toHaveBeenNthCalledWith(2, 3, 2, 'def', RepositoryType.USER);
        expect(processRepositoryDiffSpy).toHaveBeenCalledOnce();
    }));

    it('should subscribe to CachedRepositoryFilesChange event', () => {
        const cachedFiles = new Map<string, Map<string, string>>();
        cachedFiles.set('abc', new Map<string, string>());
        const cachedRepositoryFilesServiceSpy = jest.spyOn(cachedRepositoryFilesService, 'getCachedRepositoryFilesObservable').mockReturnValue(of(cachedFiles));
        component.ngOnInit();
        expect(cachedRepositoryFilesServiceSpy).toHaveBeenCalled();
        expect(component.cachedRepositoryFiles).toEqual(cachedFiles);
    });
});
