import { Component, ElementRef, TemplateRef, ViewContainerRef, computed, inject, input, model, signal, viewChild } from '@angular/core';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { TutorialEditValidation, TutorialEditValidationStatus } from 'app/tutorialgroup/manage/tutorial-edit/tutorial-edit.component';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import { ScrollingModule } from '@angular/cdk/scrolling';

@Component({
    selector: 'jhi-tutorial-edit-languages-input',
    imports: [InputTextModule, InputGroupModule, InputGroupAddonModule, TooltipModule, FormsModule, ScrollingModule],
    templateUrl: './tutorial-edit-languages-input.component.html',
    styleUrl: './tutorial-edit-languages-input.component.scss',
})
export class TutorialEditLanguagesInputComponent {
    protected readonly TutorialEditValidationStatus = TutorialEditValidationStatus;
    private overlay = inject(Overlay);
    private overlayRef: OverlayRef | undefined = undefined;
    private viewContainerRef = inject(ViewContainerRef);
    private searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');
    private panelTemplate = viewChild<TemplateRef<unknown>>('panelTemplate');
    private viewport = viewChild<CdkVirtualScrollViewport>(CdkVirtualScrollViewport);

    alreadyUsedLanguages = input.required<string[]>();
    language = model<string>('');
    languageInputTouched = signal(false);
    languageValidationResult = computed<TutorialEditValidation>(() => this.computeLanguageValidation());
    suggestionHighlightIndex = signal<number | undefined>(undefined);

    openPanel(): void {
        if (!this.alreadyUsedLanguages()) return;

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

    closePanel() {
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.suggestionHighlightIndex.set(undefined);
    }

    onBlur() {
        if (!this.languageInputTouched()) {
            this.languageInputTouched.set(true);
        }
        this.closePanel();
    }

    selectSuggestedLanguage(suggestionIndex: number): void {
        const languages = this.alreadyUsedLanguages();
        const language = languages[suggestionIndex];
        if (!language) return;

        this.language.set(language);
        this.closePanel();
    }

    onKeyDown(event: KeyboardEvent) {
        const viewport = this.viewport();
        if (!viewport) return;

        if (event.key === 'Enter') {
            const suggestionIndex = this.suggestionHighlightIndex();
            if (suggestionIndex !== undefined) {
                event.preventDefault();
                this.selectSuggestedLanguage(suggestionIndex);
            }
            this.closePanel();
            return;
        }

        const alreadyUsedLanguages = this.alreadyUsedLanguages();
        if (!alreadyUsedLanguages) return;
        const numberOfAlreadyUsedLanguages = alreadyUsedLanguages.length;
        if (numberOfAlreadyUsedLanguages === 0) return;
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            this.suggestionHighlightIndex.update((selectionTargetIndex) => {
                return selectionTargetIndex !== undefined ? Math.min(selectionTargetIndex + 1, numberOfAlreadyUsedLanguages - 1) : 0;
            });
        }
        if (event.key === 'ArrowUp') {
            event.preventDefault();
            this.suggestionHighlightIndex.update((selectionTargetIndex) => {
                return selectionTargetIndex !== undefined ? Math.max(selectionTargetIndex - 1, 0) : numberOfAlreadyUsedLanguages - 1;
            });
        }

        const updatedSuggestionHighlightIndex = this.suggestionHighlightIndex();
        if (updatedSuggestionHighlightIndex !== undefined) {
            const scrollTop = viewport.measureScrollOffset();
            const viewportHeight = viewport.getViewportSize();

            const ITEM_SIZE = 40;
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

    private computeLanguageValidation(): TutorialEditValidation {
        if (!this.languageInputTouched()) return { status: TutorialEditValidationStatus.VALID };
        const trimmedLanguage = this.language().trim();
        if (!trimmedLanguage) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: 'Please choose a language. The system automatically removes leading/trailing whitespaces.',
            };
        }
        if (trimmedLanguage && trimmedLanguage.length > 255) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: 'Language must contain at most 255 characters. The system automatically removes leading/trailing whitespaces.',
            };
        }
        return { status: TutorialEditValidationStatus.VALID };
    }
}
