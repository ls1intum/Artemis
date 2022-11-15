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
