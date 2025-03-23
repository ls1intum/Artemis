import { AfterViewChecked, Component, EventEmitter, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { TeamAssignmentConfig } from 'app/entities/team-assignment-config.model';
import { cloneDeep } from 'lodash-es';
import { Exercise, ExerciseMode } from 'app/entities/exercise.model';
import { ModePickerOption } from 'app/exercise/mode-picker/mode-picker.component';
import { FormsModule, NgModel } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ModePickerComponent } from 'app/exercise/mode-picker/mode-picker.component';
import { KeyValuePipe } from '@angular/common';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';

@Component({
    selector: 'jhi-team-config-form-group',
    templateUrl: './team-config-form-group.component.html',
    styleUrls: ['./team-config-form-group.component.scss'],
    imports: [TranslateDirective, HelpIconComponent, ModePickerComponent, FormsModule, KeyValuePipe, RemoveKeysPipe],
})
export class TeamConfigFormGroupComponent implements AfterViewChecked, OnDestroy, OnInit {
    readonly INDIVIDUAL = ExerciseMode.INDIVIDUAL;
    readonly TEAM = ExerciseMode.TEAM;

    @Input() exercise: Exercise;
    @Input() isImport: boolean;

    @ViewChild('minTeamSize') minTeamSizeField?: NgModel;
    @ViewChild('maxTeamSize') maxTeamsizeField?: NgModel;

    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    config: TeamAssignmentConfig;
    readonly modePickerOptions: ModePickerOption<ExerciseMode>[] = [
        {
            value: ExerciseMode.INDIVIDUAL,
            labelKey: 'artemisApp.exercise.modes.individual',
            btnClass: 'btn-secondary',
        },
        {
            value: ExerciseMode.TEAM,
            labelKey: 'artemisApp.exercise.modes.team',
            btnClass: 'btn-info',
        },
    ];

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {
        this.config = this.exercise.teamAssignmentConfig || new TeamAssignmentConfig();
        this.calculateFormValid();
    }

    ngAfterViewChecked() {
        if (!(this.minTeamSizeField?.valueChanges as EventEmitter<number>)?.observed) {
            this.inputFieldSubscriptions.push(this.minTeamSizeField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        }
        if (!(this.maxTeamsizeField?.valueChanges as EventEmitter<number>)?.observed) {
            this.inputFieldSubscriptions.push(this.maxTeamsizeField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        }
    }

    ngOnDestroy() {
        for (const subscription of this.inputFieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    calculateFormValid() {
        this.formValid = Boolean(!this.exercise.mode || this.exercise.mode === ExerciseMode.INDIVIDUAL || (this.maxTeamsizeField?.valid && this.minTeamSizeField?.valid));
        this.formValidChanges.next(this.formValid);
    }

    get changeExerciseModeDisabled(): boolean {
        // Should be disabled if exercise is present (-> edit menu), but not if menu is shown during import
        // (old exercise id is present). Should also not be present for exam exercises.
        return (!this.isImport && Boolean(this.exercise.id)) || !!this.exercise.exerciseGroup;
    }

    /**
     * Hook to indicate that exercise mode changed
     * @param {ExerciseMode} mode - Exercise mode
     */
    onExerciseModeChange(mode: ExerciseMode) {
        this.exercise.mode = mode;
        if (mode === ExerciseMode.TEAM) {
            this.applyCurrentConfig();
        } else {
            this.exercise.teamAssignmentConfig = undefined;
        }
        this.calculateFormValid();
    }

    /**
     * Update minimum number of team members
     * @param {number} minTeamSize - minimum number of team members
     */
    updateMinTeamSize(minTeamSize: number) {
        this.config.maxTeamSize = Math.max(this.config.maxTeamSize!, minTeamSize);
        this.applyCurrentConfig();
    }

    /**
     * Update maximum number of team members
     * @param {number} maxTeamSize - maximum number of team members
     */
    updateMaxTeamSize(maxTeamSize: number) {
        this.config.minTeamSize = Math.min(this.config.minTeamSize!, maxTeamSize);
        this.applyCurrentConfig();
    }

    private applyCurrentConfig() {
        this.exercise.teamAssignmentConfig = cloneDeep(this.config);
    }
}
