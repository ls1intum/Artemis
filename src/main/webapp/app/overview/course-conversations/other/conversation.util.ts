import { User } from 'app/core/user/user.model';
import { ConversationUser } from 'app/entities/metis/conversation/conversation-user-dto.model';

export function getUserLabel({ firstName, lastName, login }: User | ConversationUser) {
    let label = '';
    if (firstName) {
        label += `${firstName} `;
    }
    if (lastName) {
        label += `${lastName} `;
    }
    if (login) {
        label += `(${login})`;
    }
    return label.trim();
}
