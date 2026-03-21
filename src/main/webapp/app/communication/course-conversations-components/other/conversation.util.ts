import { User } from 'app/core/user/user.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';

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

export const defaultFirstLayerDialogOptions = {
    width: '55rem',
    modal: true,
    closable: true,
    closeOnEscape: true,
    dismissableMask: true,
    showHeader: false,
    styleClass: 'communication-dialog',
};

export const defaultSecondLayerDialogOptions = {
    width: '40rem',
    modal: true,
    closable: true,
    closeOnEscape: true,
    dismissableMask: true,
    showHeader: false,
    styleClass: 'communication-dialog',
};
