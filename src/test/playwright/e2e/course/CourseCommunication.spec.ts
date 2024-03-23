import { Course } from 'app/entities/course.model';
import { Channel, ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentThree, studentTwo } from '../../support/users';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { Fixtures } from '../../fixtures/fixtures';
import { Post } from 'app/entities/metis/post.model';

const courseConfigsToTest = [
    { description: 'messaging and communication enabled', config: { allowMessaging: true, allowCommunication: true } },
    { description: 'only communication enabled', config: { allowMessaging: false, allowCommunication: true } },
];

courseConfigsToTest.forEach((configToTest) => {
    test.describe('Course communication with ' + configToTest.description, () => {
        let course: Course;
        let courseWideRandomChannel: ChannelDTO;

        test.beforeEach('Create course', async ({ login, courseManagementAPIRequests, communicationAPIRequests }) => {
            await login(admin);
            course = await courseManagementAPIRequests.createCourse(configToTest.config);

            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
            await courseManagementAPIRequests.addStudentToCourse(course, studentThree);

            const courseWideChannels = await communicationAPIRequests.getCourseWideChannels(course.id!);
            courseWideRandomChannel = courseWideChannels.find((channel) => channel.name === 'random')!;
        });

        test.describe('Course overview communication', () => {
            test('Instructor should be able to pin a message', async ({ page, login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Pin Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await login(instructor, `/courses/${course.id}/discussion`);
                await courseCommunication.pinPost(post.id!);
                await page.reload();
                await courseCommunication.checkSinglePostByPosition(0, undefined, content);
            });

            test('Instructor should be able to select answer', async ({ page, login, communicationAPIRequests, courseCommunication }) => {
                test.fixme();
                const content = 'Answer Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await login(studentTwo, `/courses/${course.id}/discussion`);
                const answerPost = await communicationAPIRequests.createCourseMessageReply(course, post, 'Answer Reply');
                await login(instructor, `/courses/${course.id}/discussion`);
                await page.reload();
                await courseCommunication.showReplies(post.id!);
                await courseCommunication.markAsAnswer(answerPost.id!);
                await page.reload();
                await courseCommunication.checkResolved(post.id!);
            });

            test('Other students should be able to see message', async ({ page, login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await login(studentTwo, `/courses/${course.id}/discussion`);
                await page.reload();
                await courseCommunication.checkSinglePost(post.id!, content);
            });

            test('Other students should be able to search for message', async ({ page, login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Search Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await login(studentTwo, `/courses/${course.id}/discussion`);
                await page.reload();
                await courseCommunication.searchForMessage(content);
                await courseCommunication.checkSinglePost(post.id!, content);
            });

            test('Other students should be able to filter for message by context', async ({ page, login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Context Filter Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await page.reload();
                await courseCommunication.filterByContext(courseWideRandomChannel.name!);
                await courseCommunication.checkSinglePost(post.id!, content);
            });

            test('Students should be able to filter for message by own', async ({ login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Own Filter Post Content';
                await login(studentThree, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await courseCommunication.filterByOwn();
                await courseCommunication.checkSinglePost(post.id!, content);
            });

            test('Other students should be able to reply to message', async ({ page, login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Reply Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await login(studentTwo, `/courses/${course.id}/discussion`);
                await page.reload();
                const replyText = 'My Test reply';
                await courseCommunication.openReply(post.id!);
                const reply = await courseCommunication.replyWithMessage(post.id!, replyText);
                await login(studentOne, `/courses/${course.id}/discussion`);
                await courseCommunication.showReplies(post.id!);
                await courseCommunication.checkReply(reply.id!, replyText);
            });

            test('Other students should be able to react to message', async ({ page, login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test React Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await login(studentTwo, `/courses/${course.id}/discussion`);
                const emoji = 'tada';
                await courseCommunication.react(post.id!, emoji);
                await page.reload();
                await courseCommunication.checkReaction(post.id!, emoji);
            });

            test('Students should be able to edit their message', async ({ page, login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Edit Post Content';
                const newContent = 'Test Edited Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await page.reload();
                await courseCommunication.editMessage(post.id!, newContent);
                await courseCommunication.checkSinglePost(post.id!, newContent);
                await courseCommunication.checkPostEdited(post.id!);
            });

            test('Students should be able to delete their message', async ({ page, login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Delete Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, courseWideRandomChannel.id!, content);
                await page.reload();
                await courseCommunication.deletePost(post.id!);
                await page.waitForLoadState('networkidle');
                await courseCommunication.getSinglePost(post.id!).waitFor({ state: 'detached' });
            });
        });

        test.describe('Exercise communication via channel', () => {
            let textExercise: TextExercise;
            let channel: Channel;

            test.beforeEach(async ({ login, exerciseAPIRequests, communicationAPIRequests }) => {
                await login(admin);
                textExercise = await exerciseAPIRequests.createTextExercise({ course });
                channel = await communicationAPIRequests.getExerciseChannel(textExercise.course!.id!, textExercise.id!);
            });

            test('Instructor should be able to pin a message within exercises', async ({ login, communicationAPIRequests, courseCommunication, page }) => {
                const content = 'Pin Exercise Post Content';
                await login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, content);
                await login(instructor, `/courses/${course.id}/exercises/${textExercise.id}`);
                await courseCommunication.pinPost(post.id!);
                await page.reload();
                await courseCommunication.checkSinglePostByPosition(0, undefined, content);
            });

            test('Students should be able to create messages within exercises', async ({ login, courseCommunication }) => {
                await login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                await courseCommunication.newPost();
                const text = await Fixtures.get('loremIpsum-short.txt');
                await courseCommunication.setContentInline(text!);
                const response = await courseCommunication.saveMessage();
                const post: Post = await response.json();
                await courseCommunication.checkMessagePost(post.id!, text!);
            });

            test('Students should be able to search for exercise messages', async ({ login, communicationAPIRequests, courseCommunication, page }) => {
                const content = 'Exercise Search Test Post Content';
                await login(studentOne, `/courses/${course.id}/exercises/${textExercise.id!}`);
                const post = await communicationAPIRequests.createCourseMessage(course, channel.id!, 'channel', content);
                await login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                await page.reload();
                await courseCommunication.searchForMessage(content);
                await courseCommunication.checkSingleExercisePost(post.id!, content);
            });

            test('Other students should be able to filter for exercise message by context', async ({ login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Context Filter Exercise Post Content';
                await login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, content);
                await login(studentTwo, `/courses/${course.id}/discussion`);
                await courseCommunication.filterByContext(textExercise.channelName!);
                await courseCommunication.checkSinglePost(post.id!, content);
            });

            test('Students should be able to filter for exercise message by own', async ({ login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Own Filter Exercise Post Content';
                await login(studentThree, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, content);
                await courseCommunication.filterByOwn();
                await courseCommunication.checkSinglePost(post.id!, content);
            });

            test('Other students should be able to reply to an exercise message', async ({ login, communicationAPIRequests, courseCommunication, page }) => {
                const content = 'Test Reply Post Content';
                await login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                const post = await communicationAPIRequests.createCourseMessage(course, channel.id!, 'channel', content);
                await login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                await page.reload();
                const replyText = 'My Test reply';
                await courseCommunication.openReply(post.id!);
                const reply = await courseCommunication.replyWithMessage(post.id!, replyText);
                await login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                await courseCommunication.showReplies(post.id!);
                await courseCommunication.checkReply(reply.id!, replyText);
            });

            test('Other students should be able to react to an exercise message', async ({ login, communicationAPIRequests, courseCommunication, page }) => {
                const content = 'Test React Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseMessage(course, channel.id!, 'channel', content);
                await login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                const emoji = 'tada';
                await courseCommunication.react(post.id!, emoji);
                await page.reload();
                await courseCommunication.checkReaction(post.id!, emoji);
            });
        });

        test.describe('Lecture communication via channel', () => {
            let lecture: Lecture;
            let channel: Channel;

            test.beforeEach(async ({ login, courseManagementAPIRequests, communicationAPIRequests }) => {
                await login(admin);
                lecture = await courseManagementAPIRequests.createLecture(course);
                channel = await communicationAPIRequests.getLectureChannel(lecture.course!.id!, lecture.id!);
            });

            test('Instructor should be able to pin a message within lectures', async ({ login, communicationAPIRequests, courseCommunication, page }) => {
                const content = 'Pin Lecture Post Content';
                await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, content);
                await login(instructor, `/courses/${course.id}/lectures/${lecture.id}`);
                await courseCommunication.pinPost(post.id!);
                await page.reload();
                await courseCommunication.checkSinglePostByPosition(0, undefined, content);
            });

            test('Students should be able to create messages within lecture', async ({ login, courseCommunication }) => {
                await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                await courseCommunication.newPost();
                const text = await Fixtures.get('loremIpsum-short.txt');
                await courseCommunication.setContentInline(text!);
                const response = await courseCommunication.saveMessage();
                const post: Post = await response.json();
                await courseCommunication.checkMessagePost(post.id!, text!);
            });

            test('Students should be able to search for lecture messages', async ({ login, communicationAPIRequests, courseCommunication, page }) => {
                const content = 'Lecture Search Test Post Content';
                await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, content);
                await login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
                await page.reload();
                await courseCommunication.searchForMessage(content);
                await courseCommunication.checkSingleExercisePost(post.id!, content);
            });

            test('Other students should be able to filter for lecture message by context', async ({ login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Context Filter Lecture Post Content';
                await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, content);
                await login(studentTwo, `/courses/${course.id}/discussion`);
                await courseCommunication.filterByContext(lecture.channelName!);
                await courseCommunication.checkSinglePost(post.id!, content);
            });

            test('Students should be able to filter for lecture message by own', async ({ login, communicationAPIRequests, courseCommunication }) => {
                const content = 'Test Own Filter Lecture Post Content';
                await login(studentThree, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, content);
                await courseCommunication.filterByOwn();
                await courseCommunication.checkSinglePost(post.id!, content);
            });

            test('Other students should be able to reply to a lecture message', async ({ login, communicationAPIRequests, courseCommunication, page }) => {
                const content = 'Test Reply Post Content';
                await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, content);
                await login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
                await page.reload();
                const replyText = 'My Test reply';
                await courseCommunication.openReply(post.id!);
                const reply = await courseCommunication.replyWithMessage(post.id!, replyText);
                await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                await courseCommunication.showReplies(post.id!);
                await courseCommunication.checkReply(reply.id!, replyText);
            });

            test('Other students should be able to react to a lecture message', async ({ login, communicationAPIRequests, courseCommunication, page }) => {
                const content = 'Test React Post Content';
                await login(studentOne, `/courses/${course.id}/discussion`);
                const post = await communicationAPIRequests.createCourseWideMessage(course, channel.id!, content);
                await login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
                const emoji = 'tada';
                await courseCommunication.react(post.id!, emoji);
                await page.reload();
                await courseCommunication.checkReaction(post.id!, emoji);
            });
        });

        test.afterEach('Delete Courses', async ({ courseManagementAPIRequests }) => {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        });
    });
});
