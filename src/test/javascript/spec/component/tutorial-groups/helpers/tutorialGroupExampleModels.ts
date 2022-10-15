import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Language } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';
import dayjs from 'dayjs/esm';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';

export const generateExampleTutorialGroup = (id?: number) => {
    const exampleTutorialGroup = new TutorialGroup();
    exampleTutorialGroup.id = id ?? 1;
    exampleTutorialGroup.title = 'Example';
    exampleTutorialGroup.capacity = 10;
    exampleTutorialGroup.campus = 'Example Campus';
    exampleTutorialGroup.language = Language.GERMAN;
    exampleTutorialGroup.additionalInformation = 'Example Information';
    exampleTutorialGroup.isOnline = true;
    exampleTutorialGroup.teachingAssistant = { id: 1, login: 'Example' } as User;
    exampleTutorialGroup.tutorialGroupSchedule = new TutorialGroupSchedule();
    exampleTutorialGroup.tutorialGroupSchedule.id = 1;
    exampleTutorialGroup.tutorialGroupSchedule.dayOfWeek = 1;
    exampleTutorialGroup.tutorialGroupSchedule.startTime = '10:00:00';
    exampleTutorialGroup.tutorialGroupSchedule.endTime = '11:00:00';
    exampleTutorialGroup.tutorialGroupSchedule.repetitionFrequency = 1;
    exampleTutorialGroup.tutorialGroupSchedule.location = 'Example Location';
    exampleTutorialGroup.tutorialGroupSchedule.validFromInclusive = dayjs('2021-01-01');
    exampleTutorialGroup.tutorialGroupSchedule.validToInclusive = dayjs('2021-01-31');
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
            period: [entity.tutorialGroupSchedule?.validFromInclusive?.toDate()!, entity.tutorialGroupSchedule?.validToInclusive?.toDate()!],
        },
    };
};
