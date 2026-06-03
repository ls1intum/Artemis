import { Injectable, effect, signal } from '@angular/core';

export type ExerciseRowVariant = 'compact' | 'columnar' | 'table';
export type ActionButtonVariant = 'icon-only' | 'text-and-icon' | 'ellipsis';
export type AddExerciseVariant = 'none' | 'inline' | 'slim' | 'modal-split' | 'modal-unified';

const KEY_ROW = 'exerciseMgmt.devSettings.exerciseRowVariant';
const KEY_MASS_ACTIONS = 'exerciseMgmt.devSettings.massActions';
const KEY_ADD_EXERCISE = 'exerciseMgmt.devSettings.addExerciseVariant';
const KEY_ACTION_BUTTONS = 'exerciseMgmt.devSettings.actionButtonVariant';

@Injectable({ providedIn: 'root' })
export class ExerciseManagementDevSettingsService {
    readonly exerciseRowVariant = signal<ExerciseRowVariant>((localStorage.getItem(KEY_ROW) as ExerciseRowVariant) ?? 'columnar');
    readonly massActionsEnabled = signal<boolean>(localStorage.getItem(KEY_MASS_ACTIONS) === 'true');
    readonly addExerciseVariant = signal<AddExerciseVariant>((localStorage.getItem(KEY_ADD_EXERCISE) as AddExerciseVariant) ?? 'none');
    readonly actionButtonVariant = signal<ActionButtonVariant>((localStorage.getItem(KEY_ACTION_BUTTONS) as ActionButtonVariant) ?? 'icon-only');

    constructor() {
        effect(() => localStorage.setItem(KEY_ROW, this.exerciseRowVariant()));
        effect(() => localStorage.setItem(KEY_MASS_ACTIONS, String(this.massActionsEnabled())));
        effect(() => localStorage.setItem(KEY_ADD_EXERCISE, this.addExerciseVariant()));
        effect(() => localStorage.setItem(KEY_ACTION_BUTTONS, this.actionButtonVariant()));
    }

    setExerciseRowVariant(variant: ExerciseRowVariant): void {
        this.exerciseRowVariant.set(variant);
    }

    setMassActionsEnabled(enabled: boolean): void {
        this.massActionsEnabled.set(enabled);
    }

    setAddExerciseVariant(variant: AddExerciseVariant): void {
        this.addExerciseVariant.set(variant);
    }

    setActionButtonVariant(variant: ActionButtonVariant): void {
        this.actionButtonVariant.set(variant);
    }
}
