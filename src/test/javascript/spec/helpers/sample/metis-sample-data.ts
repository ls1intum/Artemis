import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { CourseWideContext, DisplayPriority, VOTE_EMOJI_ID } from 'app/shared/metis/metis.util';
import { Reaction } from 'app/entities/metis/reaction.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import dayjs from 'dayjs/esm';
import { Attachment } from 'app/entities/attachment.model';

export const metisAttachment = { id: 1, name: 'Metis Attachment', link: 'directory/Metis-Attachment.pdf' } as Attachment;

export const metisLecture = { id: 1, title: 'Metis  Lecture', attachments: [metisAttachment] } as Lecture;
export const metisLecture2 = { id: 1, title: 'Second Metis  Lecture' } as Lecture;

export const metisExercise = { id: 1, title: 'Metis  Exercise', type: ExerciseType.TEXT } as Exercise;
export const metisExercise2 = { id: 1, title: 'Second Metis  Exercise', type: ExerciseType.TEXT } as Exercise;

export const metisUser1 = { id: 1, name: 'username1', login: 'login1', groups: ['metisStudents'] } as User;
export const metisUser2 = { id: 2, name: 'username2', login: 'login2', groups: ['metisStudents'] } as User;
export const metisTutor = { id: 3, name: 'username3', login: 'login3', groups: ['metisTutors'] } as User;
export const metisTags = ['Tag1', 'Tag2'];

export const metisUpVoteReactionUser1 = { id: 1, user: metisUser1, emojiId: VOTE_EMOJI_ID } as Reaction;
export const metisReactionUser2 = { id: 2, user: metisUser2, emojiId: 'smile', creationDate: undefined } as Reaction;
export const metisReactionToCreate = { emojiId: 'cheerio', creationDate: undefined } as Reaction;

export const metisCourse = {
    id: 1,
    title: 'Metis Course',
    exercises: [metisExercise, metisExercise2],
    lectures: [metisLecture, metisLecture2],
    postsEnabled: true,
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

export const metisApprovedAnswerPostTutor = {
    id: 3,
    author: metisTutor,
    content: 'metisApprovedAnswerPostTutor',
    resolvesPost: true,
    creationDate: undefined,
} as AnswerPost;

export const metisAnswerPostToCreateUser1 = {
    author: metisUser1,
    content: 'metisAnswerPostToCreateUser1',
    creationDate: undefined,
} as AnswerPost;

export const metisPostTechSupport = {
    id: 1,
    author: metisUser1,
    courseWideContext: CourseWideContext.TECH_SUPPORT,
    course: metisCourse,
    title: 'title',
    content: 'metisPostTechSupport',
    creationDate: undefined,
} as Post;

export const metisPostRandom = {
    id: 2,
    author: metisUser1,
    courseWideContext: CourseWideContext.RANDOM,
    course: metisCourse,
    title: 'title',
    content: 'metisPostRandom',
    creationDate: undefined,
} as Post;

export const metisPostOrganization = {
    id: 3,
    author: metisUser1,
    courseWideContext: CourseWideContext.ORGANIZATION,
    course: metisCourse,
    title: 'title',
    content: 'metisPostOrganization',
    creationDate: undefined,
} as Post;

export const metisAnnouncement = {
    id: 4,
    author: metisUser1,
    courseWideContext: CourseWideContext.ORGANIZATION,
    course: metisCourse,
    title: 'title',
    content: 'metisPostOrganization',
    creationDate: undefined,
} as Post;

export const metisCoursePostsWithCourseWideContext = [metisPostTechSupport, metisPostRandom, metisPostOrganization];

export const metisPostExerciseUser1 = {
    id: 5,
    author: metisUser1,
    exercise: metisExercise,
    title: 'title',
    content: 'metisPostExerciseUser1',
    creationDate: undefined,
} as Post;

export const metisPostExerciseUser2 = {
    id: 6,
    author: metisUser2,
    exercise: metisExercise,
    title: 'title',
    content: 'metisPostExerciseUser2',
    creationDate: undefined,
} as Post;

export const metisExercisePosts = [metisPostExerciseUser1, metisPostExerciseUser2];

export const metisPostLectureUser1 = {
    id: 7,
    author: metisUser1,
    lecture: metisLecture,
    title: 'title',
    content: 'metisPostLectureUser1',
    creationDate: undefined,
} as Post;

export const metisPostLectureUser2 = {
    id: 8,
    author: metisUser2,
    lecture: metisLecture,
    title: 'title',
    content: 'metisPostLectureUser2',
    creationDate: undefined,
    answers: [metisResolvingAnswerPostUser1],
} as Post;

metisResolvingAnswerPostUser1.post = metisPostLectureUser2;

export const metisLecturePosts = [metisPostLectureUser1, metisPostLectureUser2];

export const metisCoursePosts = metisCoursePostsWithCourseWideContext.concat(metisExercisePosts, metisLecturePosts);

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

export const post1WithCreationDate = {
    ...metisPostExerciseUser1,
    creationDate: dayjs(),
    displayPriority: DisplayPriority.PINNED,
};

export const post2WithCreationDate = {
    ...metisPostExerciseUser2,
    creationDate: dayjs().subtract(2, 'day'),
    displayPriority: DisplayPriority.NONE,
};

export const post3WithCreationDate = {
    ...metisPostExerciseUser1,
    creationDate: dayjs().subtract(1, 'day'),
    reactions: [metisUpVoteReactionUser1, metisUpVoteReactionUser1],
    displayPriority: DisplayPriority.NONE,
};

export const post4WithCreationDate = {
    ...metisPostLectureUser2,
    creationDate: dayjs().subtract(2, 'minute'),
    reactions: [metisUpVoteReactionUser1],
    displayPriority: DisplayPriority.ARCHIVED,
};

export const post5WithCreationDate = {
    ...metisPostLectureUser2,
    creationDate: dayjs().subtract(3, 'minute'),
    reactions: [metisUpVoteReactionUser1],
    displayPriority: DisplayPriority.NONE,
};

export const post6WithCreationDate = {
    ...metisPostLectureUser2,
    creationDate: dayjs().subtract(4, 'minute'),
    reactions: [metisUpVoteReactionUser1],
    displayPriority: DisplayPriority.NONE,
};

export const post7WithCreationDate = {
    ...metisPostLectureUser2,
    creationDate: dayjs().subtract(1, 'minute'),
    displayPriority: DisplayPriority.NONE,
};

export const postsWithCreationDate = [
    post1WithCreationDate,
    post2WithCreationDate,
    post3WithCreationDate,
    post4WithCreationDate,
    post5WithCreationDate,
    post6WithCreationDate,
    post7WithCreationDate,
];
