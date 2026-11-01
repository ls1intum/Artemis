import { Component, computed, input, output, signal } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiButtonSeverity, TumAetUiDialogComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-confirm-button',
    templateUrl: './confirm-autofocus-button.component.html',
    imports: [TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiTooltipDirective, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class ConfirmAutofocusButtonComponent {
    readonly icon = input<IconProp>();
    readonly title = input('');
    readonly tooltip = input('');
    readonly disabled = input(false);
    readonly isLoading = input(false);
    readonly btnType = input(ButtonType.PRIMARY);
    readonly confirmationTitle = input('');
    readonly confirmationTitleTranslationParams = input<Record<string, string>>();
    readonly confirmationText = input('');
    readonly translateText = input(false);
    readonly textIsMarkdown = input(false);
    readonly onConfirm = output<void>();
    readonly onCancel = output<void>();

    protected readonly visible = signal(false);
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly markdown = computed(() => htmlForMarkdown(this.confirmationText()));
    protected readonly severity = computed<TumAetUiButtonSeverity>(() => {
        switch (this.btnType()) {
            case ButtonType.ERROR:
            case ButtonType.ERROR_OUTLINE:
                return 'danger';
            case ButtonType.SECONDARY:
            case ButtonType.DEFAULT:
                return 'secondary';
            case ButtonType.SUCCESS:
            case ButtonType.SUCCESS_OUTLINE:
                return 'success';
            case ButtonType.WARNING:
                return 'warn';
            case ButtonType.INFO:
                return 'info';
            default:
                return 'primary';
        }
    });
    protected readonly variant = computed(() => (this.btnType().includes('outline') ? 'outlined' : 'solid'));
    private confirmed = false;

    onOpenConfirmationModal(): void {
        if (this.disabled() || this.isLoading()) {
            return;
        }
        this.confirmed = false;
        this.visible.set(true);
    }

    protected confirm(): void {
        this.confirmed = true;
        this.visible.set(false);
        this.onConfirm.emit();
    }

    protected onHidden(): void {
        if (!this.confirmed) {
            this.onCancel.emit();
        }
    }
}
