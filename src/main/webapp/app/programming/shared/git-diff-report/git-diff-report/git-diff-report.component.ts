import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, computed, effect, inject, input, signal, viewChildren } from '@angular/core';
import { faAngleDown, faAngleUp, faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent, ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/buttons/button/button.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';

import { GitDiffFilePanelTitleComponent } from 'app/programming/shared/git-diff-report/git-diff-file-panel-title/git-diff-file-panel-title.component';
import { GitDiffFileComponent } from 'app/programming/shared/git-diff-report/git-diff-file/git-diff-file.component';
import { captureException } from '@sentry/angular';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbAccordionModule, NgbCollapse, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';

@Component({
    selector: 'jhi-git-diff-report',
    templateUrl: './git-diff-report.component.html',
    styleUrls: ['./git-diff-report.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        GitDiffLineStatComponent,
        GitDiffFilePanelTitleComponent,
        GitDiffFileComponent,
        ArtemisTranslatePipe,
        FontAwesomeModule,
        NgbTooltipModule,
        ButtonComponent,
        NgbAccordionModule,
        NgbCollapse,
    ],
})
export class GitDiffReportComponent implements AfterViewInit, OnDestroy {
    protected readonly faSpinner = faSpinner;
    protected readonly faTableColumns = faTableColumns;
    protected readonly faAngleUp = faAngleUp;
    protected readonly faAngleDown = faAngleDown;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly TooltipPlacement = TooltipPlacement;

    readonly repositoryDiffInformation = input.required<RepositoryDiffInformation>();
    readonly diffForTemplateAndSolution = input<boolean>(true);
    readonly diffForTemplateAndEmptyRepository = input<boolean>(false);
    readonly isRepositoryView = input<boolean>(false);

    readonly nothingToDisplay = computed(() => this.repositoryDiffInformation().diffInformations.length === 0);
    readonly leftCommitHash = input<string>();
    readonly rightCommitHash = input<string>();
    readonly participationId = input<number>();
    readonly allDiffsReady = signal<boolean>(false);
    readonly initialDiffsReady = signal<boolean>(false); // Track if initial diffs are ready to show container
    readonly allowSplitView = signal<boolean>(true);
    private readonly initialLoadCount = 5;
    readonly loadedTitles = signal<Set<string>>(new Set());
    /**
     * Per-title overrides for the collapsed state, populated when the user toggles a panel manually.
     * Stored as a signal so template bindings stay reactive without mutating the input data.
     */
    readonly userCollapsedOverrides = signal<ReadonlyMap<string, boolean>>(new Map());
    /**
     * Resolves the visible collapsed state per title: user override (if any) takes precedence,
     * otherwise the panel is collapsed iff the file has not been loaded yet.
     */
    readonly collapsedTitles = computed<ReadonlySet<string>>(() => {
        const loaded = this.loadedTitles();
        const overrides = this.userCollapsedOverrides();
        const titles = this.repositoryDiffInformation().diffInformations.map((diff) => diff.title);
        const collapsed = new Set<string>();
        for (const title of titles) {
            const override = overrides.get(title);
            const isCollapsed = override !== undefined ? override : !loaded.has(title);
            if (isCollapsed) {
                collapsed.add(title);
            }
        }
        return collapsed;
    });
    private lastDiffInformation?: RepositoryDiffInformation;
    private intersectionObserver?: IntersectionObserver;
    private lazyObserverInitTimeoutId?: number;

    readonly leftCommit = computed(() => this.leftCommitHash()?.substring(0, 10));
    readonly rightCommit = computed(() => this.rightCommitHash()?.substring(0, 10));
    readonly addedLineCount = computed(() => this.repositoryDiffInformation().totalLineChange.addedLineCount);
    readonly removedLineCount = computed(() => this.repositoryDiffInformation().totalLineChange.removedLineCount);

    readonly diffPanelContainers = viewChildren<ElementRef<HTMLElement>>('diffPanelContainer');

    private readonly hostElementRef = inject<ElementRef<HTMLElement>>(ElementRef);

    constructor() {
        effect(() => {
            const info = this.repositoryDiffInformation();
            if (info === this.lastDiffInformation) {
                return;
            }
            this.lastDiffInformation = info;

            const current = this.loadedTitles();
            const updated = new Set<string>();
            info.diffInformations.forEach((diff, index) => {
                if (index < this.initialLoadCount || current.has(diff.title)) {
                    updated.add(diff.title);
                }
            });
            this.loadedTitles.set(updated);
            this.updateAllDiffsReady();
            this.observeDiffPanels();
        });

        // Re-observe diff panels whenever the queried set of panel containers changes
        // (e.g. when the diff information input changes and new panels are rendered).
        effect(() => {
            this.diffPanelContainers();
            this.observeDiffPanels();
        });
    }

    ngAfterViewInit(): void {
        // Delay observer setup by 1 second to let initial 5 files load faster
        this.lazyObserverInitTimeoutId = window.setTimeout(() => {
            const scrollRoot = this.findScrollRoot();
            this.intersectionObserver = new IntersectionObserver(
                (entries) => {
                    for (const entry of entries) {
                        const title = (entry.target as HTMLElement).dataset['title'];

                        if (entry.isIntersecting) {
                            if (title) {
                                this.markContentAsLoaded(title);
                            }
                            this.intersectionObserver?.unobserve(entry.target);
                        }
                    }
                },
                {
                    // Load content when element is within 400px of the scroll root bottom (eg. git-diff-modal)
                    root: scrollRoot ?? undefined,
                    rootMargin: '0px 0px 400px 0px',
                    threshold: 0,
                },
            );
            this.observeDiffPanels();
            this.lazyObserverInitTimeoutId = undefined;
        }, 1000);
    }

    ngOnDestroy(): void {
        this.intersectionObserver?.disconnect();
        if (this.lazyObserverInitTimeoutId !== undefined) {
            clearTimeout(this.lazyObserverInitTimeoutId);
            this.lazyObserverInitTimeoutId = undefined;
        }
    }

    /**
     * Records that the diff editor for a file has changed its "ready" state.
     * If all paths have reported that they are ready, {@link allDiffsReady} will be set to true.
     * @param title The path of the file whose diff this event refers to.
     * @param ready Whether the diff is ready to be displayed or not.
     */
    onDiffReady(title: string, ready: boolean) {
        const diffInformation = this.repositoryDiffInformation().diffInformations;
        const index = diffInformation.findIndex((info) => info.title === title);

        if (index !== -1) {
            diffInformation[index].diffReady = ready;
        } else {
            captureException(`Received diff ready event for unknown title: ${title}`);
        }

        this.updateAllDiffsReady();
    }

    /**
     * Handles the diff ready event from a GitDiffFileComponent
     * @param title The title of the file whose diff this event refers to
     * @param ready Whether the diff is ready to be displayed or not
     */
    handleDiffReady(title: string, ready: boolean): void {
        this.onDiffReady(title, ready);
    }

    markContentAsLoaded(title: string) {
        const current = this.loadedTitles();
        if (current.has(title)) {
            return;
        }

        const updated = new Set(current);
        updated.add(title);
        this.loadedTitles.set(updated);

        this.updateAllDiffsReady();
    }

    private updateAllDiffsReady() {
        const loaded = this.loadedTitles();
        const diffInformation = this.repositoryDiffInformation().diffInformations;

        // Check if all LOADED diffs are ready (don't consider unloaded ones)
        const loadedDiffs = diffInformation.filter((info) => loaded.has(info.title));
        const allLoadedReady = loadedDiffs.length > 0 && loadedDiffs.every((info) => info.diffReady);
        this.allDiffsReady.set(allLoadedReady);

        // Set initialDiffsReady once the first batch is ready (never set back to false)
        if (allLoadedReady && !this.initialDiffsReady()) {
            this.initialDiffsReady.set(true);
        }
    }

    private observeDiffPanels() {
        if (!this.intersectionObserver) {
            return;
        }

        this.diffPanelContainers().forEach((panel) => {
            const element = panel.nativeElement;
            if (!element.dataset['title']) {
                return;
            }
            this.intersectionObserver!.observe(element);
        });
    }

    onToggleClick(title: string, wasCollapsed: boolean) {
        // NgB will flip it after this click, so remember the new state.
        const newCollapsedState = !wasCollapsed;
        const overrides = new Map(this.userCollapsedOverrides());
        overrides.set(title, newCollapsedState);
        this.userCollapsedOverrides.set(overrides);

        // If expanding (wasCollapsed = true), load content before expansion to prevent scroll jumps
        if (wasCollapsed) {
            this.markContentAsLoaded(title);
        }
    }

    private findScrollRoot(): HTMLElement | null {
        const hostElement = this.hostElementRef.nativeElement;
        return (hostElement.closest('.modal-body') as HTMLElement | null) ?? null;
    }
}
