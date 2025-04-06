import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ProgrammingExerciseExamDiffComponent } from 'app/exam/manage/student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';
import { CommitsInfoComponent } from 'app/programming/shared/commits-info/commits-info.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { of } from 'rxjs';
import { ProgrammingExerciseGitDiffReport } from 'app/programming/shared/entities/programming-exercise-git-diff-report.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';
import { ProgrammingExerciseGitDiffEntry } from 'app/programming/shared/entities/programming-exercise-git-diff-entry.model';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { CachedRepositoryFilesService } from 'app/programming/manage/services/cached-repository-files.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { input } from '@angular/core';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

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
            declarations: [ProgrammingExerciseExamDiffComponent, MockComponent(CommitsInfoComponent), MockPipe(ArtemisTranslatePipe), MockComponent(IncludedInScoreBadgeComponent)],
            providers: [
                { provide: NgbModal, useValue: new MockNgbModalService() },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseExamDiffComponent);
        component = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        modal = TestBed.inject(NgbModal);
        cachedRepositoryFilesService = TestBed.inject(CachedRepositoryFilesService);
        const exercise = { id: 3, title: 'prog' } as ProgrammingExercise;
        const studentParticipation = {} as StudentParticipation;
        component.exercise.set(exercise);
        component.studentParticipation.set(studentParticipation);
        fixture.detectChanges();
    });

    it('should call getDiffReportForSubmissions when loading diff report if previous submission is defined', () => {
        const diffForSubmissionsSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissions').mockReturnValue(of(report));
        const diffForSubmissionWithTemplateSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissionWithTemplate').mockReturnValue(
            of({
                id: 2,
            } as ProgrammingExerciseGitDiffReport),
        );
        const previousSubmission = { id: 1 };
        const currentSubmission = { id: 2 };
        component.previousSubmission.update(() => previousSubmission);
        component.currentSubmission.update(() => currentSubmission);
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
        component.previousSubmission.update(() => undefined);
        const currentSubmission = { id: 2 };
        component.currentSubmission.update(() => currentSubmission);
        const exercise = { id: 3 } as ProgrammingExercise;
        component.exercise.update(() => exercise);
        component.loadGitDiffReport();
        expect(diffForSubmissionWithTemplateSpy).toHaveBeenCalledOnce();
        expect(diffForSubmissionsSpy).not.toHaveBeenCalled();
    });

    it('should open the modal when showGitDiff is called', () => {
        const modalServiceSpy = jest.spyOn(modal, 'open');
        const exercise = { id: 1 } as ProgrammingExercise;
        component.exercise.update(() => exercise);
        component.showGitDiff();
        expect(modalServiceSpy).toHaveBeenCalledExactlyOnceWith(GitDiffReportModalComponent, { windowClass: GitDiffReportModalComponent.WINDOW_CLASS });
    });

    it('should use reports from cache if available', fakeAsync(() => {
        const diffForSubmissionsSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissions');
        const diffForSubmissionWithTemplateSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissionWithTemplate');
        const previousSubmission = { id: 1 };
        component.previousSubmission.update(() => previousSubmission);
        const currentSubmission = { id: 2 };
        component.currentSubmission.update(() => currentSubmission);
        const exercise = { id: 3 } as ProgrammingExercise;
        component.exercise.update(() => exercise);
        const cachedDiffReports = new Map<string, ProgrammingExerciseGitDiffReport>();
        const expectedReport = {
            id: 1,
            leftCommitHash: 'abc',
            rightCommitHash: 'def',
            participationIdForLeftCommit: 1,
            participationIdForRightCommit: 2,
            programmingExercise: component.exercise,
        } as unknown as ProgrammingExerciseGitDiffReport;
        cachedDiffReports.set(JSON.stringify([1, 2]), expectedReport);
        TestBed.runInInjectionContext(() => {
            component.cachedDiffReports = input(cachedDiffReports);
        });
        component.ngOnInit();
        component.exerciseIdSubject.update((subject) => {
            subject.next(1);
            return subject;
        });
        // tick 200 is needed because the observable uses debounceTime(200)
        tick(200);
        expect(component.exercise().gitDiffReport).toEqual(expectedReport);
        expect(diffForSubmissionWithTemplateSpy).not.toHaveBeenCalled();
        expect(diffForSubmissionsSpy).not.toHaveBeenCalled();
    }));

    it('should load report if not in cache', fakeAsync(() => {
        const diffForSubmissionsSpy = jest.spyOn(programmingExerciseService, 'getDiffReportForSubmissions').mockReturnValue(of(report));
        const previousSubmission = { id: 1 };
        component.previousSubmission.update(() => previousSubmission);
        const currentSubmission = { id: 2 };
        component.currentSubmission.update(() => currentSubmission);
        const exercise = { id: 3 } as ProgrammingExercise;
        component.exercise.update(() => exercise);
        const cachedDiffReports = new Map<string, ProgrammingExerciseGitDiffReport>();
        TestBed.runInInjectionContext(() => {
            component.cachedDiffReports = input(cachedDiffReports);
        });
        component.ngOnInit();
        component.exerciseIdSubject.update((subject) => {
            subject.next(1);
            return subject;
        });
        // tick 200 is needed because the observable uses debounceTime(200)
        tick(200);
        expect(component.exercise().gitDiffReport).toEqual(report);
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
