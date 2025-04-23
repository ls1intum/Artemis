import { NgbModalOptions } from '@ng-bootstrap/ng-bootstrap';
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

export const defaultFirstLayerDialogOptions: NgbModalOptions = { size: 'lg', scrollable: false, backdrop: 'static' };

export const defaultSecondLayerDialogOptions: NgbModalOptions = {
    size: 'lg',
    scrollable: false,
    backdrop: 'static',
    backdropClass: 'second-layer-modal-bg',
    centered: true,
};
