import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, input, output } from '@angular/core';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { GitDiffFilePanelTitleComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel-title.component';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { GitDiffFileComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-git-diff-file-panel',
    templateUrl: './git-diff-file-panel.component.html',
    styleUrls: ['./git-diff-file-panel.component.scss'],
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffFilePanelTitleComponent, GitDiffLineStatComponent, GitDiffFileComponent, ArtemisSharedModule],
})
export class GitDiffFilePanelComponent {
    diffEntries = input.required<ProgrammingExerciseGitDiffEntry[]>();
    originalFileContent = input<string>();
    modifiedFileContent = input<string>();
    diffForTemplateAndSolution = input<boolean>(true);
    allowSplitView = input<boolean>(true);
    onDiffReady = output<boolean>();

    originalFilePath = computed(() =>
        this.diffEntries()
            .map((entry) => entry.previousFilePath)
            .filter((filePath) => filePath)
            .first(),
    );
    modifiedFilePath = computed(() =>
        this.diffEntries()
            .map((entry) => entry.filePath)
            .filter((filePath) => filePath)
            .first(),
    );

    addedLineCount = computed(
        () =>
            this.diffEntries()
                .filter((entry) => entry && entry.filePath && entry.startLine && entry.lineCount)
                .flatMap((entry) => {
                    return this.modifiedFileContent()
                        ?.split('\n')
                        .slice(entry.startLine! - 1, entry.startLine! + entry.lineCount! - 1);
                })
                .filter((line) => line && line.trim().length !== 0).length,
    );

    removedLineCount = computed(
        () =>
            this.diffEntries()
                .filter((entry) => entry && entry.previousFilePath && entry.previousStartLine && entry.previousLineCount)
                .flatMap((entry) => {
                    return this.originalFileContent()
                        ?.split('\n')
                        .slice(entry.previousStartLine! - 1, entry.previousStartLine! + entry.previousLineCount! - 1);
                })
                .filter((line) => line && line.trim().length !== 0).length,
    );

    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;
}
