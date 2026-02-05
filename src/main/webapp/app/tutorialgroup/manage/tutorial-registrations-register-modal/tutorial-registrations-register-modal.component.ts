import { Component, computed, effect, inject, input, signal } from '@angular/core';
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
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-tutorial-registrations-register-modal',
    imports: [Dialog, FormsModule, IconFieldModule, InputIconModule, InputTextModule, OverlayModule, ScrollingModule],
    templateUrl: './tutorial-registrations-register-modal.component.html',
    styleUrl: './tutorial-registrations-register-modal.component.scss',
})
export class TutorialRegistrationsRegisterModalComponent implements OnDestroy {
    private readonly PAGE_SIZE = 25;

    private translateService = inject(TranslateService);
    private tutorialGroupsService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private overlay = inject(Overlay);
    private overlayRef: OverlayRef | undefined = undefined;
    private viewContainerRef = inject(ViewContainerRef);
    private searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');
    private panelTemplate = viewChild<TemplateRef<unknown>>('panelTemplate');
    private viewport = viewChild<CdkVirtualScrollViewport>(CdkVirtualScrollViewport);

    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    isOpen = signal(false);
    header = computed<string>(() => this.computeHeader());
    searchBarPlaceholder = computed<string>(() => this.computeSearchBarPlaceholder());
    searchString = signal<string>('');
    selectedStudents = signal<TutorialGroupRegisteredStudentDTO[]>([]);
    suggestedStudents = signal<TutorialGroupRegisteredStudentDTO[]>([]);
    nextSuggestedStudentsPageIndex = signal(0);
    hasMorePages = signal(true);
    suggestionHighlightIndex = signal<number | undefined>(undefined);
    firstSuggestedStudentsPageLoading = signal(false);
    nextSuggestedStudentsPageLoading = signal(false);
    inputIsFocused = signal(false);

    constructor() {
        effect(() => {
            const panelNotAlreadyOpen = this.overlayRef === undefined;
            const suggestedStudentsExist = this.suggestedStudents().length > 0;
            const inputIsFocused = this.inputIsFocused();
            if (inputIsFocused && suggestedStudentsExist && panelNotAlreadyOpen) {
                this.openPanel();
            }
        });

        effect(() => {
            const trimmedSearchString = this.searchString().trim();
            if (!trimmedSearchString) return;
            this.loadFirstPage(trimmedSearchString);
        });
    }

    ngOnDestroy(): void {
        this.closePanel();
    }

    open() {
        this.isOpen.set(true);
    }

    onKeyDown(event: KeyboardEvent) {
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

    onInputFocusIn() {
        this.inputIsFocused.set(true);
        const panelNotAlreadyOpen = this.overlayRef === undefined;
        const suggestedStudentsExist = this.suggestedStudents().length > 0;
        if (suggestedStudentsExist && panelNotAlreadyOpen) {
            this.openPanel();
        }
    }

    onInputFocusOut() {
        this.inputIsFocused.set(false);
        this.suggestionHighlightIndex.set(undefined);
        this.closePanel();
    }

    selectSuggestion(suggestionIndex: number): void {
        const students = this.suggestedStudents();
        const student = students[suggestionIndex];
        if (!student) return;

        this.selectedStudents.update((selectedStudents) =>
            selectedStudents.some((otherStudent) => otherStudent.id === student.id) ? selectedStudents : [...selectedStudents, student],
        );

        this.searchString.set('');
        this.resetSuggestedStudentsState();
        this.closePanel();
        this.searchInput()?.nativeElement.focus();
    }

    trackStudentById(index: number, student: TutorialGroupRegisteredStudentDTO): number {
        return student.id;
    }

    private openPanel(): void {
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

    private closePanel() {
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
    }

    private loadFirstPage(trimmedSearchString: string) {
        this.resetSuggestedStudentsState();

        this.firstSuggestedStudentsPageLoading.set(true);

        this.tutorialGroupsService.getUnregisteredStudentDTOs(this.courseId(), this.tutorialGroupId(), trimmedSearchString, 0, this.PAGE_SIZE).subscribe({
            next: (page) => {
                this.suggestedStudents.set(page);
                this.nextSuggestedStudentsPageIndex.set(1);
                this.hasMorePages.set(page.length === this.PAGE_SIZE);
                this.firstSuggestedStudentsPageLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupRegistrations.registerModal.fetchSuggestionsError');
                this.firstSuggestedStudentsPageLoading.set(false);
            },
        });
    }

    private loadNextPage() {
        if (this.nextSuggestedStudentsPageLoading()) return;
        if (!this.hasMorePages()) return;

        this.nextSuggestedStudentsPageLoading.set(true);

        const nextPageIndex = this.nextSuggestedStudentsPageIndex();
        const trimmedSearchString = this.searchString().trim();
        this.tutorialGroupsService.getUnregisteredStudentDTOs(this.courseId(), this.tutorialGroupId(), trimmedSearchString, nextPageIndex, this.PAGE_SIZE).subscribe({
            next: (page) => {
                this.suggestedStudents.update((current) => [...current, ...page]);
                this.nextSuggestedStudentsPageIndex.update((i) => i + 1);
                this.hasMorePages.set(page.length === this.PAGE_SIZE);
                this.nextSuggestedStudentsPageLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupRegistrations.registerModal.fetchSuggestionsError');
                this.nextSuggestedStudentsPageLoading.set(false);
            },
        });
    }

    private resetSuggestedStudentsState() {
        this.suggestedStudents.set([]);
        this.suggestionHighlightIndex.set(undefined);
        this.nextSuggestedStudentsPageIndex.set(0);
        this.hasMorePages.set(true);
    }

    private computeHeader(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.registerModal.header');
    }

    private computeSearchBarPlaceholder(): string {
        return 'Search by Login or Name';
    }
}
