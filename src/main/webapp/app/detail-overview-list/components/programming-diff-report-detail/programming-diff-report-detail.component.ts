import { ChangeDetectionStrategy, Component, OnDestroy, ViewContainerRef, effect, inject, input, signal, viewChild } from '@angular/core';
import type { ProgrammingDiffReportDetail } from 'app/detail-overview-list/detail.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/button.component';
import { faCodeCompare } from '@fortawesome/free-solid-svg-icons';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffLineStatComponent, LineStat } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';
import { RepositoryFilesService } from 'app/exercises/programming/manage/services/repository-files.service';

@Component({
    selector: 'jhi-programming-diff-report-detail',
    templateUrl: 'programming-diff-report-detail.component.html',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, GitDiffLineStatComponent],
})
export class ProgrammingDiffReportDetailComponent implements OnDestroy {
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly TooltipPlacement = TooltipPlacement;
    protected readonly WARNING = ButtonType.WARNING;

    protected readonly faCodeCompare = faCodeCompare;

    private readonly modalService = inject(NgbModal);
    private readonly repositoryFilesService = inject(RepositoryFilesService);
    private readonly modalRef = signal<NgbModalRef | undefined>(undefined);

    protected readonly detail = input.required<ProgrammingDiffReportDetail>();

    protected readonly leftCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);
    protected readonly rightCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);
    protected readonly container = viewChild.required('container', { read: ViewContainerRef });

    protected readonly lineStat = signal<LineStat | undefined>(undefined);

    constructor() {
        effect(
            (onCleanup) => {
                this.leftCommitFileContentByPath.set(undefined);
                this.rightCommitFileContentByPath.set(undefined);

                const exerciseId = this.detail().data.exerciseId;

                const subscription = this.repositoryFilesService.loadFilesForTemplateAndSolution(exerciseId).subscribe(([leftFileContentByPath, rightFileContentByPath]) => {
                    this.leftCommitFileContentByPath.set(leftFileContentByPath);
                    this.rightCommitFileContentByPath.set(rightFileContentByPath);
                });

                onCleanup(() => subscription.unsubscribe());
            },
            { allowSignalWrites: true },
        );

        effect((onCleanup) => {
            const templateFiles = this.leftCommitFileContentByPath();
            if (!templateFiles) {
                return;
            }
            const solutionFiles = this.rightCommitFileContentByPath();
            if (!solutionFiles) {
                return;
            }

            const container = this.container();
            const component = container.createComponent(GitDiffReportComponent);
            component.setInput('templateFileContentByPath', templateFiles);
            component.setInput('solutionFileContentByPath', solutionFiles);
            const subscription = component.instance.lineStatChanged.subscribe((lineStat) => this.lineStat.set(lineStat));

            onCleanup(() => {
                subscription.unsubscribe();
                container.remove(container.indexOf(component.hostView));
            });
        });

        effect((onCleanup) => {
            const modalRef = this.modalRef();
            if (!modalRef) {
                return;
            }

            const hiddenContainer = this.container();
            const view = hiddenContainer.detach();
            if (!view) {
                throw new Error('could not detach view');
            }

            const modalComponent: GitDiffReportModalComponent = modalRef.componentInstance;
            const modalContainer = modalComponent.container();
            modalContainer.insert(view);

            onCleanup(() => {
                modalContainer.detach(modalContainer.indexOf(view));
                hiddenContainer.insert(view);
            });
        });
    }

    ngOnDestroy() {
        this.modalRef()?.close();
    }

    showGitDiff() {
        const modalRef = this.modalService.open(GitDiffReportModalComponent, {
            windowClass: GitDiffReportModalComponent.WINDOW_CLASS,
            beforeDismiss: () => {
                this.modalRef.set(undefined);
                return true;
            },
        });
        this.modalRef.set(modalRef);
    }
}
