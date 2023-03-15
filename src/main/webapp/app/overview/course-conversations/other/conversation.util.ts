import { NgbModalOptions } from '@ng-bootstrap/ng-bootstrap';

import { User } from 'app/core/user/user.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';

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

export const defaultFirstLayerDialogOptions: NgbModalOptions = { size: 'lg', scrollable: false, backdrop: 'static' };

export const defaultSecondLayerDialogOptions: NgbModalOptions = {
    size: 'lg',
    scrollable: false,
    backdrop: 'static',
    backdropClass: 'second-layer-modal-bg',
    centered: true,
};
