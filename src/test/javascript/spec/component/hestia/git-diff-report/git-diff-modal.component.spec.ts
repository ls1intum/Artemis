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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [GitDiffReportModalComponent, MockPipe(ArtemisTranslatePipe), MockComponent(GitDiffReportComponent)],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportModalComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        modal = TestBed.inject(NgbActiveModal);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call correct service method onInit', () => {
        const loadTemplateFilesSpy = jest.spyOn(programmingExerciseService, 'getTemplateRepositoryTestFilesWithContent').mockReturnValue(of(new Map<string, string>()));
        const loadSolutionFilesSpy = jest.spyOn(programmingExerciseService, 'getSolutionRepositoryTestFilesWithContent').mockReturnValue(of(new Map<string, string>()));
        const loadParticipationFilesSpy = jest
            .spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommit')
            .mockReturnValue(of(new Map<string, string>()));
        // case 1: diff for template and solution
        comp.report = { programmingExercise: { id: 1 }, participationIdForSecondCommit: 3, secondCommitHash: 'abc' } as ProgrammingExerciseGitDiffReport;
        comp.diffForTemplateAndSolution = true;
        comp.ngOnInit();
        expect(loadTemplateFilesSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(loadSolutionFilesSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(loadParticipationFilesSpy).not.toHaveBeenCalled();
        expect(comp.firstCommitFileContentByPath).toBeDefined();
        expect(comp.secondCommitFileContentByPath).toBeDefined();

        jest.resetAllMocks();

        // case 2: diff for submission with template
        comp.diffForTemplateAndSolution = false;
        comp.ngOnInit();
        expect(loadParticipationFilesSpy).toHaveBeenCalledExactlyOnceWith(3, 'abc');
        expect(loadTemplateFilesSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(loadSolutionFilesSpy).not.toHaveBeenCalled();
        expect(comp.firstCommitFileContentByPath).toBeDefined();
        expect(comp.secondCommitFileContentByPath).toBeDefined();

        jest.resetAllMocks();

        // case 3: diff for two submissions
        comp.report = { ...comp.report, participationIdForFirstCommit: 2, firstCommitHash: 'def' } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(loadParticipationFilesSpy).toHaveBeenCalledTimes(2);
        expect(loadParticipationFilesSpy).toHaveBeenCalledWith(2, 'def');
        expect(loadParticipationFilesSpy).toHaveBeenCalledWith(3, 'abc');
        expect(comp.firstCommitFileContentByPath).toBeDefined();
        expect(comp.secondCommitFileContentByPath).toBeDefined();
    });

    it('should set error flag if loading files fails', () => {
        jest.spyOn(programmingExerciseService, 'getTemplateRepositoryTestFilesWithContent').mockReturnValue(throwError('error'));
        jest.spyOn(programmingExerciseService, 'getSolutionRepositoryTestFilesWithContent').mockReturnValue(throwError('error'));
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommit').mockReturnValue(throwError('error'));
        comp.report = { programmingExercise: { id: 1 }, participationIdForSecondCommit: 3, secondCommitHash: 'abc' } as ProgrammingExerciseGitDiffReport;
        comp.diffForTemplateAndSolution = true;
        comp.ngOnInit();
        expect(comp.errorWhileFetchingRepos).toBeTrue();

        //reset value
        comp.errorWhileFetchingRepos = false;
        comp.diffForTemplateAndSolution = false;
        comp.ngOnInit();
        expect(comp.errorWhileFetchingRepos).toBeTrue();

        //reset value
        comp.errorWhileFetchingRepos = false;
        comp.report = { ...comp.report, participationIdForFirstCommit: 2, firstCommitHash: 'def' } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(comp.errorWhileFetchingRepos).toBeTrue();
    });

    it('should call modal service when close() is invoked', () => {
        const modalServiceSpy = jest.spyOn(modal, 'dismiss');
        comp.close();
        expect(modalServiceSpy).toHaveBeenCalled();
    });
});
