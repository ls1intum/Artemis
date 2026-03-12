import { ChangeDetectorRef, Component, EventEmitter, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LLMModalResult, LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

@Component({
    selector: 'jhi-llm-selection-modal',
    templateUrl: './llm-selection-popup.component.html',
    styleUrls: ['./llm-selection-popup.component.scss'],
    imports: [TranslateDirective],
})
export class LLMSelectionModalComponent implements OnInit, OnDestroy {
    private modalService = inject(LLMSelectionModalService);
    private cdr = inject(ChangeDetectorRef);
    protected themeService = inject(ThemeService);
    private profileService = inject(ProfileService);
    private router = inject(Router);

    @Output() choice = new EventEmitter<LLMModalResult>();

    isVisible = false;
    currentSelection?: LLMSelectionDecision;
    private modalSubscription?: Subscription;

    isOnPremiseEnabled: boolean;

    ngOnInit(): void {
        this.modalSubscription = this.modalService.openModal$.subscribe((currentSelection) => {
            this.currentSelection = currentSelection;
            this.open();
            this.cdr.detectChanges(); // Manually trigger change detection
        });
        this.isOnPremiseEnabled = this.profileService.isLLMDeploymentEnabled();
    }

    ngOnDestroy(): void {
        this.modalSubscription?.unsubscribe();
    }

    open(): void {
        this.isVisible = true;
    }

    close(): void {
        this.isVisible = false;
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
            this.choice.emit(LLM_MODAL_DISMISSED);
            this.modalService.emitChoice(LLM_MODAL_DISMISSED);
            this.close();
        }
    }

    onLearnMoreClick(event: MouseEvent): void {
        event.preventDefault();
        this.modalService.emitChoice(LLM_MODAL_DISMISSED);
        this.router.navigate(['/ai-experience-info']);
        this.close();
    }

    protected readonly Theme = Theme;
    protected readonly LLMSelectionDecision = LLMSelectionDecision;
}
