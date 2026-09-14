import { Component, ElementRef, OnDestroy, TemplateRef, ViewContainerRef, computed, effect, inject, input, model, signal, viewChild } from '@angular/core';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { Validation, ValidationStatus } from 'app/foundation/util/validation';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { createPanelOverlay } from 'app/tutorialgroup/shared/util/search-input-overlay';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo, faFlag } from '@fortawesome/free-solid-svg-icons';
import { TumUiInputDirective, TumUiInputGroupAddonComponent, TumUiInputGroupComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-tutorial-edit-languages-input',
    imports: [
        FaIconComponent,
        TumUiInputDirective,
        TumUiInputGroupComponent,
        TumUiInputGroupAddonComponent,
        TumUiTooltipDirective,
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
    templateUrl: './tutorial-edit-languages-input.component.html',
    styleUrl: './tutorial-edit-languages-input.component.scss',
})
export class TutorialEditLanguagesInputComponent implements OnDestroy {
    protected readonly TutorialEditValidationStatus = ValidationStatus;
    protected readonly faFlag = faFlag;
    protected readonly faCircleInfo = faCircleInfo;
    private overlay = inject(Overlay);
    private overlayRef: OverlayRef | undefined = undefined;
    private viewContainerRef = inject(ViewContainerRef);
    private searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');
    private panelTemplate = viewChild<TemplateRef<unknown>>('panelTemplate');
    private languageValidationResultInternal = computed<Validation>(() => this.computeLanguageValidation());

    alreadyUsedLanguages = input.required<string[]>();
    language = model<string>('');
    languageInputTouched = signal(false);
    languageValidationResult = model<Validation>({ status: ValidationStatus.VALID });
    suggestionHighlightIndex = signal<number | undefined>(undefined);

    constructor() {
        effect(() => this.languageValidationResult.set(this.languageValidationResultInternal()));
    }

    ngOnDestroy(): void {
        this.closePanel();
    }

    openPanel(): void {
        if (!this.alreadyUsedLanguages()) return;
        if (this.overlayRef?.hasAttached()) return;
        this.overlayRef = createPanelOverlay(this.overlay, this.searchInput()?.nativeElement, this.panelTemplate(), this.viewContainerRef);
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
        if (event.key === 'Enter') {
            event.preventDefault();
            const suggestionIndex = this.suggestionHighlightIndex();
            if (suggestionIndex !== undefined) {
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
                return selectionTargetIndex !== undefined ? (selectionTargetIndex + 1) % numberOfAlreadyUsedLanguages : 0;
            });
        }
        if (event.key === 'ArrowUp') {
            event.preventDefault();
            this.suggestionHighlightIndex.update((selectionTargetIndex) => {
                return selectionTargetIndex !== undefined
                    ? (selectionTargetIndex - 1 + numberOfAlreadyUsedLanguages) % numberOfAlreadyUsedLanguages
                    : numberOfAlreadyUsedLanguages - 1;
            });
        }

        const updatedSuggestionHighlightIndex = this.suggestionHighlightIndex();
        if (updatedSuggestionHighlightIndex !== undefined) {
            const highlightedSuggestion = this.overlayRef?.overlayElement.querySelectorAll<HTMLButtonElement>('.language-row')[updatedSuggestionHighlightIndex];
            highlightedSuggestion?.scrollIntoView({ block: 'nearest' });
        }
    }

    /**
     * Validates the value itself. Whether the reader has touched the field decides only whether the error is
     * shown, exactly as the title and tutor fields do; folding it in here reported an empty required language
     * as valid, which left the save button enabled and the request rejected by the server with nothing shown.
     */
    private computeLanguageValidation(): Validation {
        const trimmedLanguage = this.language().trim();
        if (!trimmedLanguage) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.languageRequired',
            };
        }
        if (trimmedLanguage && trimmedLanguage.length > 255) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.languageLength',
            };
        }
        return { status: ValidationStatus.VALID };
    }
}
