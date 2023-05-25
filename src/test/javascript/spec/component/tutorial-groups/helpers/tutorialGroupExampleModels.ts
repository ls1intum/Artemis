import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import dayjs from 'dayjs/esm';

export const generateExampleTutorialGroup = ({
    id = 1,
    title = 'Example',
    capacity = 10,
    campus = 'Example Campus',
    language = 'ENGLISH',
    additionalInformation = 'Example Additional Information',
    isOnline = false,
    teachingAssistant = { id: 1, login: 'Example TA' } as User,
    isUserRegistered = false,
    isUserTutor = false,
    numberOfRegisteredUsers = 5,
    course = { id: 1, title: 'Test Course' } as Course,
    teachingAssistantName = 'Example TA',
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
    exampleTutorialGroup.courseTitle = courseTitle;
    exampleTutorialGroup.tutorialGroupSchedule = tutorialGroupSchedule;
    return exampleTutorialGroup;
};

export const tutorialGroupToTutorialGroupFormData = (entity: TutorialGroup): TutorialGroupFormData => {
    return {
        title: entity.title,
        capacity: entity.capacity,
        campus: entity.campus,
        language: entity.language,
        additionalInformation: entity.additionalInformation,
        isOnline: entity.isOnline,
        teachingAssistant: entity.teachingAssistant,
        schedule: {
            location: entity.tutorialGroupSchedule?.location,
            dayOfWeek: entity.tutorialGroupSchedule?.dayOfWeek,
            startTime: entity.tutorialGroupSchedule?.startTime,
            endTime: entity.tutorialGroupSchedule?.endTime,
            repetitionFrequency: entity.tutorialGroupSchedule?.repetitionFrequency,
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            period: [entity.tutorialGroupSchedule?.validFromInclusive?.toDate()!, entity.tutorialGroupSchedule?.validToInclusive?.toDate()!],
        },
    };
};
