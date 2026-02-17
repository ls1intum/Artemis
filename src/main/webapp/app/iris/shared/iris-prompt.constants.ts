import { faBook, faLightbulb, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

export type PromptOptionKey = 'explainConcept' | 'quizTopic' | 'studyTips';

export const IRIS_PROMPT_CONFIGS: ReadonlyArray<{
    type: PromptOptionKey;
    icon: typeof faBook;
    labelKey: string;
    starterKey: string;
}> = [
    {
        type: 'explainConcept',
        icon: faBook,
        labelKey: 'artemisApp.iris.onboarding.step4.prompts.explainConcept',
        starterKey: 'artemisApp.iris.onboarding.step4.prompts.explainConceptStarter',
    },
    {
        type: 'quizTopic',
        icon: faQuestionCircle,
        labelKey: 'artemisApp.iris.onboarding.step4.prompts.quizTopic',
        starterKey: 'artemisApp.iris.onboarding.step4.prompts.quizTopicStarter',
    },
    {
        type: 'studyTips',
        icon: faLightbulb,
        labelKey: 'artemisApp.iris.onboarding.step4.prompts.studyTips',
        starterKey: 'artemisApp.iris.onboarding.step4.prompts.studyTipsStarter',
    },
];
