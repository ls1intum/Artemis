import { Component, ElementRef, OnDestroy, OnInit, effect, inject, output, signal, viewChild } from '@angular/core';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LLMModalResult, LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

@Component({
    selector: 'jhi-llm-selection-modal',
    templateUrl: './llm-selection-popup.component.html',
    styleUrls: ['./llm-selection-popup.component.scss'],
    imports: [TranslateDirective],
})
export class LLMSelectionModalComponent implements OnInit, OnDestroy {
    private modalService = inject(LLMSelectionModalService);
    protected themeService = inject(ThemeService);
    private profileService = inject(ProfileService);
    private router = inject(Router);

    readonly choice = output<LLMModalResult>();

    readonly isVisible = signal(false);
    readonly currentSelection = signal<LLMSelectionDecision | undefined>(undefined);
    private modalSubscription?: Subscription;
    private readonly dialog = viewChild<ElementRef<HTMLElement>>('dialog');

    readonly isOnPremiseEnabled = signal<boolean>(undefined!);

    constructor() {
        // Focus starts on the button that opened the dialog; move it inside so keyboard users, and Escape, reach it right away.
        effect(() => this.dialog()?.nativeElement.focus());
    }

    ngOnInit(): void {
        this.modalSubscription = this.modalService.openModal$.subscribe((currentSelection) => {
            this.currentSelection.set(currentSelection);
            this.open();
        });
        this.isOnPremiseEnabled.set(this.profileService.isLLMDeploymentEnabled());
    }

    ngOnDestroy(): void {
        this.modalSubscription?.unsubscribe();
    }

    open(): void {
        this.isVisible.set(true);
    }

    close(): void {
        this.isVisible.set(false);
    }

    selectCloud(): void {
        this.choice.emit(LLMSelectionDecision.CLOUD_AI);
        this.modalService.emitChoice(LLMSelectionDecision.CLOUD_AI);
        this.close();
    }

    selectLocal(): void {
        this.choice.emit(LLMSelectionDecision.LOCAL_AI);
        this.modalService.emitChoice(LLMSelectionDecision.LOCAL_AI);
        this.close();
    }

    selectNone(): void {
        this.choice.emit(LLMSelectionDecision.NO_AI);
        this.modalService.emitChoice(LLMSelectionDecision.NO_AI);
        this.close();
    }

    onBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget) {
            this.dismiss();
        }
    }

    /**
     * Closes the modal without a choice. It must resolve the pending choice: otherwise a choice made later elsewhere,
     * e.g. in the user settings, would resolve it and resume the action that opened the modal.
     */
    dismiss(): void {
        this.choice.emit(LLM_MODAL_DISMISSED);
        this.modalService.emitChoice(LLM_MODAL_DISMISSED);
        this.close();
    }

    onLearnMoreClick(event: MouseEvent): void {
        event.preventDefault();
        this.modalService.emitChoice(LLM_MODAL_DISMISSED);
        void this.router.navigate(['/ai-experience-info']);
        this.close();
    }

    protected readonly Theme = Theme;
    protected readonly LLMSelectionDecision = LLMSelectionDecision;
}
