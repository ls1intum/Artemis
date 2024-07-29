import { Component, OnInit } from '@angular/core';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { Ide } from 'app/shared/user-settings/default-ide/ide.model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { IdeSettingsService } from 'app/shared/user-settings/default-ide/ide-settings.service';

@Component({
    selector: 'jhi-ide-preferences',
    templateUrl: './ide-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class IdeSettingsComponent implements OnInit {
    programmingLanguageToDeepLink: Map<ProgrammingLanguage, Ide>;
    private showDropdown = false;

    constructor(private ideSettingsService: IdeSettingsService) {}

    ngOnInit() {
        this.ideSettingsService.loadIdePreferences().subscribe((res) => {
            this.programmingLanguageToDeepLink = new Map(res.body!.map((x) => [x.programmingLanguage, x.ide]));
            if (this.programmingLanguageToDeepLink.size === 0) {
                new Map([[ProgrammingLanguage.EMPTY, PREDEFINED_IDE[0]]]);
            }
        });
        return;
    }

    addProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, PREDEFINED_IDE[0]).subscribe((res) => {
            this.programmingLanguageToDeepLink.set(res.body!.programmingLanguage, res.body!.ide);
        });
    }

    changeIde(programmingLanguage: ProgrammingLanguage, ide: Ide) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, ide).subscribe((res) => {
            this.programmingLanguageToDeepLink.set(res.body!.programmingLanguage, res.body!.ide);
        });
    }

    removeProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.deleteIdePreference(programmingLanguage).subscribe(() => {
            this.programmingLanguageToDeepLink.delete(programmingLanguage);
        });
    }

    getCustomizedProgrammingLanguages(): ProgrammingLanguage[] {
        return Array.from(this.programmingLanguageToDeepLink.keys()).filter((x: ProgrammingLanguage) => x !== ProgrammingLanguage.EMPTY);
    }

    getRemainingProgrammingLanguages(): ProgrammingLanguage[] {
        const selected = Array.from(this.programmingLanguageToDeepLink.keys());
        const allLanguages = Object.values(ProgrammingLanguage);
        return Array.from(allLanguages.filter((x) => !selected.includes(x)));
    }

    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly PREDEFINED_IDE = PREDEFINED_IDE;
    protected readonly faPlus = faPlus;
}

export const PREDEFINED_IDE: Ide[] = [
    { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' },
    {
        name: 'IntelliJ',
        deepLink: 'jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}',
    },
    { name: 'Eclipse', deepLink: 'eclipse://clone?repo={cloneUrl}' },
    {
        name: 'PyCharm',
        deepLink: 'jetbrains://pycharm/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}',
    },
    {
        name: 'CLion',
        deepLink: 'jetbrains://clion/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}',
    },
    { name: 'XCode', deepLink: 'xcode://clone?repo={cloneUrl}' },
];
