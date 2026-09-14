import { Component, computed, inject, model } from '@angular/core';
import { APOLLON_SHORTCUTS, type ApollonShortcutCombo, type ApollonShortcutId, shortcutKeyName } from '@tumaet/apollon';
import { TumUiDialogComponent, TumUiTabComponent, TumUiTabListComponent, TumUiTabPanelComponent, TumUiTabPanelsComponent, TumUiTabsComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { OsDetectorService } from 'app/core/navbar/global-search/services/os-detector.service';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

type ShortcutGroup = 'editing' | 'selection' | 'view';

const SHORTCUT_GROUPS: Record<ApollonShortcutId, ShortcutGroup> = {
    'select-all': 'selection',
    'clear-selection': 'selection',
    delete: 'editing',
    copy: 'editing',
    cut: 'editing',
    paste: 'editing',
    duplicate: 'editing',
    'move-selection': 'selection',
    undo: 'editing',
    redo: 'editing',
    'zoom-in': 'view',
    'zoom-out': 'view',
    'reset-zoom': 'view',
    'fit-view': 'view',
    'zoom-to-selection': 'view',
};

interface HelpWalkthrough {
    readonly topic: 'createElement' | 'createRelationship' | 'updateElement' | 'moveElement' | 'resizeElement' | 'reconnectRelationship';
    readonly imageName: string;
    readonly width: number;
    readonly height: number;
}

const WALKTHROUGHS: readonly HelpWalkthrough[] = [
    { topic: 'createElement', imageName: 'create-element', width: 1120, height: 596 },
    { topic: 'createRelationship', imageName: 'create-relationship', width: 1120, height: 480 },
    { topic: 'updateElement', imageName: 'update-element', width: 1080, height: 1080 },
    { topic: 'moveElement', imageName: 'move-element', width: 800, height: 560 },
    { topic: 'resizeElement', imageName: 'resize-element', width: 800, height: 500 },
    { topic: 'reconnectRelationship', imageName: 'reconnect-relationship', width: 1480, height: 680 },
];

@Component({
    selector: 'jhi-modeling-editor-help',
    templateUrl: './modeling-editor-help.component.html',
    styleUrls: ['./modeling-editor-help.component.scss'],
    imports: [
        TumUiDialogComponent,
        TumUiTabsComponent,
        TumUiTabListComponent,
        TumUiTabComponent,
        TumUiTabPanelsComponent,
        TumUiTabPanelComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class ModelingEditorHelpComponent {
    private readonly osDetector = inject(OsDetectorService);
    private readonly translateService = inject(TranslateService);
    private readonly themeService = inject(ThemeService);

    visible = model(false);

    protected readonly walkthroughs = computed(() => {
        const theme = this.themeService.currentTheme() === Theme.DARK ? 'dark' : 'light';
        return WALKTHROUGHS.map((walkthrough) => cloneWith(walkthrough, { image: `/content/images/modeling-help/${walkthrough.imageName}-${theme}.png` }));
    });
    protected readonly additionalTopics = ['select', 'duplicate', 'deleteElement', 'undo'] as const;
    protected readonly shortcuts = APOLLON_SHORTCUTS;
    protected readonly actionKeyLabel = this.osDetector.actionKeyLabel;
    protected readonly shortcutLabelPrefix = 'artemisApp.modelingEditor.helpModal.shortcuts.actions.';

    protected readonly shortcutGroups = [
        {
            label: 'artemisApp.modelingEditor.helpModal.shortcuts.groups.editing',
            shortcuts: this.shortcuts.filter(({ id }) => SHORTCUT_GROUPS[id] === 'editing'),
        },
        {
            label: 'artemisApp.modelingEditor.helpModal.shortcuts.groups.selection',
            shortcuts: this.shortcuts.filter(({ id }) => SHORTCUT_GROUPS[id] === 'selection'),
        },
        {
            label: 'artemisApp.modelingEditor.helpModal.shortcuts.groups.view',
            shortcuts: this.shortcuts.filter(({ id }) => SHORTCUT_GROUPS[id] === 'view'),
        },
    ] as const;

    protected formatCombo(combo: ApollonShortcutCombo): readonly string[] {
        const keys: string[] = [];
        if (combo.alt) {
            keys.push(this.osDetector.isMac() ? '⌥' : 'Alt');
        }
        if (combo.shift) {
            keys.push(this.osDetector.isMac() ? '⇧' : 'Shift');
        }
        if (combo.mod) {
            keys.push(this.actionKeyLabel());
        }
        keys.push(this.formatKey(shortcutKeyName(combo)));
        return keys;
    }

    protected displayCombos(id: ApollonShortcutId, combos: readonly ApollonShortcutCombo[]): readonly ApollonShortcutCombo[] {
        if (id === 'delete' || id === 'redo' || id === 'move-selection') {
            return combos;
        }
        return combos.slice(0, 1);
    }

    protected shortcutTranslationKey(id: ApollonShortcutId): string {
        return `${this.shortcutLabelPrefix}${id}`;
    }

    private formatKey(key: string): string {
        const keyNames: Record<string, string> = {
            ArrowUp: '↑',
            ArrowDown: '↓',
            ArrowLeft: '←',
            ArrowRight: '→',
            Escape: 'Esc',
            Delete: this.translateService.instant('artemisApp.modelingEditor.helpModal.shortcuts.keys.delete'),
            Backspace: this.translateService.instant('artemisApp.modelingEditor.helpModal.shortcuts.keys.backspace'),
        };
        return keyNames[key] ?? key.toUpperCase();
    }
}
