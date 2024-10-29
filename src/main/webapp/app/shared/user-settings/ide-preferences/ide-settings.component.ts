import { ChangeDetectionStrategy, Component, OnInit, WritableSignal, signal } from '@angular/core';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { Ide, ideEquals } from 'app/shared/user-settings/ide-preferences/ide.model';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { IdeSettingsService } from 'app/shared/user-settings/ide-preferences/ide-settings.service';

@Component({
    selector: 'jhi-ide-preferences',
    templateUrl: './ide-settings.component.html',
    styleUrls: ['./ide-settings.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IdeSettingsComponent implements OnInit {
    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    PREDEFINED_IDE: Ide[] = [{ name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }];

    programmingLanguageToIde: WritableSignal<Map<ProgrammingLanguage, Ide>> = signal(new Map([[ProgrammingLanguage.EMPTY, this.PREDEFINED_IDE[0]]]));

    assignedProgrammingLanguages: ProgrammingLanguage[] = [];
    // languages that have no IDE assigned yet
    remainingProgrammingLanguages: ProgrammingLanguage[] = Object.values(ProgrammingLanguage).filter((x) => x !== ProgrammingLanguage.EMPTY);

    constructor(private ideSettingsService: IdeSettingsService) {}

    ngOnInit() {
        this.ideSettingsService.loadPredefinedIdes().subscribe((predefinedIde) => {
            this.PREDEFINED_IDE = predefinedIde;
        });

        this.ideSettingsService.loadIdePreferences(true).then((programmingLanguageToIdeMap) => {
            if (!programmingLanguageToIdeMap.has(ProgrammingLanguage.EMPTY)) {
                programmingLanguageToIdeMap.set(ProgrammingLanguage.EMPTY, this.PREDEFINED_IDE[0]);
            }

            this.programmingLanguageToIde.set(programmingLanguageToIdeMap);

            // initialize assigned programming languages
            this.assignedProgrammingLanguages = Array.from(programmingLanguageToIdeMap.keys()).filter((x: ProgrammingLanguage) => x !== ProgrammingLanguage.EMPTY);

            // initialize remaining programming languages
            this.remainingProgrammingLanguages = Array.from(
                Object.values(ProgrammingLanguage).filter((x) => !this.assignedProgrammingLanguages.includes(x) && x !== ProgrammingLanguage.EMPTY),
            );
        });
    }

    addProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, this.PREDEFINED_IDE[0]).subscribe((ide) => {
            this.programmingLanguageToIde.update((map) => new Map(map.set(programmingLanguage, ide)));

            this.assignedProgrammingLanguages.push(programmingLanguage);
            this.remainingProgrammingLanguages = this.remainingProgrammingLanguages.filter((x) => x !== programmingLanguage);
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

            this.remainingProgrammingLanguages.push(programmingLanguage);
            this.assignedProgrammingLanguages = this.assignedProgrammingLanguages.filter((x) => x !== programmingLanguage);
        });
    }

    isIdeOfProgrammingLanguage(programmingLanguage: ProgrammingLanguage, ide: Ide): boolean {
        return ideEquals(this.programmingLanguageToIde().get(programmingLanguage), ide);
    }
}
