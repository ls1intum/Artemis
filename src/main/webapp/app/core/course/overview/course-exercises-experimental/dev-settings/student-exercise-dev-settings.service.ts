import { Injectable, effect, signal } from '@angular/core';

/** Which version of the experimental student exercise overview to render. New versions are added here. */
export type StudentExerciseViewVersion = 'grouped' | 'flat';

/** How an exercise group is presented in the sidebar. */
export type GroupSidebarStyle = 'clickable' | 'connected' | 'select';

/** What happens when a clickable exercise group is clicked in the sidebar — i.e. which group page opens. */
export type GroupClickAction = 'rows' | 'tiles' | 'exercise-page';

const KEY_VERSION = 'studentExercises.devSettings.viewVersion';
const KEY_SIDEBAR_STYLE = 'studentExercises.devSettings.groupSidebarStyle';
const KEY_CLICK_ACTION = 'studentExercises.devSettings.groupClickAction';

function readGroupSidebarStyle(): GroupSidebarStyle {
    const stored = localStorage.getItem(KEY_SIDEBAR_STYLE);
    return stored === 'select' || stored === 'connected' ? stored : 'clickable';
}

function readGroupClickAction(): GroupClickAction {
    const stored = localStorage.getItem(KEY_CLICK_ACTION);
    return stored === 'tiles' || stored === 'exercise-page' ? stored : 'rows';
}

/**
 * Dev-only settings for the experimental student exercise overview, mirroring the instructor
 * {@link ExerciseManagementDevSettingsService}. Settings are signals persisted to localStorage so a
 * chosen version survives reloads while iterating on different designs. Group behaviour is split into
 * two independent settings: how the group looks in the sidebar, and what clicking it does.
 */
@Injectable({ providedIn: 'root' })
export class StudentExerciseDevSettingsService {
    readonly viewVersion = signal<StudentExerciseViewVersion>((localStorage.getItem(KEY_VERSION) as StudentExerciseViewVersion) ?? 'grouped');
    readonly groupSidebarStyle = signal<GroupSidebarStyle>(readGroupSidebarStyle());
    readonly groupClickAction = signal<GroupClickAction>(readGroupClickAction());

    /** Whether the experimental student exercise view is currently shown (drives the title-bar gear). */
    readonly active = signal(false);
    /** Whether the dev-settings modal is open (shared so the title-bar gear can open the modal). */
    readonly settingsVisible = signal(false);

    constructor() {
        effect(() => localStorage.setItem(KEY_VERSION, this.viewVersion()));
        effect(() => localStorage.setItem(KEY_SIDEBAR_STYLE, this.groupSidebarStyle()));
        effect(() => localStorage.setItem(KEY_CLICK_ACTION, this.groupClickAction()));
    }

    setViewVersion(version: StudentExerciseViewVersion): void {
        this.viewVersion.set(version);
    }

    setGroupSidebarStyle(style: GroupSidebarStyle): void {
        this.groupSidebarStyle.set(style);
    }

    setGroupClickAction(action: GroupClickAction): void {
        this.groupClickAction.set(action);
    }
}
