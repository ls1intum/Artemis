import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Observable } from 'rxjs';
import { EntitySummary } from 'app/shared-ui/delete-dialog/delete-dialog.model';

/** The kit button severities the action buttons use (a subset of `TumUiButtonSeverity`). */
export type ActionSeverity = 'primary' | 'info' | 'success' | 'warn' | 'danger';

/** Everything a `[jhiDeleteButton]` action needs, gathered here so the bar can render it from data alone. */
export interface DeleteActionConfig {
    entityTitle: string;
    deleteQuestion: string;
    deleteConfirmationText: string;
    entitySummaryTitle?: string;
    fetchEntitySummary?: Observable<EntitySummary>;
    translateValues?: { [key: string]: unknown };
    additionalChecks?: { [key: string]: string };
    dialogError: Observable<string>;
    onDelete: (event: { [key: string]: boolean }) => void;
}

/** A single collapsible action rendered in the {@link ExerciseActionBarComponent}'s row or its ellipsis overflow menu. */
export interface ActionItem {
    id: string;
    /** i18n key for the button label, resolved via the `artemisTranslate` pipe. */
    labelKey: string;
    icon: IconProp;
    severity: ActionSeverity;
    kind: 'link' | 'button' | 'delete';
    link?: (string | number)[];
    onClick?: () => void;
    /** When true the action is rendered greyed-out and non-interactive (links become non-navigable, buttons disabled). */
    disabled?: boolean;
    /** i18n key for the tooltip shown on a disabled action. */
    disabledTooltip?: string;
    /** Required when `kind === 'delete'`: everything the `[jhiDeleteButton]` directive needs. */
    delete?: DeleteActionConfig;
}
