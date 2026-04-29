export enum ChatServiceMode {
    TEXT_EXERCISE = 'TEXT_EXERCISE_CHAT',
    PROGRAMMING_EXERCISE = 'PROGRAMMING_EXERCISE_CHAT',
    COURSE = 'COURSE_CHAT',
    LECTURE = 'LECTURE_CHAT',
    TUTOR_SUGGESTION = 'TUTOR_SUGGESTION',
}

export function chatModeToUrlComponent(mode: ChatServiceMode): string | undefined {
    switch (mode) {
        case ChatServiceMode.COURSE:
            return 'course-chat';
        case ChatServiceMode.LECTURE:
            return 'lecture-chat';
        case ChatServiceMode.PROGRAMMING_EXERCISE:
            return 'programming-exercise-chat';
        case ChatServiceMode.TEXT_EXERCISE:
            return 'text-exercise-chat';
        case ChatServiceMode.TUTOR_SUGGESTION:
            return 'tutor-suggestion';
        default:
            return undefined;
    }
}
