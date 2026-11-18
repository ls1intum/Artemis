import { Signal, computed } from '@angular/core';
import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { faBrain, faCompass, faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

export interface IrisSuggestionChip {
    icon: IconDefinition;
    translationKey: string;
    starterKey: string;
}

const QUIZ_COURSE_CHIP: IrisSuggestionChip = {
    icon: faBrain,
    translationKey: 'artemisApp.iris.chat.suggestions.quiz',
    starterKey: 'artemisApp.iris.chat.suggestions.quizTopicStarter',
};

const QUIZ_LECTURE_CHIP: IrisSuggestionChip = {
    icon: faBrain,
    translationKey: 'artemisApp.iris.chat.suggestions.quiz',
    starterKey: 'artemisApp.iris.chat.suggestions.quizLectureStarter',
};

const QUIZ_EXERCISE_CHIP: IrisSuggestionChip = {
    icon: faBrain,
    translationKey: 'artemisApp.iris.chat.suggestions.quiz',
    starterKey: 'artemisApp.iris.chat.suggestions.quizExerciseStarter',
};

export const COURSE_SUGGESTION_CHIPS = [
    { icon: faGraduationCap, translationKey: 'artemisApp.iris.chat.suggestions.learn', starterKey: 'artemisApp.iris.chat.suggestions.learnStarter' },
    QUIZ_COURSE_CHIP,
    { icon: faCompass, translationKey: 'artemisApp.iris.chat.suggestions.tips', starterKey: 'artemisApp.iris.chat.suggestions.tipsStarter' },
] as const satisfies readonly IrisSuggestionChip[];

export const EXERCISE_SUGGESTION_CHIPS = [
    {
        icon: faGraduationCap,
        translationKey: 'artemisApp.iris.chat.suggestions.learn',
        starterKey: 'artemisApp.iris.chat.placeholders.exercise.walkThrough',
    },
    QUIZ_EXERCISE_CHIP,
    {
        icon: faCompass,
        translationKey: 'artemisApp.iris.chat.suggestions.tips',
        starterKey: 'artemisApp.iris.chat.placeholders.exercise.testsFailing',
    },
] as const satisfies readonly IrisSuggestionChip[];

export const LECTURE_SUGGESTION_CHIPS = [
    {
        icon: faGraduationCap,
        translationKey: 'artemisApp.iris.chat.suggestions.learn',
        starterKey: 'artemisApp.iris.chat.placeholders.lecture.keyPoints',
    },
    QUIZ_LECTURE_CHIP,
    {
        icon: faCompass,
        translationKey: 'artemisApp.iris.chat.suggestions.tips',
        starterKey: 'artemisApp.iris.chat.placeholders.lecture.whereToStart',
    },
] as const satisfies readonly IrisSuggestionChip[];

export function getActiveSuggestionChips(mode: ChatServiceMode | undefined): readonly IrisSuggestionChip[] {
    switch (mode) {
        case ChatServiceMode.COURSE:
            return COURSE_SUGGESTION_CHIPS;
        case ChatServiceMode.LECTURE:
            return LECTURE_SUGGESTION_CHIPS;
        case ChatServiceMode.TEXT_EXERCISE:
        case ChatServiceMode.PROGRAMMING_EXERCISE:
            return EXERCISE_SUGGESTION_CHIPS;
        default:
            return [];
    }
}

export function createActiveSuggestionChips(currentChatMode: Signal<ChatServiceMode | undefined>) {
    return computed(() => getActiveSuggestionChips(currentChatMode()));
}
