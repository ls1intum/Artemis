import { Component, inject, model } from '@angular/core';
import { APOLLON_SHORTCUTS, type ApollonShortcutCombo, type ApollonShortcutId, shortcutKeyName } from '@tumaet/apollon';
import { TumUiDialogComponent, TumUiTabComponent, TumUiTabListComponent, TumUiTabPanelComponent, TumUiTabPanelsComponent, TumUiTabsComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { OsDetectorService } from 'app/core/navbar/global-search/services/os-detector.service';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

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
    readonly topic: 'createElement' | 'createRelationship' | 'updateElement' | 'moveElement';
    readonly image: string;
}

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

    visible = model(false);

    protected readonly walkthroughs: readonly HelpWalkthrough[] = [
        { topic: 'createElement', image: '/content/images/apollon-help-node-creation.png' },
        { topic: 'createRelationship', image: '/content/images/apollon-help-edge-creation.png' },
        { topic: 'updateElement', image: '/content/images/apollon-help-node-edit.png' },
        { topic: 'moveElement', image: '/content/images/apollon-help-node-move.png' },
    ];
    protected readonly additionalTopics = ['select', 'resizeElement', 'reconnectRelationship', 'duplicate', 'deleteElement', 'undo'] as const;
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
