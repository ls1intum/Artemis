import { ChangeDetectionStrategy, Component, OnInit, WritableSignal, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { faPlus, faSpinner, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TumUiButtonComponent,
    TumUiButtonDirective,
    TumUiListComponent,
    TumUiListItemDirective,
    TumUiMenuComponent,
    TumUiMenuItemDirective,
    TumUiMenuTriggerDirective,
    TumUiSelectButtonComponent,
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { IdeSettingsService } from 'app/account/user/settings/ide-preferences/ide-settings.service';
import { Ide } from 'app/account/user/settings/ide-preferences/ide.model';

@Component({
    selector: 'jhi-ide-preferences',
    templateUrl: './ide-settings.component.html',
    styleUrls: ['./ide-settings.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        HelpIconComponent,
        NgTemplateOutlet,
        FaIconComponent,
        FormsModule,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiListComponent,
        TumUiListItemDirective,
        TumUiMenuComponent,
        TumUiMenuItemDirective,
        TumUiMenuTriggerDirective,
        TumUiSelectButtonComponent,
        ArtemisTranslatePipe,
    ],
})
export class IdeSettingsComponent implements OnInit {
    private ideSettingsService = inject(IdeSettingsService);

    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly faSpinner = faSpinner;
    // Hides the IDE button rows until the predefined IDEs and the saved preferences have loaded, so the
    // selection is not first rendered on the VS Code default and then jumped to the actually saved IDE.
    readonly isLoading = signal<boolean>(true);
    readonly PREDEFINED_IDE = signal<Ide[]>([{ name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }]);

    programmingLanguageToIde: WritableSignal<Map<ProgrammingLanguage, Ide>> = signal(new Map([[ProgrammingLanguage.EMPTY, this.PREDEFINED_IDE()[0]]]));

    readonly assignedProgrammingLanguages = signal<ProgrammingLanguage[]>([]);
    // languages that have no IDE assigned yet
    readonly remainingProgrammingLanguages = signal<ProgrammingLanguage[]>(Object.values(ProgrammingLanguage).filter((x) => x !== ProgrammingLanguage.EMPTY));

    async ngOnInit() {
        try {
            // Load the predefined IDEs and the saved preferences together and only then render, so the
            // selection is shown correctly from the start instead of flashing on the VS Code default.
            const [predefinedIdes, programmingLanguageToIdeMap] = await Promise.all([
                firstValueFrom(this.ideSettingsService.loadPredefinedIdes()),
                this.ideSettingsService.loadIdePreferences(true),
            ]);

            this.PREDEFINED_IDE.set(predefinedIdes);

            if (!programmingLanguageToIdeMap.has(ProgrammingLanguage.EMPTY)) {
                programmingLanguageToIdeMap.set(ProgrammingLanguage.EMPTY, predefinedIdes[0]);
            }

            this.programmingLanguageToIde.set(programmingLanguageToIdeMap);

            // initialize assigned programming languages
            const assignedProgrammingLanguages: ProgrammingLanguage[] = Array.from(programmingLanguageToIdeMap.keys()).filter(
                (x: ProgrammingLanguage) => x !== ProgrammingLanguage.EMPTY,
            );
            this.assignedProgrammingLanguages.set(assignedProgrammingLanguages);

            // initialize remaining programming languages
            this.remainingProgrammingLanguages.set(
                Array.from(Object.values(ProgrammingLanguage).filter((x) => !assignedProgrammingLanguages.includes(x) && x !== ProgrammingLanguage.EMPTY)),
            );
        } finally {
            this.isLoading.set(false);
        }
    }

    addProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, this.PREDEFINED_IDE()[0]).subscribe((ide) => {
            this.programmingLanguageToIde.update((map) => new Map(map.set(programmingLanguage, ide)));

            this.assignedProgrammingLanguages.update((languages) => [...languages, programmingLanguage]);
            this.remainingProgrammingLanguages.update((languages) => languages.filter((x) => x !== programmingLanguage));
        });
    }

    changeIde(programmingLanguage: ProgrammingLanguage, ide: Ide) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, ide).subscribe({
            next: (savedIde) => {
                this.programmingLanguageToIde.update((map) => new Map(map.set(programmingLanguage, savedIde)));
            },
            // The control holds the option the user clicked, and a one-way binding only writes back when the
            // bound value changes. Republish the map so the selection snaps back to what is actually saved.
            error: () => this.programmingLanguageToIde.update((map) => new Map(map)),
        });
    }

    removeProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.deleteIdePreference(programmingLanguage).subscribe(() => {
            const programmingLanguageToIdeMap: Map<ProgrammingLanguage, Ide> = new Map(this.programmingLanguageToIde());
            programmingLanguageToIdeMap.delete(programmingLanguage);

            this.programmingLanguageToIde.set(programmingLanguageToIdeMap);

            this.remainingProgrammingLanguages.update((languages) => [...languages, programmingLanguage]);
            this.assignedProgrammingLanguages.update((languages) => languages.filter((x) => x !== programmingLanguage));
        });
    }

    /**
     * Applies a selection made by deep link, which is what the option list writes: two `Ide` objects for the
     * same IDE are separate instances, so they are matched on their deep link rather than by identity.
     */
    changeIdeByDeepLink(programmingLanguage: ProgrammingLanguage, deepLink: unknown) {
        const ide = this.PREDEFINED_IDE().find((candidate) => candidate.deepLink === deepLink);
        if (ide) {
            this.changeIde(programmingLanguage, ide);
        }
    }
}
