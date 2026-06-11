import { Component, computed, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faBan, faExclamationCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TranslateService } from '@ngx-translate/core';
import { SwitchEditModeButtonComponent } from 'app/programming/manage/update/switch-edit-mode-button/switch-edit-mode-button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ExerciseUpdateNotificationComponent } from 'app/exercise/exercise-update-notification/exercise-update-notification.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ButtonModule } from 'primeng/button';

/** The create/edit form mode: the two manual modes plus the create-only AI-assisted mode. */
export type FormEditMode = 'simple' | 'advanced' | 'ai';

@Component({
    selector: 'jhi-form-footer',
    templateUrl: 'form-footer.component.html',
    styleUrls: ['form-footer.component.scss'],
    imports: [
        NgbTooltip,
        FormsModule,
        SelectButtonModule,
        SwitchEditModeButtonComponent,
        HelpIconComponent,
        ExerciseUpdateNotificationComponent,
        TranslateDirective,
        FaIconComponent,
        ButtonModule,
        ArtemisTranslatePipe,
    ],
})
export class FormFooterComponent {
    private readonly translateService = inject(TranslateService);

    protected readonly ButtonSize = ButtonSize;
    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faExclamationCircle = faExclamationCircle;
    protected readonly facArtemisIntelligence = facArtemisIntelligence;

    isSaving = input(false);
    isDisabled = input(false);
    invalidReasons = input<ValidationReason[]>([]);
    isGeneratingWithAi = input(false);
    notificationText = input<string | undefined>();
    switchEditMode = input<(() => void) | undefined>();
    isImport = input<boolean>();
    isCreation = input<boolean>();
    isSimpleMode = input<boolean>();
    areAuxiliaryRepositoriesValid = input<boolean>(true);

    // AI-mode wiring (all additive and default-off, so other exercise types are unaffected). When aiModeAvailable, the 2-way switch is replaced by a 3-way mode selector and, in AI
    // mode, the footer's primary action becomes "Generate entire exercise" (routed via aiGenerate) instead of the normal save.
    aiModeAvailable = input<boolean>(false);
    editMode = input<FormEditMode | undefined>();
    isAiMode = input<boolean>(false);
    aiBriefPresent = input<boolean>(false);
    /** Whether the selected language can be verified by the generation oracle. When false, the "Generate entire exercise" action stays disabled with an explanatory tooltip. */
    aiLanguageSupported = input<boolean>(true);

    notificationTextChange = output<string>();
    save = output<void>();
    onCancel = output<void>();
    setEditMode = output<FormEditMode>();
    aiGenerate = output<void>();

    saveTitle = computed<string>(() =>
        this.isAiMode()
            ? 'artemisApp.programmingExercise.generateExercise.generateEntire'
            : this.isImport()
              ? 'entity.action.import'
              : this.isCreation()
                ? 'entity.action.generate'
                : 'entity.action.save',
    );

    /** In AI mode the primary action is blocked until the instructor has written a brief AND the selected language is one the oracle can verify. */
    aiActionBlocked = computed<boolean>(() => this.isAiMode() && (!this.aiBriefPresent() || !this.aiLanguageSupported()));

    /** Explains why the AI action is disabled (empty brief vs. unsupported language), so the capability stays discoverable instead of silently dead. */
    aiActionTooltip = computed<string | undefined>(() => {
        if (!this.isAiMode()) {
            return undefined;
        }
        if (!this.aiLanguageSupported()) {
            return this.translateService.instant('artemisApp.programmingExercise.generateExercise.languageUnsupported');
        }
        if (!this.aiBriefPresent()) {
            return this.translateService.instant('artemisApp.programmingExercise.generateExercise.briefRequired');
        }
        return undefined;
    });

    /** The 3-way mode selector options (labels translated up front; the footer is not re-rendered on a mid-form language switch). */
    get modeOptions(): { label: string; value: FormEditMode }[] {
        return [
            { label: this.translateService.instant('artemisApp.programmingExercise.editMode.simple'), value: 'simple' },
            { label: this.translateService.instant('artemisApp.programmingExercise.editMode.advanced'), value: 'advanced' },
            { label: this.translateService.instant('artemisApp.programmingExercise.editMode.ai'), value: 'ai' },
        ];
    }

    onSwitchEditMode() {
        this.switchEditMode()?.();
    }

    onModeChange(mode: FormEditMode): void {
        this.setEditMode.emit(mode);
    }
}
