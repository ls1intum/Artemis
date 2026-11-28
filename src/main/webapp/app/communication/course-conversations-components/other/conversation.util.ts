import { User } from 'app/account/user/user.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { TumAetUiDialogOptions } from '@tumaet/ui-angular/dialog';

export function getUserLabel({ firstName, lastName, login }: User | ConversationUserDTO, showLogin = true) {
    let label = '';
    if (firstName) {
        label += `${firstName} `;
    }
    if (lastName) {
        label += `${lastName} `;
    }
    if (login && showLogin) {
        label += `(${login})`;
    }
    return label.trim();
}

export function getChannelSubTypeReferenceTranslationKey(subType: ChannelSubType | undefined) {
    const prefix = 'artemisApp.conversationsLayout.';
    switch (subType) {
        case ChannelSubType.EXERCISE:
            return prefix + 'goToExercise';
        case ChannelSubType.LECTURE:
            return prefix + 'goToLecture';
        case ChannelSubType.EXAM:
            return prefix + 'goToExam';
        default:
            return undefined;
    }
}

/**
 * Options of the dialogs opened from the conversation layout. The dialog content renders its own header, so the opener adds
 * the accessible name (`ariaLabel`) and the `data` the content reads through `TumAetUiDialogConfig`.
 */
export const defaultFirstLayerDialogOptions: TumAetUiDialogOptions = {
    size: 'medium',
    closable: true,
    closeOnEscape: true,
    dismissableMask: true,
    showHeader: false,
    autoFocus: 'first-tabbable',
};

/** Options of a dialog opened on top of a first layer dialog, which is narrower than the first layer. */
export const defaultSecondLayerDialogOptions: TumAetUiDialogOptions = {
    size: 'small',
    closable: true,
    closeOnEscape: true,
    dismissableMask: true,
    showHeader: false,
    autoFocus: 'first-tabbable',
};
