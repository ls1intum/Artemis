import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardUser, faCheckDouble, faDiagramProject, faFileArrowUp, faFont, faGraduationCap, faKeyboard, faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { CalendarEventTypeEnum } from 'app/openapi/models/calendar-event';

const eventTypeIconMap: Record<CalendarEventTypeEnum, IconProp> = {
    ['LECTURE']: faChalkboardUser,
    ['TUTORIAL']: faPersonChalkboard,
    ['EXAM']: faGraduationCap,
    ['QUIZ_EXERCISE']: faCheckDouble,
    ['TEXT_EXERCISE']: faFont,
    ['MODELING_EXERCISE']: faDiagramProject,
    ['PROGRAMMING_EXERCISE']: faKeyboard,
    ['FILE_UPLOAD_EXERCISE']: faFileArrowUp,
};

export function getIconForEventType(type: CalendarEventTypeEnum): IconProp {
    return eventTypeIconMap[type];
}

export function getWeekdayNameKeys(): string[] {
    return [
        'global.weekdays.mondayShort',
        'global.weekdays.tuesdayShort',
        'global.weekdays.wednesdayShort',
        'global.weekdays.thursdayShort',
        'global.weekdays.fridayShort',
        'global.weekdays.saturdayShort',
        'global.weekdays.sundayShort',
    ];
}

export enum CalendarEventFilterOption {
    ExamEvents = 'examEvents',
    LectureEvents = 'lectureEvents',
    TutorialEvents = 'tutorialEvents',
    ExerciseEvents = 'exerciseEvents',
}

const eventTypeToColorClassMap: Record<CalendarEventTypeEnum, string> = {
    ['EXAM']: 'var(--pastel-teal)',
    ['LECTURE']: 'var(--pastel-blue)',
    ['TUTORIAL']: 'var(--pastel-purple)',
    ['TEXT_EXERCISE']: 'var(--pastel-cyan)',
    ['MODELING_EXERCISE']: 'var(--pastel-cyan)',
    ['PROGRAMMING_EXERCISE']: 'var(--pastel-cyan)',
    ['FILE_UPLOAD_EXERCISE']: 'var(--pastel-cyan)',
    ['QUIZ_EXERCISE']: 'var(--pastel-cyan)',
};

export function getColorForEventType(type: CalendarEventTypeEnum): string {
    return eventTypeToColorClassMap[type];
}
