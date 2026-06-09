import { Injectable, effect, signal } from '@angular/core';

export type CompetencyExerciseViewVariant = 'default' | 'group-card' | 'group-card-v2' | 'grouped';

const KEY = 'competency.detail.exerciseViewVariant';

@Injectable({ providedIn: 'root' })
export class CompetencyDetailDevSettingsService {
    readonly exerciseViewVariant = signal<CompetencyExerciseViewVariant>((localStorage.getItem(KEY) as CompetencyExerciseViewVariant) ?? 'default');

    constructor() {
        effect(() => localStorage.setItem(KEY, this.exerciseViewVariant()));
    }

    setExerciseViewVariant(variant: CompetencyExerciseViewVariant): void {
        this.exerciseViewVariant.set(variant);
    }
}
