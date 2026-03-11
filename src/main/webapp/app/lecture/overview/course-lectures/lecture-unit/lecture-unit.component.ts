import { Component, ElementRef, Injector, OnDestroy, afterNextRender, computed, effect, inject, input, output, signal } from '@angular/core';
import { IconDefinition, faCheckCircle, faCircle, faDownload, faExternalLinkAlt } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { NgbCollapseModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';

@Component({
    selector: 'jhi-lecture-unit-card',
    imports: [FontAwesomeModule, NgbCollapseModule, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, CommonModule, NgbTooltipModule, CompetencyContributionComponent],
    templateUrl: './lecture-unit.component.html',
    styleUrl: './lecture-unit.component.scss',
})
export class LectureUnitComponent implements OnDestroy {
    // Delay to allow async content (PDFs, videos) to render before scrolling
    private static readonly SCROLL_INTO_VIEW_DELAY_MS = 500;

    private router = inject(Router);
    private elementRef = inject(ElementRef);
    private injector = inject(Injector);
    private scrollTimeoutId: ReturnType<typeof setTimeout> | undefined;

    protected faDownload = faDownload;
    protected faCheckCircle = faCheckCircle;
    protected faCircle = faCircle;

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

    readonly onShowIsolated = output<void>();
    readonly onCollapse = output<boolean>();
    readonly onCompletion = output<boolean>();

    readonly isCollapsed = signal<boolean>(true);

    readonly isVisibleToStudents = computed(() => this.lectureUnit().visibleToStudents);
    readonly isStudentPath = computed(() => this.router.url.startsWith('/courses'));

    constructor() {
        // Watch initiallyExpanded signal and trigger expansion/scroll when it becomes true
        effect(
            (onCleanup) => {
                if (this.initiallyExpanded()) {
                    // Set collapsed state and emit event so parent components can load content
                    if (this.isCollapsed()) {
                        this.isCollapsed.set(false);
                        this.onCollapse.emit(false);
                    }

                    afterNextRender(
                        () => {
                            this.scrollTimeoutId = setTimeout(() => {
                                this.elementRef.nativeElement.scrollIntoView?.({
                                    behavior: 'smooth',
                                    block: 'start',
                                });
                            }, LectureUnitComponent.SCROLL_INTO_VIEW_DELAY_MS);
                        },
                        { injector: this.injector },
                    );
                }

                // Cleanup: clear timeout if effect re-runs or component is destroyed
                onCleanup(() => {
                    if (this.scrollTimeoutId !== undefined) {
                        clearTimeout(this.scrollTimeoutId);
                        this.scrollTimeoutId = undefined;
                    }
                });
            },
            { injector: this.injector },
        );
    }

    ngOnDestroy(): void {
        // Clear any outstanding scroll timeout to avoid callbacks after destruction
        if (this.scrollTimeoutId !== undefined) {
            clearTimeout(this.scrollTimeoutId);
            this.scrollTimeoutId = undefined;
        }
    }

    toggleCompletion(event: Event) {
        event.stopPropagation();
        this.onCompletion.emit(!this.lectureUnit().completed!);
    }

    toggleCollapse() {
        this.isCollapsed.update((isCollapsed) => !isCollapsed);
        this.onCollapse.emit(this.isCollapsed());

        if (!this.isCollapsed()) {
            afterNextRender(
                () => {
                    this.elementRef.nativeElement.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' });
                },
                { injector: this.injector },
            );
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
}
