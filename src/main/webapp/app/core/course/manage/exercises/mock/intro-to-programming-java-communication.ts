import dayjs from 'dayjs/esm';
import { Post } from 'app/communication/shared/entities/post.model';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { DisplayPriority, UserRole } from 'app/communication/metis.util';
import { User } from 'app/account/user/user.model';

/**
 * Mock communication (a channel + a few messages) for an exercise group's Communication panel, so the
 * real {@link DiscussionSectionComponent} has something to display. Dev-only; no server-side counterpart.
 */
const CHANNEL_ID = 99001;

function mockUser(id: number, login: string, firstName: string, lastName: string): User {
    const user = new User(id, login, firstName, lastName);
    user.name = `${firstName} ${lastName}`;
    return user;
}

const ANNA = mockUser(90101, 'anna', 'Anna', 'Schmidt');
const TOM = mockUser(90102, 'tom', 'Tom', 'Becker');
const LENA = mockUser(90103, 'lena', 'Lena', 'Hoffmann');
const MEHMET = mockUser(90104, 'mehmet', 'Mehmet', 'Yılmaz');

export function getMockGroupChannel(): ChannelDTO {
    const channel = new ChannelDTO();
    channel.id = CHANNEL_ID;
    channel.name = 'exercise-group';
    channel.description = 'Discussion for this exercise group';
    channel.subType = ChannelSubType.EXERCISE;
    channel.isPublic = true;
    channel.isMember = true;
    channel.numberOfMembers = 14;
    channel.creationDate = dayjs().subtract(10, 'day');
    return channel;
}

function mockPost(id: number, author: User, role: UserRole, daysAgo: number, content: string): Post {
    const post = new Post();
    post.id = id;
    post.author = author;
    post.authorRole = role;
    post.creationDate = dayjs().subtract(daysAgo, 'day');
    post.content = content;
    post.displayPriority = DisplayPriority.NONE;
    post.reactions = [];
    post.answers = [];
    return post;
}

export function getMockGroupPosts(): Post[] {
    return [
        mockPost(90001, ANNA, UserRole.USER, 2, 'Has anyone started the group exercises yet? Not sure which variant to pick.'),
        mockPost(90002, TOM, UserRole.USER, 1, 'I did the easiest one first to understand the concept, then tried a harder variant for practice.'),
        mockPost(90003, LENA, UserRole.TUTOR, 0, 'Good approach! Remember only one of the variants needs to be submitted to count towards your score.'),
        mockPost(90004, MEHMET, UserRole.USER, 0, 'Thanks, that helps a lot. Going with the robots variant then.'),
    ];
}
