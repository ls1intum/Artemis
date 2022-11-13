import { UserPublicInfoDTO } from 'app/core/user/user.model';

export class ConversationUser extends UserPublicInfoDTO {
    public isChannelAdmin?: boolean;
}
