import { User } from 'app/core/user/user.model';

export function getUserLabel({ firstName, lastName, login }: User) {
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
