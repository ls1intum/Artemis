import { Dayjs } from 'dayjs/esm';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardUser, faCheckDouble, faDiagramProject, faFileArrowUp, faFont, faGraduationCap, faKeyboard, faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';

export function getTimeString(timestamp: Dayjs): string {
    return timestamp.format('HH:mm');
}

const eventTypeIconMap: Record<CalendarEventType, IconProp> = {
    [CalendarEventType.Lecture]: faChalkboardUser,
    [CalendarEventType.Tutorial]: faPersonChalkboard,
    [CalendarEventType.Exam]: faGraduationCap,
    [CalendarEventType.QuizExercise]: faCheckDouble,
    [CalendarEventType.TextExercise]: faFont,
    [CalendarEventType.ModelingExercise]: faDiagramProject,
    [CalendarEventType.ProgrammingExercise]: faKeyboard,
    [CalendarEventType.FileUploadExercise]: faFileArrowUp,
};

export function getIconForEvent(event: CalendarEvent): IconProp {
    return eventTypeIconMap[event.type];
}

export function getWeekDayNameKeys(): string[] {
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

const eventTypeNameKeyMap: Record<CalendarEventType, string> = {
    [CalendarEventType.Lecture]: 'artemisApp.calendar.eventTypeName.lecture',
    [CalendarEventType.Tutorial]: 'artemisApp.calendar.eventTypeName.tutorial',
    [CalendarEventType.Exam]: 'artemisApp.calendar.eventTypeName.exam',
    [CalendarEventType.QuizExercise]: 'artemisApp.calendar.eventTypeName.quiz',
    [CalendarEventType.TextExercise]: 'artemisApp.calendar.eventTypeName.text',
    [CalendarEventType.ModelingExercise]: 'artemisApp.calendar.eventTypeName.modeling',
    [CalendarEventType.ProgrammingExercise]: 'artemisApp.calendar.eventTypeName.programming',
    [CalendarEventType.FileUploadExercise]: 'artemisApp.calendar.eventTypeName.fileUpload',
};

export function getEventTypeNameKey(event: CalendarEvent): string {
    return eventTypeNameKeyMap[event.type];
}

export enum CalendarEventFilterOption {
    ExamEvents = 'examEvents',
    LectureEvents = 'lectureEvents',
    TutorialEvents = 'tutorialEvents',
    ExerciseEvents = 'exerciseEvents',
}

const eventTypeToColorClassMap: Record<CalendarEventType, string> = {
    [CalendarEventType.Exam]: 'var(--pastel-teal)',
    [CalendarEventType.Lecture]: 'var(--pastel-blue)',
    [CalendarEventType.Tutorial]: 'var(--pastel-purple)',
    [CalendarEventType.TextExercise]: 'var(--pastel-cyan)',
    [CalendarEventType.ModelingExercise]: 'var(--pastel-cyan)',
    [CalendarEventType.ProgrammingExercise]: 'var(--pastel-cyan)',
    [CalendarEventType.FileUploadExercise]: 'var(--pastel-cyan)',
    [CalendarEventType.QuizExercise]: 'var(--pastel-cyan)',
};

export function getColorFor(event: CalendarEvent): string {
    return eventTypeToColorClassMap[event.type];
}
