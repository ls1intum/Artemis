import { ApplicationRef, ChangeDetectionStrategy, Component, ComponentRef, OnDestroy, computed, createComponent, effect, inject, input, signal, untracked } from '@angular/core';
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
import { combineLatest, switchMap } from 'rxjs';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';

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
    private readonly participationWebsocketService = inject(ParticipationWebsocketService);
    private readonly applicationRef = inject(ApplicationRef);

    protected readonly detail = input.required<ProgrammingDiffReportDetail>();

    private readonly exerciseId = computed(() => this.detail().data.exerciseId);
    private readonly templateParticipationId = computed(() => this.detail().data.templateParticipationId);
    private readonly solutionParticipationId = computed(() => this.detail().data.solutionParticipationId);

    protected readonly leftCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);
    protected readonly rightCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);
    private readonly modalRef = signal<NgbModalRef | undefined>(undefined);
    private readonly diffComponent = signal<ComponentRef<GitDiffReportComponent> | undefined>(undefined);

    protected readonly lineStat = signal<LineStat | undefined>(undefined);

    private readonly fileContentsLoaded = computed(() => this.leftCommitFileContentByPath() !== undefined && this.rightCommitFileContentByPath() !== undefined);

    constructor() {
        effect((onCleanup) => {
            const exerciseId = this.exerciseId();
            const templateParticipationId = this.templateParticipationId();
            const solutionParticipationId = this.solutionParticipationId();

            const subscription = combineLatest([
                this.participationWebsocketService.subscribeForLatestResultOfParticipation(templateParticipationId, false, exerciseId),
                this.participationWebsocketService.subscribeForLatestResultOfParticipation(solutionParticipationId, false, exerciseId),
            ])
                .pipe(switchMap(() => this.repositoryFilesService.loadFilesForTemplateAndSolution(exerciseId)))
                .subscribe(([leftFileContentByPath, rightFileContentByPath]) => {
                    this.leftCommitFileContentByPath.set(leftFileContentByPath);
                    this.rightCommitFileContentByPath.set(rightFileContentByPath);
                });

            onCleanup(() => subscription.unsubscribe());
        });

        effect(
            (onCleanup) => {
                if (!this.fileContentsLoaded()) {
                    return;
                }

                const diffComponent = createComponent(GitDiffReportComponent, { environmentInjector: this.applicationRef.injector });
                untracked(() => {
                    diffComponent.setInput('templateFileContentByPath', this.leftCommitFileContentByPath()!);
                    diffComponent.setInput('solutionFileContentByPath', this.rightCommitFileContentByPath()!);
                    diffComponent.changeDetectorRef.detectChanges();
                });

                const subscription = diffComponent.instance.lineStatChanged.subscribe((lineStat) => this.lineStat.set(lineStat));
                this.diffComponent.set(diffComponent);

                onCleanup(() => {
                    this.diffComponent.set(undefined);
                    subscription.unsubscribe();
                    diffComponent.destroy();
                });
            },
            { allowSignalWrites: true },
        );

        effect((onCleanup) => {
            const modalRef = this.modalRef();
            if (!modalRef) {
                return;
            }

            const diffComponent = this.diffComponent();
            if (!diffComponent) {
                return;
            }

            const modalComponent: GitDiffReportModalComponent = modalRef.componentInstance;
            const diffView = diffComponent.hostView;

            modalComponent.insertView(diffView);

            onCleanup(() => {
                modalComponent.detachView(diffView);
            });
        });

        effect(() => {
            const diffComponent = this.diffComponent();
            if (!diffComponent) {
                return;
            }

            const templateFiles = this.leftCommitFileContentByPath();
            const solutionFiles = this.rightCommitFileContentByPath();
            if (!templateFiles || !solutionFiles) {
                return;
            }

            diffComponent.setInput('templateFileContentByPath', templateFiles);
            diffComponent.setInput('solutionFileContentByPath', solutionFiles);
            diffComponent.changeDetectorRef.detectChanges();
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
