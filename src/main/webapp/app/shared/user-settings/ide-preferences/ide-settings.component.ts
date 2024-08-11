import { Component, OnInit } from '@angular/core';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { Ide, ideEqual } from 'app/shared/user-settings/ide-preferences/ide.model';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { IdeSettingsService, PREDEFINED_IDE } from 'app/shared/user-settings/ide-preferences/ide-settings.service';

@Component({
    selector: 'jhi-ide-preferences',
    templateUrl: './ide-settings.component.html',
    styleUrls: ['./ide-settings.scss'],
})
export class IdeSettingsComponent implements OnInit {
    programmingLanguageToIde: Map<ProgrammingLanguage, Ide> = new Map([[ProgrammingLanguage.EMPTY, PREDEFINED_IDE[0]]]);

    constructor(private ideSettingsService: IdeSettingsService) {}

    ngOnInit() {
        this.ideSettingsService.loadIdePreferences().subscribe((programmingLanguageToIde) => {
            if (programmingLanguageToIde.size !== 0) {
                this.programmingLanguageToIde = programmingLanguageToIde;
            }
        });
    }

    addProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, PREDEFINED_IDE[0]).subscribe((ide) => {
            this.programmingLanguageToIde.set(programmingLanguage, ide);
        });
    }

    changeIde(programmingLanguage: ProgrammingLanguage, ide: Ide) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, ide).subscribe((ide) => {
            this.programmingLanguageToIde.set(programmingLanguage, ide);
        });
    }

    removeProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.deleteIdePreference(programmingLanguage);
        this.programmingLanguageToIde.delete(programmingLanguage);
    }

    // returns all programming languages that have ide preference assigned EXCEPT ProgrammingLanguage.EMPTY
    getCustomizedProgrammingLanguages(): ProgrammingLanguage[] {
        return Array.from(this.programmingLanguageToIde.keys()).filter((x: ProgrammingLanguage) => x !== ProgrammingLanguage.EMPTY);
    }

    // returns all programming languages that have no ide preference assigned
    getRemainingProgrammingLanguages(): ProgrammingLanguage[] {
        const selected = Array.from(this.programmingLanguageToIde.keys());
        const allLanguages = Object.values(ProgrammingLanguage);
        return Array.from(allLanguages.filter((x) => !selected.includes(x)));
    }

    isIdeOfProgrammingLanguage(programmingLanguage: ProgrammingLanguage, ide: Ide) {
        return ideEqual(this.programmingLanguageToIde.get(programmingLanguage), ide);
    }

    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly PREDEFINED_IDE = PREDEFINED_IDE;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
}
