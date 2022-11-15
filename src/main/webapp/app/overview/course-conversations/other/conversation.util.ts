import { User } from 'app/core/user/user.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';

export function getUserLabel({ firstName, lastName, login }: User | ConversationUserDTO) {
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
