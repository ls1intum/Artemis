import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { VOTE_EMOJI_ID } from 'app/shared/metis/metis.util';
import { Reaction } from 'app/entities/metis/reaction.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import dayjs from 'dayjs/esm';
import { Attachment } from 'app/entities/attachment.model';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-participant.model';
import { Conversation, ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { Slide } from 'app/entities/lecture-unit/slide.model';
import { Channel, ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { Exam } from 'app/entities/exam.model';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';

export const metisSlide1 = { id: 1, slideNumber: 1, slideImagePath: 'directory/attachments/slides/Metis-Slide-1.png' } as Slide;
export const metisAttachment = { id: 1, name: 'Metis Attachment', link: 'directory/attachments/Metis-Attachment.pdf' } as Attachment;
export const metisAttachmentUnit = { id: 1, name: 'Metis Attachment Unit', attachment: metisAttachment, slides: [metisSlide1] } as AttachmentUnit;
export const metisLecture = { id: 1, title: 'Metis  Lecture', attachments: [metisAttachment] } as Lecture;

export const metisExam = { id: 1, title: 'Metis exam' } as Exam;
export const metisLecture2 = { id: 2, title: 'Second Metis  Lecture' } as Lecture;
export const metisLecture3 = { id: 3, title: 'Third Metis  Lecture 3', attachments: [metisAttachment], lectureUnits: [metisAttachmentUnit] } as Lecture;

export const metisExercise = { id: 1, title: 'Metis  Exercise', type: ExerciseType.TEXT } as Exercise;
export const metisExercise2 = { id: 1, title: 'Second Metis  Exercise', type: ExerciseType.TEXT } as Exercise;

export const metisUser1 = { id: 1, name: 'username1', login: 'login1', groups: ['metisStudents'] } as User;
export const metisUser2 = { id: 2, name: 'username2', login: 'login2', groups: ['metisStudents'] } as User;
export const metisTutor = { id: 4, name: 'username4', login: 'login4', groups: ['metisTutors'] } as User;

export const metisTags = ['Tag1', 'Tag2'];

export const metisUpVoteReactionUser1 = { id: 1, user: metisUser1, emojiId: VOTE_EMOJI_ID } as Reaction;
export const metisReactionUser2 = { id: 2, user: metisUser2, emojiId: 'smile', creationDate: undefined } as Reaction;
export const metisReactionToCreate = { emojiId: 'cheerio', creationDate: undefined } as Reaction;

export const metisCourse = {
    id: 1,
    title: 'Metis Course',
    exercises: [metisExercise, metisExercise2],
    lectures: [metisLecture, metisLecture2, metisLecture3],
    courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    groups: ['metisTutors', 'metisStudents', 'metisInstructors'],
} as Course;

export const metisResolvingAnswerPostUser1 = {
    id: 1,
    author: metisUser1,
    content: 'metisAnswerPostUser3',
    creationDate: undefined,
    resolvesPost: true,
} as AnswerPost;

export const metisAnswerPostUser2 = {
    id: 2,
    author: metisUser2,
    content: 'metisAnswerPostUser3',
    creationDate: undefined,
} as AnswerPost;
export const metisAnswerPostToCreateUser1 = {
    author: metisUser1,
    content: 'metisAnswerPostToCreateUser1',
    creationDate: undefined,
} as AnswerPost;

const courseWideChannelTemplate = {
    type: ConversationType.CHANNEL,
    course: metisCourse,
    isAnnouncementChannel: false,
    isArchived: false,
    isPublic: true,
    isCourseWide: true,
    description: 'Course-wide channel',
};

const metisExerciseChannel = {
    ...courseWideChannelTemplate,
    id: 14,
    name: 'exercise-channel',
    exercise: metisExercise,
} as Channel;

const metisLectureChannel = {
    ...courseWideChannelTemplate,
    id: 15,
    name: 'lecture-channel',
    lecture: metisLecture,
} as Channel;

const metisTechSupportChannel = {
    ...courseWideChannelTemplate,
    id: 16,
    name: 'tech-support',
} as Channel;

const metisOrganizationChannel = {
    ...courseWideChannelTemplate,
    id: 17,
    name: 'organization',
} as Channel;

const metisRandomChannel = {
    ...courseWideChannelTemplate,
    id: 18,
    name: 'random',
} as Channel;

const metisAnnouncementChannel = {
    ...courseWideChannelTemplate,
    id: 19,
    name: 'announcement',
    isAnnouncementChannel: true,
} as Channel;

export const metisPostTechSupport = {
    id: 1,
    author: metisUser1,
    conversation: metisTechSupportChannel,
    title: 'title',
    content: 'metisPostTechSupport',
    creationDate: undefined,
} as Post;

export const metisPostRandom = {
    id: 2,
    author: metisUser1,
    conversation: metisRandomChannel,
    title: 'title',
    content: 'metisPostRandom',
    creationDate: undefined,
} as Post;

export const metisPostOrganization = {
    id: 3,
    author: metisUser1,
    conversation: metisOrganizationChannel,
    title: 'title',
    content: 'metisPostOrganization',
    creationDate: undefined,
} as Post;

export const metisAnnouncement = {
    id: 4,
    author: metisUser1,
    conversation: metisAnnouncementChannel,
    title: 'title',
    content: 'metisPostOrganization',
    creationDate: undefined,
} as Post;

export const metisGeneralCourseWidePosts = [metisPostTechSupport, metisPostRandom, metisPostOrganization];

export const metisPostExerciseUser1 = {
    id: 5,
    author: metisUser1,
    conversation: metisExerciseChannel,
    title: 'title',
    content: 'metisPostExerciseUser1',
    creationDate: undefined,
} as Post;

export const metisPostExerciseUser2 = {
    id: 6,
    author: metisUser2,
    conversation: metisExerciseChannel,
    title: 'title',
    content: 'metisPostExerciseUser2',
    creationDate: undefined,
} as Post;

export const metisExercisePosts = [metisPostExerciseUser1, metisPostExerciseUser2];

export const metisPostLectureUser1 = {
    id: 7,
    author: metisUser1,
    conversation: metisLectureChannel,
    title: 'title',
    content: 'metisPostLectureUser1',
    creationDate: undefined,
} as Post;

export const metisPostLectureUser2 = {
    id: 8,
    author: metisUser2,
    conversation: metisLectureChannel,
    title: 'title',
    content: 'metisPostLectureUser2',
    creationDate: undefined,
    answers: [metisResolvingAnswerPostUser1],
} as Post;

metisResolvingAnswerPostUser1.post = metisPostLectureUser2;

export const metisLecturePosts = [metisPostLectureUser1, metisPostLectureUser2];

export const metisCoursePosts = metisGeneralCourseWidePosts.concat(metisExercisePosts, metisLecturePosts);

export const metisPostToCreateUser1 = {
    author: metisUser1,
    content: 'metisAnswerToCreateUser1',
    creationDate: undefined,
} as Post;

export const unApprovedAnswerPost1 = {
    id: 1,
    creationDate: dayjs(),
    content: 'not approved most recent',
    resolvesPost: false,
} as AnswerPost;

export const unApprovedAnswerPost2 = {
    id: 2,
    creationDate: dayjs().subtract(1, 'day'),
    content: 'not approved',
    resolvesPost: false,
} as AnswerPost;

export const approvedAnswerPost = {
    id: 2,
    creationDate: undefined,
    content: 'approved',
    resolvesPost: true,
} as AnswerPost;

export const sortedAnswerArray: AnswerPost[] = [approvedAnswerPost, unApprovedAnswerPost2, unApprovedAnswerPost1];
export const unsortedAnswerArray: AnswerPost[] = [unApprovedAnswerPost1, unApprovedAnswerPost2, approvedAnswerPost];

export const post = {
    id: 1,
    creationDate: undefined,
    answers: unsortedAnswerArray,
} as Post;

const conversationParticipantUser1 = { id: 1, user: metisUser1, unreadMessagesCount: 1 } as ConversationParticipant;

const conversationParticipantUser2 = { id: 2, user: metisUser2, unreadMessagesCount: 0 } as ConversationParticipant;

export const conversationBetweenUser1User2 = {
    id: 1,
    conversationParticipants: [conversationParticipantUser1, conversationParticipantUser2],
    creationDate: undefined,
    lastMessageDate: undefined,
} as Conversation;

export const directMessageUser1 = {
    id: 9,
    author: metisUser1,
    content: 'user1directMessageToUser2',
    creationDate: undefined,
    conversation: conversationBetweenUser1User2,
} as Post;

export const directMessageUser2 = {
    id: 10,
    author: metisUser1,
    content: 'user2directMessageToUser1',
    creationDate: undefined,
    conversation: conversationBetweenUser1User2,
} as Post;

export const messagesBetweenUser1User2 = [directMessageUser1, directMessageUser2];

export const metisChannel = {
    id: 21,
    type: ConversationType.CHANNEL,
    name: 'example-channel',
    description: 'Example course-wide channel',
    isAnnouncementChannel: false,
    isArchived: false,
    isPublic: true,
    isCourseWide: true,
} as Channel;

export const metisPostInChannel = {
    id: 4,
    author: metisUser1,
    title: 'title',
    content: 'metisPostOrganization',
    creationDate: undefined,
    conversation: metisChannel,
} as Post;

export const plagiarismPost = {
    id: 11,
    author: metisUser1,
    title: 'title',
    content: 'plagiarism Case',
    plagiarismCase: { id: 1 } as PlagiarismCase,
} as Post;

export const metisGeneralChannelDTO = {
    id: 17,
    type: ConversationType.CHANNEL,
    subType: ChannelSubType.GENERAL,
    isCourseWide: true,
    name: 'general-channel',
} as ChannelDTO;

export const metisExerciseChannelDTO = {
    id: 14,
    type: ConversationType.CHANNEL,
    subType: ChannelSubType.EXERCISE,
    isCourseWide: true,
    subTypeReferenceId: metisExercise.id,

    name: 'exercise-channel',
} as ChannelDTO;

export const metisLectureChannelDTO = {
    id: 15,
    type: ConversationType.CHANNEL,
    subType: ChannelSubType.LECTURE,
    isCourseWide: true,
    subTypeReferenceId: metisLecture.id,
    name: 'lecture-channel',
} as ChannelDTO;

export const metisExamChannelDTO = {
    id: 20,
    type: ConversationType.CHANNEL,
    subType: ChannelSubType.EXAM,
    isCourseWide: true,
    subTypeReferenceId: metisExam.id,
    name: 'exam-channel',
} as ChannelDTO;
