import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { ArtemisTranslatePipe } from '../../../../../main/webapp/app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { of, throwError } from 'rxjs';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ProgrammingExerciseGitDiffReport } from 'app/programming/shared/entities/programming-exercise-git-diff-report.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';

describe('GitDiffReportModalComponent', () => {
    let comp: GitDiffReportModalComponent;
    let fixture: ComponentFixture<GitDiffReportModalComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let modal: NgbActiveModal;
    let loadTemplateFilesSpy: jest.SpyInstance;
    let loadParticipationFilesSpy: jest.SpyInstance;
    let loadSolutionFilesSpy: jest.SpyInstance;
    let filesWithContentTemplate: Map<string, string>;
    let filesWithContentParticipation1: Map<string, string>;
    let filesWithContentParticipation2: Map<string, string>;
    let filesWithContentSolution: Map<string, string>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(GitDiffReportComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(NgbActiveModal),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportModalComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);

        modal = TestBed.inject(NgbActiveModal);

        filesWithContentTemplate = new Map<string, string>();
        filesWithContentTemplate.set('test', 'test');
        filesWithContentParticipation1 = new Map<string, string>();
        filesWithContentParticipation1.set('test3', 'test3');
        filesWithContentParticipation2 = new Map<string, string>();
        filesWithContentParticipation2.set('test4', 'test4');
        filesWithContentSolution = new Map<string, string>();
        filesWithContentSolution.set('test2', 'test2');

        loadSolutionFilesSpy = jest.spyOn(programmingExerciseService, 'getSolutionRepositoryTestFilesWithContent').mockReturnValue(of(filesWithContentSolution));
        loadTemplateFilesSpy = jest.spyOn(programmingExerciseService, 'getTemplateRepositoryTestFilesWithContent').mockReturnValue(of(filesWithContentTemplate));
        loadParticipationFilesSpy = jest
            .spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommit')
            .mockReturnValue(of(filesWithContentParticipation1));

        modal = TestBed.inject(NgbActiveModal);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const finishEffects = async () => {
        // We need to wait for all asynchronous code to finish execution before we can expect the results.
        // See https://stackoverflow.com/questions/44741102/how-to-make-jest-wait-for-all-asynchronous-code-to-finish-execution-before-expec/51045733#51045733
        await new Promise(process.nextTick);
        await fixture.whenStable();
    };

    it('should call correct service method onInit', async () => {
        // case 1: diff for template and solution
        fixture.componentRef.setInput('report', { programmingExercise: { id: 1 }, participationIdForRightCommit: 3, rightCommitHash: 'abc' } as ProgrammingExerciseGitDiffReport);
        fixture.componentRef.setInput('diffForTemplateAndSolution', true);
        fixture.detectChanges();
        await finishEffects();
        expect(loadTemplateFilesSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(loadSolutionFilesSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(loadParticipationFilesSpy).not.toHaveBeenCalled();
        expect(comp.leftCommitFileContentByPath()).toEqual(filesWithContentTemplate);
        expect(comp.rightCommitFileContentByPath()).toEqual(filesWithContentSolution);
    });

    it('should retrieve files for template and submission if diffForTemplateAndSolution is false and firstParticipationId is undefined', async () => {
        fixture.componentRef.setInput('report', { programmingExercise: { id: 1 }, participationIdForRightCommit: 3, rightCommitHash: 'abc' } as ProgrammingExerciseGitDiffReport);
        // case 2: diff for submission with template
        fixture.componentRef.setInput('diffForTemplateAndSolution', false);
        fixture.detectChanges();
        await finishEffects();
        expect(loadParticipationFilesSpy).toHaveBeenCalledExactlyOnceWith(3, 'abc');
        expect(loadTemplateFilesSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(comp.leftCommitFileContentByPath()).toEqual(filesWithContentTemplate);
        expect(comp.rightCommitFileContentByPath()).toEqual(filesWithContentParticipation1);
    });

    it('should retrieve files for both submissions if diffForTemplateAndSolution is false and firstParticipationId is defined', async () => {
        // case 3: diff for two submissions
        fixture.componentRef.setInput('report', {
            programmingExercise: { id: 1 },
            participationIdForRightCommit: 3,
            rightCommitHash: 'abc',
            participationIdForLeftCommit: 2,
            leftCommitHash: 'def',
        } as ProgrammingExerciseGitDiffReport);
        fixture.componentRef.setInput('diffForTemplateAndSolution', false);
        fixture.detectChanges();
        await finishEffects();
        expect(loadParticipationFilesSpy).toHaveBeenCalledTimes(2);
        expect(loadParticipationFilesSpy).toHaveBeenCalledWith(2, 'def');
        expect(loadParticipationFilesSpy).toHaveBeenCalledWith(3, 'abc');
        expect(comp.leftCommitFileContentByPath()).toEqual(filesWithContentParticipation1);
        expect(comp.rightCommitFileContentByPath()).toEqual(filesWithContentParticipation1);
    });

    /**
     * For some reason, this test needs to be executed before the next one.
     * Somehow, the mocked error-response leaks into this test, despite properly resetting the mocks.
     * Also, other tests seem to not be affected by this.
     */
    it('should load files from cache if available for template and participation repo', async () => {
        const cachedRepositoryFiles = new Map<string, Map<string, string>>();
        cachedRepositoryFiles.set('1-template', filesWithContentTemplate);
        cachedRepositoryFiles.set('def', filesWithContentParticipation1);
        fixture.componentRef.setInput('cachedRepositoryFiles', cachedRepositoryFiles);
        fixture.componentRef.setInput('report', { programmingExercise: { id: 1 }, participationIdForRightCommit: 3, rightCommitHash: 'def' } as ProgrammingExerciseGitDiffReport);
        fixture.componentRef.setInput('diffForTemplateAndSolution', false);
        fixture.detectChanges();
        await finishEffects();
        expect(loadParticipationFilesSpy).not.toHaveBeenCalled();
        expect(loadTemplateFilesSpy).not.toHaveBeenCalled();
        expect(comp.leftCommitFileContentByPath()).toEqual(filesWithContentTemplate);
        expect(comp.rightCommitFileContentByPath()).toEqual(filesWithContentParticipation1);
    });

    it('should set error flag if loading files fails', async () => {
        jest.spyOn(programmingExerciseService, 'getTemplateRepositoryTestFilesWithContent').mockReturnValue(throwError('error1'));
        jest.spyOn(programmingExerciseService, 'getSolutionRepositoryTestFilesWithContent').mockReturnValue(throwError('error'));
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommit').mockReturnValue(throwError('error'));
        fixture.componentRef.setInput('report', { programmingExercise: { id: 1 }, participationIdForRightCommit: 3, rightCommitHash: 'abc' } as ProgrammingExerciseGitDiffReport);
        fixture.componentRef.setInput('diffForTemplateAndSolution', true);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(comp.errorWhileFetchingRepos()).toBeTrue();

        //reset value
        comp.errorWhileFetchingRepos.set(false);
        fixture.componentRef.setInput('diffForTemplateAndSolution', false);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(comp.errorWhileFetchingRepos()).toBeTrue();

        //reset value
        comp.errorWhileFetchingRepos.set(false);
        fixture.componentRef.setInput('report', { ...comp.report, participationIdForLeftCommit: 2, leftCommitHash: 'def' } as unknown as ProgrammingExerciseGitDiffReport);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(comp.errorWhileFetchingRepos()).toBeTrue();
    });

    it('should call modal service when close() is invoked', () => {
        const modalServiceSpy = jest.spyOn(modal, 'dismiss');
        comp.close();
        expect(modalServiceSpy).toHaveBeenCalled();
    });

    it('should load files from cache if available for participation repo at both commits', async () => {
        const cachedRepositoryFiles = new Map<string, Map<string, string>>();
        cachedRepositoryFiles.set('def', filesWithContentParticipation1);
        cachedRepositoryFiles.set('abc', filesWithContentParticipation2);
        fixture.componentRef.setInput('cachedRepositoryFiles', cachedRepositoryFiles);
        fixture.componentRef.setInput('report', {
            programmingExercise: { id: 1 },
            participationIdForRightCommit: 3,
            rightCommitHash: 'abc',
            participationIdForLeftCommit: 2,
            leftCommitHash: 'def',
        } as ProgrammingExerciseGitDiffReport);
        fixture.componentRef.setInput('diffForTemplateAndSolution', false);
        fixture.detectChanges();
        await finishEffects();
        expect(loadParticipationFilesSpy).not.toHaveBeenCalled();
        expect(comp.leftCommitFileContentByPath()).toEqual(filesWithContentParticipation1);
        expect(comp.rightCommitFileContentByPath()).toEqual(filesWithContentParticipation2);
    });
});
