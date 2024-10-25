import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, WritableSignal, signal } from '@angular/core';
import { Subscription } from 'rxjs';
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
export class IdeSettingsComponent implements OnInit, OnDestroy {
    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    PREDEFINED_IDE: Ide[];

    programmingLanguageToIde: WritableSignal<Map<ProgrammingLanguage, Ide>>;

    assignedProgrammingLanguages: ProgrammingLanguage[] = [];
    // languages that have no IDE assigned yet
    remainingProgrammingLanguages: ProgrammingLanguage[] = Object.values(ProgrammingLanguage).filter((x) => x !== ProgrammingLanguage.EMPTY);

    private subscription: Subscription = new Subscription();

    constructor(private ideSettingsService: IdeSettingsService) {
        this.PREDEFINED_IDE = this.ideSettingsService.getPredefinedIdes();
        this.programmingLanguageToIde = signal(new Map([[ProgrammingLanguage.EMPTY, this.PREDEFINED_IDE[0]]]));
    }

    ngOnInit() {
        const predefinedIdesSubscription = this.ideSettingsService.loadPredefinedIdes().subscribe((ides) => {
            this.PREDEFINED_IDE = ides;
        });
        this.subscription.add(predefinedIdesSubscription);

        const idePreferencesSubscription = this.ideSettingsService.getIdePreferences().subscribe((programmingLanguageToIdeMap) => {
            this.programmingLanguageToIde.set(programmingLanguageToIdeMap);

            // initialize assigned programming languages
            this.assignedProgrammingLanguages = Array.from(programmingLanguageToIdeMap.keys()).filter((x: ProgrammingLanguage) => x !== ProgrammingLanguage.EMPTY);

            // initialize remaining programming languages
            this.remainingProgrammingLanguages = Array.from(
                Object.values(ProgrammingLanguage).filter((x) => !this.assignedProgrammingLanguages.includes(x) && x !== ProgrammingLanguage.EMPTY),
            );
        });
        this.subscription.add(idePreferencesSubscription);
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    addProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, this.PREDEFINED_IDE[0]).subscribe();
    }

    changeIde(programmingLanguage: ProgrammingLanguage, ide: Ide) {
        this.ideSettingsService.saveIdePreference(programmingLanguage, ide).subscribe();
    }

    removeProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        this.ideSettingsService.deleteIdePreference(programmingLanguage).subscribe();
    }

    isIdeOfProgrammingLanguage(programmingLanguage: ProgrammingLanguage, ide: Ide): boolean {
        return ideEquals(this.programmingLanguageToIde().get(programmingLanguage), ide);
    }
}
