import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { of, throwError } from 'rxjs';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

describe('GitDiffReportModalComponent', () => {
    let comp: GitDiffReportModalComponent;
    let fixture: ComponentFixture<GitDiffReportModalComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let modal: NgbActiveModal;
    let loadTemplateFilesSpy: jest.SpyInstance;
    let loadParticipationFilesSpy: jest.SpyInstance;
    let loadSolutionFilesSpy: jest.SpyInstance;

    const filesWithContentTemplate = new Map<string, string>();
    filesWithContentTemplate.set('test', 'test');
    const filesWithContentParticipation1 = new Map<string, string>();
    filesWithContentParticipation1.set('test3', 'test3');
    const filesWithContentParticipation2 = new Map<string, string>();
    filesWithContentParticipation2.set('test4', 'test4');
    const filesWithContentSolution = new Map<string, string>();
    filesWithContentSolution.set('test2', 'test2');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [MockPipe(ArtemisTranslatePipe), MockComponent(GitDiffReportComponent)],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportModalComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);

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

    it('should call correct service method onInit', async () => {
        // case 1: diff for template and solution
        fixture.componentRef.setInput('report', { programmingExercise: { id: 1 }, participationIdForRightCommit: 3, rightCommitHash: 'abc' } as ProgrammingExerciseGitDiffReport);
        fixture.componentRef.setInput('diffForTemplateAndSolution', true);
        fixture.detectChanges();
        await fixture.whenStable();
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
        await fixture.whenStable();
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
        await fixture.whenStable();
        expect(loadParticipationFilesSpy).toHaveBeenCalledTimes(2);
        expect(loadParticipationFilesSpy).toHaveBeenCalledWith(2, 'def');
        expect(loadParticipationFilesSpy).toHaveBeenCalledWith(3, 'abc');
        expect(comp.leftCommitFileContentByPath()).toEqual(filesWithContentParticipation1);
        expect(comp.rightCommitFileContentByPath()).toEqual(filesWithContentParticipation1);
    });

    it('should set error flag if loading files fails', async () => {
        jest.spyOn(programmingExerciseService, 'getTemplateRepositoryTestFilesWithContent').mockReturnValue(throwError('error'));
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

    it('should load files from cache if available for template and participation repo', () => {
        const cachedRepositoryFiles = new Map<string, Map<string, string>>();
        cachedRepositoryFiles.set('1-template', filesWithContentTemplate);
        cachedRepositoryFiles.set('def', filesWithContentParticipation1);
        fixture.componentRef.setInput('cachedRepositoryFiles', cachedRepositoryFiles);
        fixture.componentRef.setInput('report', { programmingExercise: { id: 1 }, participationIdForRightCommit: 3, rightCommitHash: 'def' } as ProgrammingExerciseGitDiffReport);
        fixture.componentRef.setInput('diffForTemplateAndSolution', false);
        fixture.detectChanges();
        expect(loadParticipationFilesSpy).not.toHaveBeenCalled();
        expect(loadTemplateFilesSpy).not.toHaveBeenCalled();
        expect(comp.leftCommitFileContentByPath()).toEqual(filesWithContentTemplate);
        expect(comp.rightCommitFileContentByPath()).toEqual(filesWithContentParticipation1);
    });

    it('should load files from cache if available for participation repo at both commits', () => {
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
        expect(loadParticipationFilesSpy).not.toHaveBeenCalled();
        expect(comp.leftCommitFileContentByPath()).toEqual(filesWithContentParticipation1);
        expect(comp.rightCommitFileContentByPath()).toEqual(filesWithContentParticipation2);
    });
});
