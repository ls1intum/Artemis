import { AfterViewInit, Component, OnDestroy, OnInit, computed, effect, input, model, output, signal, viewChild } from '@angular/core';
import { ControlContainer, FormsModule, NgForm, NgModel } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CustomNotIncludedInValidatorDirective } from 'app/foundation/validators/custom-not-included-in-validator.directive';
import { HelpIconComponent } from '../../components/help-icon/help-icon.component';

/**
 * @deprecated Use {@link TitleChannelNamePrimengComponent} instead.
 */
@Component({
    selector: 'jhi-title-channel-name',
    templateUrl: './title-channel-name.component.html',
    viewProviders: [{ provide: ControlContainer, useExisting: NgForm }],
    imports: [TranslateDirective, FormsModule, CustomNotIncludedInValidatorDirective, HelpIconComponent],
})
export class TitleChannelNameComponent implements AfterViewInit, OnDestroy, OnInit {
    title = model<string | undefined>(undefined);
    channelName = model<string | undefined>(undefined);
    channelNamePrefix = input<string | undefined>('');
    titlePattern = input<string>();
    hideTitleLabel = input<boolean>(false);
    hideChannelNameLabel = input<boolean>(false);
    titleHelpIconText = input<string>();
    channelNameHelpIconText = input<string>('artemisApp.programmingExercise.channelNameTooltip');
    emphasizeLabels = input<boolean>(false);
    minTitleLength = input<number>();
    initChannelName = input<boolean>(true);
    hideChannelName = input<boolean>();
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
    alreadyUsedTitles = input<Set<string>>(new Set());

    titleOnPageLoad = signal<string | undefined>(undefined);

    private readonly fieldTitle = viewChild<NgModel>('field_title');
    private readonly fieldChannelName = viewChild<NgModel>('field_channel_name');

    get field_title(): NgModel {
        return this.fieldTitle()!;
    }

    get field_channel_name(): NgModel {
        return this.fieldChannelName()!;
    }

    titleChange = output<string>();
    channelNameChange = output<string>();

    isValid = signal<boolean>(false);

    fieldTitleSubscription?: Subscription;
    fieldChannelNameSubscription?: Subscription;

    isChannelFieldDisplayed = computed(() => {
        return !this.hideChannelName() && (!this.isEditFieldDisplayedRecord() || this.isEditFieldDisplayedRecord()?.channelName);
    });

    constructor() {
        effect(() => {
            this.isEditFieldDisplayedRecord(); // triggers effect on change
            this.registerChangeListeners();
        });

        effect(
            function removeInitialTitleInEditFromForbiddenTitles() {
                if (this.titleOnPageLoad()) {
                    this.alreadyUsedTitles().delete(this.titleOnPageLoad());
                }
            }.bind(this),
        );
    }

    ngAfterViewInit() {
        this.registerChangeListeners();
    }

    ngOnInit(): void {
        if (this.initChannelName()) {
            // Defer updating the channel name into the next change detection cycle to avoid the
            // "NG0100: Expression has changed after it was checked" error
            setTimeout(() => {
                this.updateChannelName();
            });
        }

        this.titleOnPageLoad.set(this.title());
    }

    private registerChangeListeners() {
        this.fieldTitleSubscription?.unsubscribe();
        this.fieldChannelNameSubscription?.unsubscribe();
        this.fieldTitleSubscription = this.fieldTitle()?.valueChanges?.subscribe(() => this.calculateFormValid());
        this.fieldChannelNameSubscription = this.fieldChannelName()?.valueChanges?.subscribe(() => this.calculateFormValid());
    }

    ngOnDestroy() {
        this.fieldTitleSubscription?.unsubscribe();
        this.fieldChannelNameSubscription?.unsubscribe();
    }

    calculateFormValid(): void {
        const updatedFormValidValue = Boolean(this.fieldTitle()?.valid && (!this.isChannelFieldDisplayed() || this.fieldChannelName()?.valid));
        this.isValid.set(updatedFormValidValue);
    }

    updateTitle(newTitle: string) {
        this.title.set(newTitle);
        this.titleChange.emit(this.title() ?? '');
        this.updateChannelName();
    }

    updateChannelName() {
        this.formatChannelName((this.channelNamePrefix() ?? '') + (this.title() ?? ''), false, !!this.title());
    }

    /**
     * Formats a channel name by applying specific transformations based on the provided options.
     *
     * @param {string} newName - The new channel name to be formatted.
     * @param {boolean} [allowDuplicateHyphens=true] - Flag indicating whether duplicate hyphens should be allowed in the formatted name.
     * @param {boolean} [removeTrailingHyphens=false] - Flag indicating whether trailing hyphens should be removed from the formatted name.
     * @return {void} This method does not return a value but emits the formatted channel name.
     */
    formatChannelName(newName: string, allowDuplicateHyphens: boolean = true, removeTrailingHyphens: boolean = false): void {
        const REMOVE_SPECIAL_CHARACTERS_ALLOW_HYPHENS = /[^a-z0-9-]+/g;
        const REMOVE_SPECIAL_CHARACTERS_NO_HYPHENS = /[^a-z0-9]+/g;
        const TRAILING_HYPHENS = /-$/;
        const removeSpecialCharactersRegEx: RegExp = allowDuplicateHyphens ? REMOVE_SPECIAL_CHARACTERS_ALLOW_HYPHENS : REMOVE_SPECIAL_CHARACTERS_NO_HYPHENS;

        const nameWithoutSpecialCharacters = newName.toLowerCase().replaceAll(removeSpecialCharactersRegEx, '-');
        const formattedName = removeTrailingHyphens ? nameWithoutSpecialCharacters.replace(TRAILING_HYPHENS, '') : nameWithoutSpecialCharacters;

        this.channelName.set(formattedName.slice(0, 30));
        this.channelNameChange.emit(this.channelName() ?? '');
    }
}
