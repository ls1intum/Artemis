import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import dayjs from 'dayjs/esm';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { ConversationParticipant } from 'app/communication/shared/entities/conversation/conversation-participant.model';
import { Conversation, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Slide } from 'app/lecture/shared/entities/lecture-unit/slide.model';
import { Channel, ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

export const communicationSlide1 = { id: 1, slideNumber: 1, slideImagePath: 'Communication-Slide-1.png' } as Slide;
export const communicationAttachment = { id: 1, name: 'Communication Attachment', link: 'attachments/lectures/1/Communication-Attachment.pdf' } as Attachment;
export const communicationAttachmentUnit = {
    id: 1,
    name: 'Communication Attachment Unit',
    attachment: communicationAttachment,
    slides: [communicationSlide1],
    type: LectureUnitType.ATTACHMENT_VIDEO,
} as AttachmentVideoUnit;
export const communicationLecture = { id: 1, title: 'Communication  Lecture', attachments: [communicationAttachment] } as Lecture;

export const communicationExam = { id: 1, title: 'Communication exam' } as Exam;
export const communicationLecture2 = { id: 2, title: 'Second Communication  Lecture' } as Lecture;
export const communicationLecture3 = {
    id: 3,
    title: 'Third Communication  Lecture 3',
    attachments: [communicationAttachment],
    lectureUnits: [communicationAttachmentUnit],
} as Lecture;

export const communicationExercise = { id: 1, title: 'Communication  Exercise', type: ExerciseType.TEXT } as Exercise;
export const communicationExercise2 = { id: 2, title: 'Second Communication  Exercise', type: ExerciseType.TEXT } as Exercise;

export const communicationUser1 = { id: 1, name: 'username1', login: 'login1' } as User;
export const communicationUser2 = { id: 2, name: 'username2', login: 'login2' } as User;
export const communicationTutor = { id: 4, name: 'username4', login: 'login4' } as User;

export const communicationTags = ['Tag1', 'Tag2'];

export const communicationReactionUser2 = { id: 2, user: communicationUser2, emojiId: 'smile', creationDate: undefined } as Reaction;
export const communicationReactionToCreate = { emojiId: 'cheerio', creationDate: undefined } as Reaction;

export const communicationFaq1 = { id: 1, questionTitle: 'title', questionAnswer: 'answer' };
export const communicationFaq2 = { id: 2, questionTitle: 'title', questionAnswer: 'answer' };
export const communicationFaq3 = { id: 3, questionTitle: 'title', questionAnswer: 'answer' };

export const communicationCourse = {
    id: 1,
    title: 'Communication Course',
    exercises: [communicationExercise, communicationExercise2],
    lectures: [communicationLecture, communicationLecture2, communicationLecture3],
    courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    faqs: [communicationFaq1, communicationFaq2, communicationFaq3],
} as Course;

export const communicationResolvingAnswerPostUser1 = {
    id: 1,
    author: communicationUser1,
    content: 'communicationAnswerPostUser3',
    creationDate: undefined,
    resolvesPost: true,
} as AnswerPost;

export const communicationAnswerPostUser2 = {
    id: 2,
    author: communicationUser2,
    content: 'communicationAnswerPostUser3',
    creationDate: undefined,
} as AnswerPost;
export const communicationAnswerPostToCreateUser1 = {
    author: communicationUser1,
    content: 'communicationAnswerPostToCreateUser1',
    creationDate: undefined,
} as AnswerPost;

const courseWideChannelTemplate = {
    type: ConversationType.CHANNEL,
    course: communicationCourse,
    isAnnouncementChannel: false,
    isArchived: false,
    isPublic: true,
    isCourseWide: true,
    description: 'Course-wide channel',
};

const communicationExerciseChannel = {
    ...courseWideChannelTemplate,
    id: 14,
    name: 'exercise-channel',
    exercise: communicationExercise,
} as Channel;

const communicationLectureChannel = {
    ...courseWideChannelTemplate,
    id: 15,
    name: 'lecture-channel',
    lecture: communicationLecture,
} as Channel;

const communicationTechSupportChannel = {
    ...courseWideChannelTemplate,
    id: 16,
    name: 'tech-support',
} as Channel;

const communicationOrganizationChannel = {
    ...courseWideChannelTemplate,
    id: 17,
    name: 'organization',
} as Channel;

const communicationRandomChannel = {
    ...courseWideChannelTemplate,
    id: 18,
    name: 'random',
} as Channel;

const communicationAnnouncementChannel = {
    ...courseWideChannelTemplate,
    id: 19,
    name: 'announcement',
    isAnnouncementChannel: true,
} as Channel;

export const communicationPostTechSupport = {
    id: 1,
    author: communicationUser1,
    conversation: communicationTechSupportChannel,
    title: 'title',
    content: 'communicationPostTechSupport',
    creationDate: undefined,
} as Post;

export const communicationPostRandom = {
    id: 2,
    author: communicationUser1,
    conversation: communicationRandomChannel,
    title: 'title',
    content: 'communicationPostRandom',
    creationDate: undefined,
} as Post;

export const communicationPostOrganization = {
    id: 3,
    author: communicationUser1,
    conversation: communicationOrganizationChannel,
    title: 'title',
    content: 'communicationPostOrganization',
    creationDate: undefined,
} as Post;

export const communicationAnnouncement = {
    id: 4,
    author: communicationUser1,
    conversation: communicationAnnouncementChannel,
    title: 'title',
    content: 'communicationPostOrganization',
    creationDate: undefined,
} as Post;

export const communicationGeneralCourseWidePosts = [communicationPostTechSupport, communicationPostRandom, communicationPostOrganization];

export const communicationPostExerciseUser1 = {
    id: 5,
    author: communicationUser1,
    conversation: communicationExerciseChannel,
    title: 'title',
    content: 'communicationPostExerciseUser1',
    creationDate: undefined,
    isSaved: false,
} as Post;

export const communicationPostExerciseUser2 = {
    id: 6,
    author: communicationUser2,
    conversation: communicationExerciseChannel,
    title: 'title',
    content: 'communicationPostExerciseUser2',
    creationDate: undefined,
} as Post;

export const communicationExercisePosts = [communicationPostExerciseUser1, communicationPostExerciseUser2];

export const communicationPostLectureUser1 = {
    id: 7,
    author: communicationUser1,
    conversation: communicationLectureChannel,
    title: 'title',
    content: 'communicationPostLectureUser1',
    creationDate: undefined,
} as Post;

export const communicationPostLectureUser2 = {
    id: 8,
    author: communicationUser2,
    conversation: communicationLectureChannel,
    title: 'title',
    content: 'communicationPostLectureUser2',
    creationDate: undefined,
    answers: [communicationResolvingAnswerPostUser1],
} as Post;

communicationResolvingAnswerPostUser1.post = communicationPostLectureUser2;

export const communicationLecturePosts = [communicationPostLectureUser1, communicationPostLectureUser2];

export const communicationCoursePosts = communicationGeneralCourseWidePosts.concat(communicationExercisePosts, communicationLecturePosts);

export const communicationPostToCreateUser1 = {
    author: communicationUser1,
    content: 'communicationAnswerToCreateUser1',
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

const conversationParticipantUser1 = { id: 1, user: communicationUser1, unreadMessagesCount: 1 } as ConversationParticipant;

const conversationParticipantUser2 = { id: 2, user: communicationUser2, unreadMessagesCount: 0 } as ConversationParticipant;

export const conversationBetweenUser1User2 = {
    id: 1,
    conversationParticipants: [conversationParticipantUser1, conversationParticipantUser2],
    creationDate: undefined,
    lastMessageDate: undefined,
} as Conversation;

export const directMessageUser1 = {
    id: 9,
    author: communicationUser1,
    content: 'user1directMessageToUser2',
    creationDate: undefined,
    conversation: conversationBetweenUser1User2,
} as Post;

export const directMessageUser2 = {
    id: 10,
    author: communicationUser1,
    content: 'user2directMessageToUser1',
    creationDate: undefined,
    conversation: conversationBetweenUser1User2,
} as Post;

export const messagesBetweenUser1User2 = [directMessageUser1, directMessageUser2];

export const communicationChannel = {
    id: 21,
    type: ConversationType.CHANNEL,
    name: 'example-channel',
    description: 'Example course-wide channel',
    isAnnouncementChannel: false,
    isArchived: false,
    isPublic: true,
    isCourseWide: true,
} as Channel;

export const communicationPostInChannel = {
    id: 4,
    author: communicationUser1,
    title: 'title',
    content: 'communicationPostOrganization',
    creationDate: undefined,
    conversation: communicationChannel,
} as Post;

export const plagiarismPost = {
    id: 11,
    author: communicationUser1,
    title: 'title',
    content: 'plagiarism Case',
    plagiarismCase: { id: 1 } as PlagiarismCase,
} as Post;

export const communicationGeneralChannelDTO = {
    id: 17,
    type: ConversationType.CHANNEL,
    subType: ChannelSubType.GENERAL,
    isCourseWide: true,
    name: 'general-channel',
} as ChannelDTO;

export const communicationExerciseChannelDTO = {
    id: 14,
    type: ConversationType.CHANNEL,
    subType: ChannelSubType.EXERCISE,
    isCourseWide: true,
    subTypeReferenceId: communicationExercise.id,

    name: 'exercise-channel',
} as ChannelDTO;

export const communicationLectureChannelDTO = {
    id: 15,
    type: ConversationType.CHANNEL,
    subType: ChannelSubType.LECTURE,
    isCourseWide: true,
    subTypeReferenceId: communicationLecture.id,
    name: 'lecture-channel',
} as ChannelDTO;

export const communicationExamChannelDTO = {
    id: 20,
    type: ConversationType.CHANNEL,
    subType: ChannelSubType.EXAM,
    isCourseWide: true,
    subTypeReferenceId: communicationExam.id,
    name: 'exam-channel',
} as ChannelDTO;
