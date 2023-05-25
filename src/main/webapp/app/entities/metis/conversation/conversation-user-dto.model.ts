import { UserPublicInfoDTO } from 'app/core/user/user.model';

export class ConversationUserDTO extends UserPublicInfoDTO {
    public isChannelModerator?: boolean;
    public isRequestingUser?: boolean;
}
