import { UserPublicInfoDTO } from 'app/core/user/user.model';

export class ConversationUserDTO extends UserPublicInfoDTO {
    public isChannelAdmin?: boolean;
    public isRequestingUser?: boolean;
}
