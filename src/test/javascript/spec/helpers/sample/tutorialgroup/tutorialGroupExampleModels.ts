import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';

export class UserWithLabel extends User {
    label: string;
}

export const generateExampleTutorialGroup = ({
    id = 1,
    title = 'Example',
    capacity = 10,
    campus = 'Example Campus',
    language = 'ENGLISH',
    additionalInformation = 'Example Additional Information',
    isOnline = false,
    teachingAssistant = { id: 1, login: 'Example TA', label: '(Example TA)' } as UserWithLabel,
    isUserRegistered = false,
    isUserTutor = false,
    numberOfRegisteredUsers = 5,
    course = { id: 1, title: 'Test Course' } as Course,
    teachingAssistantName = 'Example TA',
    teachingAssistantId = 1,
    teachingAssistantImageUrl = 'test image',
    courseTitle = 'Test Course',
    tutorialGroupSchedule = {
        id: 1,
        dayOfWeek: 1,
        startTime: '10:00',
        endTime: '11:00',
        repetitionFrequency: 1,
        location: 'Example Location',
        validFromInclusive: dayjs('2021-01-01'),
        validToInclusive: dayjs('2021-01-01'),
    },
}: TutorialGroup) => {
    const exampleTutorialGroup = new TutorialGroup();
    exampleTutorialGroup.id = id;
    exampleTutorialGroup.title = title;
    exampleTutorialGroup.capacity = capacity;
    exampleTutorialGroup.campus = campus;
    exampleTutorialGroup.language = language;
    exampleTutorialGroup.additionalInformation = additionalInformation;
    exampleTutorialGroup.isOnline = isOnline;
    exampleTutorialGroup.teachingAssistant = teachingAssistant;
    exampleTutorialGroup.isUserRegistered = isUserRegistered;
    exampleTutorialGroup.isUserTutor = isUserTutor;
    exampleTutorialGroup.numberOfRegisteredUsers = numberOfRegisteredUsers;
    exampleTutorialGroup.course = course;
    exampleTutorialGroup.teachingAssistantName = teachingAssistantName;
    exampleTutorialGroup.teachingAssistantId = teachingAssistantId;
    exampleTutorialGroup.teachingAssistantImageUrl = teachingAssistantImageUrl;
    exampleTutorialGroup.courseTitle = courseTitle;
    exampleTutorialGroup.tutorialGroupSchedule = tutorialGroupSchedule;
    return exampleTutorialGroup;
};
