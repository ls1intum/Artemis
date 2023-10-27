import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ProgrammingExerciseExamDiffComponent } from 'app/exam/manage/student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';
import { ArtemisTestModule } from '../../../test.module';
import { CommitsInfoComponent } from 'app/exercises/programming/shared/commits-info/commits-info.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { of } from 'rxjs';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { CachedRepositoryFilesService } from 'app/exercises/programming/manage/services/cached-repository-files.service';

describe('ProgrammingExerciseExamDiffComponent', () => {
    let component: ProgrammingExerciseExamDiffComponent;
    let fixture: ComponentFixture<ProgrammingExerciseExamDiffComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let cachedRepositoryFilesService: CachedRepositoryFilesService;
    let modal: NgbModal;
    const report = {
        id: 1,
        entries: [
            {
                previousFilePath: 'abc',
                previousStartLine: 1,
                previousLineCount: 1,
                lineCount: 3,
                startLine: 2,
                filePath: 'abc',
                id: 1,
            } as ProgrammingExerciseGitDiffEntry,
        ],
    } as ProgrammingExerciseGitDiffReport;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseExamDiffComponent, MockComponent(CommitsInfoComponent), MockPipe(ArtemisTranslatePipe), MockComponent(IncludedInScoreBadgeComponent)],
            providers: [{ provide: NgbModal, useValue: new MockNgbModalService() }],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseExamDiffComponent);
        component = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        modal = TestBed.inject(NgbModal);
        cachedRepositoryFilesService = TestBed.inject(CachedRepositoryFilesService);
        component.exercise = { id: 3, title: 'prog' } as ProgrammingExercise;
        fixture.detectChanges();
    });

    it('should call getDiffReportForSubmissions when loading diff report if previous submission is defined', () => {
        const diffForSubmissionsSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissions').mockReturnValue(of(report));
        const diffForSubmissionWithTemplateSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissionWithTemplate').mockReturnValue(
            of({
                id: 2,
            } as ProgrammingExerciseGitDiffReport),
        );
        component.previousSubmission = { id: 1 };
        component.currentSubmission = { id: 2 };
        component.loadGitDiffReport();
        expect(diffForSubmissionsSpy).toHaveBeenCalledExactlyOnceWith(3, 1, 2);
        expect(diffForSubmissionWithTemplateSpy).not.toHaveBeenCalled();
        expect(component.addedLineCount).toBe(3);
        expect(component.removedLineCount).toBe(1);
    });

    it('should call getDiffReportForSubmissionWithTemplate when loading diff report if previous submission is undefined', () => {
        const diffForSubmissionsSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissions').mockReturnValue(of({ id: 1 } as ProgrammingExerciseGitDiffReport));
        const diffForSubmissionWithTemplateSpy = jest
            .spyOn(programmingExerciseService, 'getDiffReportForSubmissionWithTemplate')
            .mockReturnValue(of({ id: 2 } as ProgrammingExerciseGitDiffReport));
        component.previousSubmission = undefined;
        component.currentSubmission = { id: 2 };
        component.exercise = { id: 3 } as ProgrammingExercise;
        component.loadGitDiffReport();
        expect(diffForSubmissionWithTemplateSpy).toHaveBeenCalledOnce();
        expect(diffForSubmissionsSpy).not.toHaveBeenCalled();
    });

    it('should open the modal when showGitDiff is called', () => {
        const modalServiceSpy = jest.spyOn(modal, 'open');
        component.exercise = { id: 1 } as ProgrammingExercise;
        component.showGitDiff();
        expect(modalServiceSpy).toHaveBeenCalledExactlyOnceWith(GitDiffReportModalComponent, { size: 'xl' });
    });

    it('should use reports from cache if available', fakeAsync(() => {
        const diffForSubmissionsSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissions');
        const diffForSubmissionWithTemplateSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissionWithTemplate');
        component.previousSubmission = { id: 1 };
        component.currentSubmission = { id: 2 };
        component.exercise = { id: 3 } as ProgrammingExercise;
        const cachedDiffReports = new Map<string, ProgrammingExerciseGitDiffReport>();
        const expectedReport = {
            id: 1,
            leftCommitHash: 'abc',
            rightCommitHash: 'def',
            participationIdForLeftCommit: 1,
            participationIdForRightCommit: 2,
            programmingExercise: component.exercise,
        } as ProgrammingExerciseGitDiffReport;
        cachedDiffReports.set(JSON.stringify([1, 2]), expectedReport);
        component.cachedDiffReports = cachedDiffReports;
        component.ngOnInit();
        component.exerciseIdSubject.next(1);
        // tick 200 is needed because the observable uses debounceTime(200)
        tick(200);
        expect(component.exercise.gitDiffReport).toEqual(expectedReport);
        expect(diffForSubmissionWithTemplateSpy).not.toHaveBeenCalled();
        expect(diffForSubmissionsSpy).not.toHaveBeenCalled();
    }));

    it('should load report if not in cache', fakeAsync(() => {
        const diffForSubmissionsSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissions').mockReturnValue(of(report));
        component.previousSubmission = { id: 1 };
        component.currentSubmission = { id: 2 };
        component.exercise = { id: 3 } as ProgrammingExercise;
        component.cachedDiffReports = new Map<string, ProgrammingExerciseGitDiffReport>();
        component.ngOnInit();
        component.exerciseIdSubject.next(1);
        // tick 200 is needed because the observable uses debounceTime(200)
        tick(200);
        expect(component.exercise.gitDiffReport).toEqual(report);
        expect(diffForSubmissionsSpy).toHaveBeenCalledExactlyOnceWith(3, 1, 2);
    }));

    it('should subscribe to CachedRepositoryFilesChange event', () => {
        const cachedFiles = new Map<string, Map<string, string>>();
        cachedFiles.set('abc', new Map<string, string>());
        const cachedRepositoryFilesServiceSpy = jest.spyOn(cachedRepositoryFilesService, 'getCachedRepositoryFilesObservable').mockReturnValue(of(cachedFiles));
        component.showGitDiff();
        expect(cachedRepositoryFilesServiceSpy).toHaveBeenCalled();
        expect(component.cachedRepositoryFiles).toEqual(cachedFiles);
    });
});
