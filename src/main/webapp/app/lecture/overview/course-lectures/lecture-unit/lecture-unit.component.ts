import { Component, ElementRef, Injector, OnDestroy, afterNextRender, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { IconDefinition, faCheckCircle, faCircle, faDownload, faExpand, faExternalLinkAlt } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { NgbCollapseModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';

@Component({
    selector: 'jhi-lecture-unit-card',
    imports: [FontAwesomeModule, NgbCollapseModule, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, CommonModule, NgbTooltipModule, CompetencyContributionComponent],
    templateUrl: './lecture-unit.component.html',
    styleUrl: './lecture-unit.component.scss',
})
export class LectureUnitComponent implements OnDestroy {
    private static readonly SCROLL_INTO_VIEW_DELAY_MS = 500;

    private router = inject(Router);
    private route = inject(ActivatedRoute, { optional: true });
    private elementRef = inject(ElementRef);
    private injector = inject(Injector);
    private scrollTimeoutId: ReturnType<typeof setTimeout> | undefined;
    private autoExpanded = false;

    protected faDownload = faDownload;
    protected faCheckCircle = faCheckCircle;
    protected faCircle = faCircle;
    protected faExpand = faExpand;

    courseId = input.required<number>();

    lectureUnit = input.required<LectureUnit>();
    icon = input.required<IconDefinition>();

    showViewIsolatedButton = input<boolean>(false);
    viewIsolatedButtonLabel = input<string>('artemisApp.textUnit.isolated');
    viewIsolatedButtonIcon = input<IconDefinition>(faExternalLinkAlt);
    isPresentationMode = input.required<boolean>();
    initiallyExpanded = input<boolean>(false);

    readonly showOriginalVersionButton = input<boolean>(false);
    readonly onShowOriginalVersion = output<void>();

    /** Controls visibility of the fullscreen action button in the lecture unit header. */
    readonly showFullscreenButton = input<boolean>(false);
    /** Emitted when the fullscreen action button is activated. */
    readonly onFullscreen = output<void>();

    readonly onShowIsolated = output<void>();
    readonly onCollapse = output<boolean>();
    readonly onCompletion = output<boolean>();

    readonly isCollapsed = signal<boolean>(true);

    readonly isVisibleToStudents = computed(() => this.lectureUnit().visibleToStudents);
    readonly isStudentPath = computed(() => this.router.url.startsWith('/courses'));

    constructor() {
        effect(
            (onCleanup) => {
                const shouldAutoExpand = this.initiallyExpanded();
                if (shouldAutoExpand && !this.autoExpanded) {
                    this.autoExpanded = true;
                    if (untracked(() => this.isCollapsed())) {
                        this.isCollapsed.set(false);
                        this.onCollapse.emit(false);
                    }

                    this.scheduleScroll('start', LectureUnitComponent.SCROLL_INTO_VIEW_DELAY_MS, true);
                }
                if (!shouldAutoExpand) {
                    this.autoExpanded = false;
                }

                onCleanup(() => {
                    this.clearScrollTimeout();
                });
            },
            { injector: this.injector },
        );
    }

    ngOnDestroy(): void {
        this.clearScrollTimeout();
    }

    toggleCompletion(event: Event) {
        event.stopPropagation();
        this.onCompletion.emit(!this.lectureUnit().completed!);
    }

    toggleCollapse() {
        this.isCollapsed.update((isCollapsed) => !isCollapsed);
        this.onCollapse.emit(this.isCollapsed());

        if (!this.isCollapsed()) {
            this.scheduleScroll('nearest');
        }
    }

    handleIsolatedView(event: Event) {
        event.stopPropagation();
        this.onShowIsolated.emit();
    }

    handleOriginalVersionView(event: Event) {
        event.stopPropagation();
        this.onShowOriginalVersion.emit();
    }

    /** Handles fullscreen action clicks without collapsing/expanding the card. */
    handleFullscreen(event: Event): void {
        event.stopPropagation();
        this.onFullscreen.emit();
    }

    private scheduleScroll(block: ScrollLogicalPosition, delayMs = 0, useDeeplinkTarget = false): void {
        afterNextRender(
            () => {
                const doScroll = () => {
                    const queryParams = useDeeplinkTarget ? this.route?.snapshot.queryParams : undefined;
                    const timestamp = queryParams?.['timestamp'];
                    const page = queryParams?.['page'];

                    // Scroll to video player if timestamp is provided (deeplinking)
                    if (timestamp !== undefined) {
                        const videoPlayer = this.elementRef.nativeElement.querySelector('jhi-video-player');
                        if (videoPlayer) {
                            videoPlayer.scrollIntoView({ behavior: 'smooth', block: 'start' });
                            return;
                        }
                    }

                    // Scroll to PDF viewer if page is provided (deeplinking)
                    if (page !== undefined) {
                        const pdfViewer = this.elementRef.nativeElement.querySelector('jhi-pdf-viewer');
                        if (pdfViewer) {
                            pdfViewer.scrollIntoView({ behavior: 'smooth', block: 'start' });
                            return;
                        }
                    }

                    // Default: scroll to unit card
                    this.elementRef.nativeElement.scrollIntoView?.({ behavior: 'smooth', block });
                };
                if (delayMs > 0) {
                    this.scrollTimeoutId = setTimeout(doScroll, delayMs);
                } else {
                    doScroll();
                }
            },
            { injector: this.injector },
        );
    }

    private clearScrollTimeout(): void {
        if (this.scrollTimeoutId !== undefined) {
            clearTimeout(this.scrollTimeoutId);
            this.scrollTimeoutId = undefined;
        }
    }
}
