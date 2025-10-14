import {
    AfterViewInit,
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    ElementRef,
    OnDestroy,
    QueryList,
    ViewChildren,
    computed,
    effect,
    inject,
    input,
    signal,
} from '@angular/core';
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
import { Subscription } from 'rxjs';

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
    private readonly loadedTitles = signal<Set<string>>(new Set());
    private lastDiffInformation?: RepositoryDiffInformation;
    private intersectionObserver?: IntersectionObserver;
    private diffPanelsChangesSub?: Subscription;
    private lazyObserverInitTimeoutId?: number;

    readonly leftCommit = computed(() => this.leftCommitHash()?.substring(0, 10));
    readonly rightCommit = computed(() => this.rightCommitHash()?.substring(0, 10));
    readonly addedLineCount = computed(() => this.repositoryDiffInformation().totalLineChange.addedLineCount);
    readonly removedLineCount = computed(() => this.repositoryDiffInformation().totalLineChange.removedLineCount);

    private readonly userCollapsed = new Map<string, boolean>();

    @ViewChildren('diffPanelContainer')
    private diffPanelContainers?: QueryList<ElementRef<HTMLElement>>;

    private readonly changeDetectorRef = inject(ChangeDetectorRef);
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

        // Update isCollapsed and loadContent properties when loadedTitles changes
        effect(() => {
            const loaded = this.loadedTitles();
            const info = this.repositoryDiffInformation();

            info.diffInformations.forEach((diff) => {
                const isLoaded = loaded.has(diff.title);

                diff.loadContent = isLoaded;

                // Update isCollapsed based on user override or default
                // Default: collapsed if not loaded, expanded if loaded
                const override = this.userCollapsed.get(diff.title);
                diff.isCollapsed = override !== undefined ? override : !isLoaded;
            });

            this.changeDetectorRef.markForCheck();
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
            this.diffPanelsChangesSub = this.diffPanelContainers?.changes.subscribe(() => this.observeDiffPanels());
            this.lazyObserverInitTimeoutId = undefined;
        }, 1000);
    }

    ngOnDestroy(): void {
        this.diffPanelsChangesSub?.unsubscribe();
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

        const diffInfo = this.repositoryDiffInformation().diffInformations.find((info) => info.title === title);
        if (diffInfo) {
            diffInfo.loadContent = true;
            const override = this.userCollapsed.get(title);
            diffInfo.isCollapsed = override !== undefined ? override : false;
        }

        this.updateAllDiffsReady();

        this.changeDetectorRef.markForCheck();
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
        if (!this.intersectionObserver || !this.diffPanelContainers) {
            return;
        }

        this.diffPanelContainers.forEach((panel) => {
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
        this.userCollapsed.set(title, newCollapsedState);

        const diffInfo = this.repositoryDiffInformation().diffInformations.find((diff) => diff.title === title);
        if (diffInfo) {
            diffInfo.isCollapsed = newCollapsedState;
        }

        this.changeDetectorRef.markForCheck();

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
