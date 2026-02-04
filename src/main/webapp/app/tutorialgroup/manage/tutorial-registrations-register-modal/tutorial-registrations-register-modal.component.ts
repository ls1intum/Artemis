import { Component, computed, effect, inject, signal } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { ElementRef, OnDestroy, TemplateRef } from '@angular/core';
import { viewChild } from '@angular/core';
import { Overlay, OverlayModule, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { ViewContainerRef } from '@angular/core';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { CdkVirtualScrollViewport } from '@angular/cdk/scrolling';

@Component({
    selector: 'jhi-tutorial-registrations-register-modal',
    imports: [Dialog, FormsModule, IconFieldModule, InputIconModule, InputTextModule, OverlayModule, ScrollingModule],
    templateUrl: './tutorial-registrations-register-modal.component.html',
    styleUrl: './tutorial-registrations-register-modal.component.scss',
})
export class TutorialRegistrationsRegisterModalComponent implements OnDestroy {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private overlay = inject(Overlay);
    private overlayRef: OverlayRef | undefined = undefined;
    private viewContainerRef = inject(ViewContainerRef);
    private searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');
    private panelTemplate = viewChild<TemplateRef<unknown>>('panelTemplate');
    private viewport = viewChild<CdkVirtualScrollViewport>(CdkVirtualScrollViewport);

    isOpen = signal(false);
    header = computed<string>(() => this.computeHeader());
    searchBarPlaceholder = computed<string>(() => this.computeSearchBarPlaceholder());
    searchString = signal<string>('');
    suggestedStudents = signal<TutorialGroupRegisteredStudentDTO[]>([]);
    selectedStudents = signal<TutorialGroupRegisteredStudentDTO[]>([]);
    suggestionHighlightIndex = signal<number | undefined>(undefined);

    currentPage = signal(0);
    hasMorePages = signal(true);
    firstPageLoading = signal(false);
    nextPageLoading = signal(false);

    constructor() {
        effect(() => {
            if (this.suggestedStudents().length > 0) {
                this.openPanelIfNotAlreadyOpen();
            }
        });

        effect(() => {
            const query = this.searchString().trim();
            if (!query) return;

            this.loadFirstPage();
        });
    }

    ngOnDestroy(): void {
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
    }

    open() {
        this.isOpen.set(true);
    }

    onKeyDown(event: KeyboardEvent): void {
        const viewport = this.viewport();
        if (!viewport) return;

        if (event.key === 'Enter') {
            const suggestionIndex = this.suggestionHighlightIndex();
            if (suggestionIndex !== undefined) {
                event.preventDefault();
                this.selectSuggestion(suggestionIndex);
            }
            return;
        }

        const suggestedStudents = this.suggestedStudents();
        if (!suggestedStudents) return;
        const numberOfSuggestedStudents = suggestedStudents.length;
        if (numberOfSuggestedStudents === 0) return;
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            this.suggestionHighlightIndex.update((selectionTargetIndex) => {
                return selectionTargetIndex !== undefined ? Math.min(selectionTargetIndex + 1, numberOfSuggestedStudents - 1) : 0;
            });
        }
        if (event.key === 'ArrowUp') {
            event.preventDefault();
            this.suggestionHighlightIndex.update((selectionTargetIndex) => {
                return selectionTargetIndex !== undefined ? Math.max(selectionTargetIndex - 1, 0) : numberOfSuggestedStudents - 1;
            });
        }

        const updatedSuggestionHighlightIndex = this.suggestionHighlightIndex();
        if (updatedSuggestionHighlightIndex !== undefined) {
            const scrollTop = viewport.measureScrollOffset();
            const viewportHeight = viewport.getViewportSize();

            const ITEM_SIZE = 36;
            const itemTop = updatedSuggestionHighlightIndex * ITEM_SIZE;
            const itemBottom = itemTop + ITEM_SIZE;

            const viewportTop = scrollTop;
            const viewportBottom = scrollTop + viewportHeight;

            if (itemTop < viewportTop) {
                viewport.scrollToIndex(updatedSuggestionHighlightIndex, 'smooth');
            } else if (itemBottom > viewportBottom) {
                viewport.scrollToIndex(updatedSuggestionHighlightIndex, 'smooth');
            }
        }
    }

    selectSuggestion(suggestionIndex: number): void {
        const students = this.suggestedStudents();
        const student = students[suggestionIndex];
        if (!student) return;

        this.selectedStudents.update((selectedStudents) =>
            selectedStudents.some((otherStudent) => otherStudent.id === student.id) ? selectedStudents : [...selectedStudents, student],
        );

        this.searchString.set('');
        this.suggestionHighlightIndex.set(undefined);
        this.suggestedStudents.set([]);

        this.overlayRef?.dispose();
        this.overlayRef = undefined;

        this.searchInput()?.nativeElement.focus();
    }

    trackStudentById(index: number, student: TutorialGroupRegisteredStudentDTO): number {
        return student.id;
    }

    private openPanelIfNotAlreadyOpen(): void {
        if (this.overlayRef) return;

        const searchInput = this.searchInput()?.nativeElement;
        const panelTemplate = this.panelTemplate();
        if (!searchInput || !panelTemplate) return;

        const positionStrategy = this.overlay
            .position()
            .flexibleConnectedTo(searchInput)
            .withPositions([
                {
                    originX: 'start',
                    originY: 'bottom',
                    overlayX: 'start',
                    overlayY: 'top',
                },
                {
                    originX: 'start',
                    originY: 'top',
                    overlayX: 'start',
                    overlayY: 'bottom',
                },
            ])
            .withFlexibleDimensions(false)
            .withPush(false);

        this.overlayRef = this.overlay.create({
            positionStrategy,
            scrollStrategy: this.overlay.scrollStrategies.reposition(),
        });

        this.overlayRef.updateSize({
            width: searchInput.offsetWidth,
        });

        this.overlayRef.attach(new TemplatePortal(panelTemplate, this.viewContainerRef));

        const viewport = this.viewport();
        if (!viewport) return;

        // TODO: fix memory leak
        viewport.elementScrolled().subscribe(() => {
            const distanceToBottom = viewport.measureScrollOffset('bottom');

            if (distanceToBottom < 40) {
                this.loadNextPage();
            }
        });
    }

    private async loadFirstPage(): Promise<void> {
        this.currentPage.set(0);
        this.hasMorePages.set(true);
        this.suggestedStudents.set([]);

        this.firstPageLoading.set(true);

        const page = await fetchPage(0);

        this.suggestedStudents.set(page);
        this.currentPage.set(1);

        this.hasMorePages.set(page.length === PAGE_SIZE);

        this.firstPageLoading.set(false);
    }

    private async loadNextPage(): Promise<void> {
        if (this.nextPageLoading()) return;
        if (!this.hasMorePages()) return;

        this.nextPageLoading.set(true);

        const pageIndex = this.currentPage();
        const page = await fetchPage(pageIndex);

        this.suggestedStudents.update((current) => [...current, ...page]);
        this.currentPage.set(pageIndex + 1);

        this.hasMorePages.set(page.length === PAGE_SIZE);

        this.nextPageLoading.set(false);
    }

    private computeHeader(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.registerModal.header');
    }

    private computeSearchBarPlaceholder(): string {
        return 'Search by Login or Name';
    }
}

const PAGE_SIZE = 25;

const MOCK_PAGES: TutorialGroupRegisteredStudentDTO[][] = generateMockPages();

function generateMockPages(): TutorialGroupRegisteredStudentDTO[][] {
    const pages: TutorialGroupRegisteredStudentDTO[][] = [];
    let id = 1;

    for (let p = 0; p < 4; p++) {
        const page: TutorialGroupRegisteredStudentDTO[] = [];
        for (let i = 0; i < 25; i++) {
            const currentId = id++;
            page.push({
                id: currentId,
                login: `jauser${currentId}`,
                name: `JaUser ${currentId}`,
            });
        }
        pages.push(page);
    }

    return pages;
}

function fetchPage(page: number): Promise<TutorialGroupRegisteredStudentDTO[]> {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(MOCK_PAGES[page] ?? []);
        }, 2000);
    });
}
