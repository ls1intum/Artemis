import { ChangeDetectionStrategy, Component, OnInit, WritableSignal, inject, signal } from '@angular/core';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { IdeSettingsService } from 'app/account/user/settings/ide-preferences/ide-settings.service';
import { Ide, ideEquals } from 'app/account/user/settings/ide-preferences/ide.model';

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
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        NgClass,
    ],
})
export class IdeSettingsComponent implements OnInit {
    private ideSettingsService = inject(IdeSettingsService);

    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    readonly PREDEFINED_IDE = signal<Ide[]>([{ name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }]);

    programmingLanguageToIde: WritableSignal<Map<ProgrammingLanguage, Ide>> = signal(new Map([[ProgrammingLanguage.EMPTY, this.PREDEFINED_IDE()[0]]]));

    readonly assignedProgrammingLanguages = signal<ProgrammingLanguage[]>([]);
    // languages that have no IDE assigned yet
    readonly remainingProgrammingLanguages = signal<ProgrammingLanguage[]>(Object.values(ProgrammingLanguage).filter((x) => x !== ProgrammingLanguage.EMPTY));

    ngOnInit() {
        this.ideSettingsService.loadPredefinedIdes().subscribe((predefinedIde) => {
            this.PREDEFINED_IDE.set(predefinedIde);
        });

        this.ideSettingsService.loadIdePreferences(true).then((programmingLanguageToIdeMap) => {
            if (!programmingLanguageToIdeMap.has(ProgrammingLanguage.EMPTY)) {
                programmingLanguageToIdeMap.set(ProgrammingLanguage.EMPTY, this.PREDEFINED_IDE()[0]);
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
        });
    }

    addProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, this.PREDEFINED_IDE()[0]).subscribe((ide) => {
            this.programmingLanguageToIde.update((map) => new Map(map.set(programmingLanguage, ide)));

            this.assignedProgrammingLanguages.update((languages) => [...languages, programmingLanguage]);
            this.remainingProgrammingLanguages.update((languages) => languages.filter((x) => x !== programmingLanguage));
        });
    }

    changeIde(programmingLanguage: ProgrammingLanguage, ide: Ide) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, ide).subscribe((ide) => {
            this.programmingLanguageToIde.update((map) => new Map(map.set(programmingLanguage, ide)));
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

    isIdeOfProgrammingLanguage(programmingLanguage: ProgrammingLanguage, ide: Ide): boolean {
        return ideEquals(this.programmingLanguageToIde().get(programmingLanguage), ide);
    }
}
