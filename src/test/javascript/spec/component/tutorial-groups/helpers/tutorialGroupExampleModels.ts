import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course, Language } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';

export interface GenerateExampleTutorialGroupParams {
    id?: number;
    title?: string;
    capacity?: number;
    campus?: string;
    language?: Language;
    additionalInformation?: string;
    isOnline?: boolean;
    teachingAssistant?: User;
    isUserRegistered?: boolean;
    numberOfRegisteredUsers?: number;
    course?: Course;
}

export const generateExampleTutorialGroup = ({
    id = 1,
    title = 'Example',
    capacity = 10,
    campus = 'Example Campus',
    language = Language.ENGLISH,
    additionalInformation = 'Example Additional Information',
    isOnline = false,
    teachingAssistant = { id: 1, login: 'Example TA' } as User,
    isUserRegistered = false,
    numberOfRegisteredUsers = 5,
    course = { id: 1, title: 'Test Course' } as Course,
}: GenerateExampleTutorialGroupParams) => {
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
    exampleTutorialGroup.numberOfRegisteredUsers = numberOfRegisteredUsers;
    exampleTutorialGroup.course = course;
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
    };
};
