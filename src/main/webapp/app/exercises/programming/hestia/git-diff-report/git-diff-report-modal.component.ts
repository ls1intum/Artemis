import { ChangeDetectionStrategy, Component, effect, inject, output, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { Observable } from 'rxjs';
import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LineStat } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { isEqual } from 'lodash-es';
import { RepositoryFilesService } from 'app/exercises/programming/manage/services/repository-files.service';

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffReportComponent, TranslateDirective],
})
export class GitDiffReportModalComponent {
    static readonly WINDOW_CLASS = 'diff-view-modal';

    private readonly activeModal = inject(NgbActiveModal);
    private readonly repositoryFilesService = inject(RepositoryFilesService);

    public readonly report = signal<ProgrammingExerciseGitDiffReport | undefined>(undefined, { equal: isEqual });
    public readonly diffForTemplateAndSolution = signal<boolean>(true);

    public readonly lineStatChanged = output<LineStat>();

    protected readonly errorWhileFetchingRepos = signal<boolean>(false);
    protected readonly leftCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);
    protected readonly rightCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);

    constructor() {
        effect(
            (onCleanup) => {
                this.leftCommitFileContentByPath.set(undefined);
                this.rightCommitFileContentByPath.set(undefined);
                this.errorWhileFetchingRepos.set(false);

                const report = this.report();
                if (!report) {
                    return;
                }

                const subscription = this.loadFiles(report).subscribe({
                    next: ([leftFileContentByPath, rightFileContentByPath]) => {
                        this.leftCommitFileContentByPath.set(leftFileContentByPath);
                        this.rightCommitFileContentByPath.set(rightFileContentByPath);
                    },
                    error: () => {
                        this.errorWhileFetchingRepos.set(true);
                    },
                });

                onCleanup(() => subscription.unsubscribe());
            },
            { allowSignalWrites: true },
        );
    }

    private loadFiles(report: ProgrammingExerciseGitDiffReport): Observable<[Map<string, string>, Map<string, string>]> {
        if (this.diffForTemplateAndSolution()) {
            return this.repositoryFilesService.loadFilesForTemplateAndSolution(report);
        } else {
            return this.repositoryFilesService.loadRepositoryFilesForParticipations(report);
        }
    }

    public close(): void {
        this.activeModal.dismiss();
    }
}
