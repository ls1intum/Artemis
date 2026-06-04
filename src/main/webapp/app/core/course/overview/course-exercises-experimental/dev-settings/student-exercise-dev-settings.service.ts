import { Injectable, effect, signal } from '@angular/core';

/** Which version of the experimental student exercise overview to render. New versions are added here. */
export type StudentExerciseViewVersion = 'grouped' | 'flat';

/**
 * How a group's title is shown in the grouped version: as a card tile, a plain heading, a plain
 * heading with a "Pick 1 of X" helper line, or that heading plus a select checkbox per exercise.
 */
export type GroupHeaderStyle = 'card' | 'label' | 'label-hint' | 'label-select';

const KEY_VERSION = 'studentExercises.devSettings.viewVersion';
const KEY_GROUP_HEADER = 'studentExercises.devSettings.groupHeaderStyle';

/**
 * Dev-only settings for the experimental student exercise overview, mirroring the instructor
 * {@link ExerciseManagementDevSettingsService}. Settings are signals persisted to localStorage so a
 * chosen version survives reloads while iterating on different designs.
 */
@Injectable({ providedIn: 'root' })
export class StudentExerciseDevSettingsService {
    readonly viewVersion = signal<StudentExerciseViewVersion>((localStorage.getItem(KEY_VERSION) as StudentExerciseViewVersion) ?? 'grouped');
    readonly groupHeaderStyle = signal<GroupHeaderStyle>((localStorage.getItem(KEY_GROUP_HEADER) as GroupHeaderStyle) ?? 'card');

    constructor() {
        effect(() => localStorage.setItem(KEY_VERSION, this.viewVersion()));
        effect(() => localStorage.setItem(KEY_GROUP_HEADER, this.groupHeaderStyle()));
    }

    setViewVersion(version: StudentExerciseViewVersion): void {
        this.viewVersion.set(version);
    }

    setGroupHeaderStyle(style: GroupHeaderStyle): void {
        this.groupHeaderStyle.set(style);
    }
}
