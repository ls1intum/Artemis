import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardUser, faCheckDouble, faDiagramProject, faFileArrowUp, faFont, faGraduationCap, faKeyboard, faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { CalendarEvent } from 'app/openapi/model/calendarEvent';

const eventTypeIconMap: Record<CalendarEvent.TypeEnum, IconProp> = {
    [CalendarEvent.TypeEnum.Lecture]: faChalkboardUser,
    [CalendarEvent.TypeEnum.Tutorial]: faPersonChalkboard,
    [CalendarEvent.TypeEnum.Exam]: faGraduationCap,
    [CalendarEvent.TypeEnum.QuizExercise]: faCheckDouble,
    [CalendarEvent.TypeEnum.TextExercise]: faFont,
    [CalendarEvent.TypeEnum.ModelingExercise]: faDiagramProject,
    [CalendarEvent.TypeEnum.ProgrammingExercise]: faKeyboard,
    [CalendarEvent.TypeEnum.FileUploadExercise]: faFileArrowUp,
};

export function getIconForEventType(type: CalendarEvent.TypeEnum): IconProp {
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

const eventTypeToColorClassMap: Record<CalendarEvent.TypeEnum, string> = {
    [CalendarEvent.TypeEnum.Exam]: 'var(--pastel-teal)',
    [CalendarEvent.TypeEnum.Lecture]: 'var(--pastel-blue)',
    [CalendarEvent.TypeEnum.Tutorial]: 'var(--pastel-purple)',
    [CalendarEvent.TypeEnum.TextExercise]: 'var(--pastel-cyan)',
    [CalendarEvent.TypeEnum.ModelingExercise]: 'var(--pastel-cyan)',
    [CalendarEvent.TypeEnum.ProgrammingExercise]: 'var(--pastel-cyan)',
    [CalendarEvent.TypeEnum.FileUploadExercise]: 'var(--pastel-cyan)',
    [CalendarEvent.TypeEnum.QuizExercise]: 'var(--pastel-cyan)',
};

export function getColorForEventType(type: CalendarEvent.TypeEnum): string {
    return eventTypeToColorClassMap[type];
}
