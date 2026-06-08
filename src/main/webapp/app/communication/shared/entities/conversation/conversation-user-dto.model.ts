import { UserPublicInfoDTO } from 'app/account/user/user.model';

export class ConversationUserDTO extends UserPublicInfoDTO {
    public isChannelModerator?: boolean;
    public isRequestingUser?: boolean;
}
