import { Component, computed, inject, signal } from '@angular/core';
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
    isLoading = signal(true);
    searchString = signal<string>('');
    header = computed<string>(() => this.computeHeader());
    searchBarPlaceholder = computed<string>(() => this.computeSearchBarPlaceholder());
    suggestedStudents = signal<TutorialGroupRegisteredStudentDTO[]>(mockStudents);
    selectedStudents = signal<TutorialGroupRegisteredStudentDTO[]>([]);
    suggestionHighlightIndex = signal<number | undefined>(undefined);

    ngOnDestroy(): void {
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
    }

    open() {
        this.isOpen.set(true);
    }

    onKeyDown(event: KeyboardEvent): void {
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
            this.ensureIndexVisible(updatedSuggestionHighlightIndex);
        }
    }

    private ensureIndexVisible(index: number): void {
        const viewport = this.viewport();
        if (!viewport) return;

        const scrollTop = viewport.measureScrollOffset();
        const viewportHeight = viewport.getViewportSize();

        const ITEM_SIZE = 36;
        const itemTop = index * ITEM_SIZE;
        const itemBottom = itemTop + ITEM_SIZE;

        const viewportTop = scrollTop;
        const viewportBottom = scrollTop + viewportHeight;

        if (itemTop < viewportTop) {
            viewport.scrollToIndex(index, 'smooth');
        } else if (itemBottom > viewportBottom) {
            viewport.scrollToIndex(index, 'smooth');
        }
    }

    openPanel(): void {
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
    }

    trackStudentById(index: number, student: TutorialGroupRegisteredStudentDTO): number {
        return student.id;
    }

    private computeHeader(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.registerModal.header');
    }

    private computeSearchBarPlaceholder(): string {
        return 'Search by Login or Name';
    }
}

const mockStudents: TutorialGroupRegisteredStudentDTO[] = [
    { id: 1, name: 'Jaohn Doe', login: 'jdoe' },
    { id: 2, name: 'Jaice Smith', login: 'asmith' },
    { id: 3, name: 'Jabruce Wayne', login: 'bwayne' },
    { id: 4, name: 'Jalark Kent', login: 'ckent' },
    { id: 5, name: 'Japeter Parker', login: 'pparker' },
    { id: 6, name: 'Jaaron Miller', login: 'amiller' },
    { id: 7, name: 'Jajessica Brown', login: 'jbrown' },
    { id: 8, name: 'Jaalex Johnson', login: 'ajohnson' },
    { id: 9, name: 'Jalinda Davis', login: 'ldavis' },
    { id: 10, name: 'Jamichael Wilson', login: 'mwilson' },
    { id: 11, name: 'Jaaniel Taylor', login: 'dtaylor' },
    { id: 12, name: 'Jasophia Anderson', login: 'sanderson' },
    { id: 13, name: 'Jabrian Thomas', login: 'bthomas' },
    { id: 14, name: 'Jarebecca Moore', login: 'rmoore' },
    { id: 15, name: 'Jajames Jackson', login: 'jjackson' },
    { id: 16, name: 'Japatricia White', login: 'pwhite' },
    { id: 17, name: 'Jarobert Harris', login: 'rharris' },
    { id: 18, name: 'Jamanda Martin', login: 'amartin' },
    { id: 19, name: 'Jacharles Thompson', login: 'cthompson' },
    { id: 20, name: 'Janicole Garcia', login: 'ngarcia' },
];
