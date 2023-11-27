import { SafeHtml } from '@angular/platform-browser';
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';

export function getExerciseGeneralDetailsSection(exercise: Exercise): DetailOverviewSection {
    return {
        headline: 'artemisApp.exercise.sections.general',
        details: [
            exercise.course && {
                type: DetailType.Link,
                title: 'artemisApp.exercise.course',
                data: { text: exercise.course?.title, routerLink: ['/course-management', exercise.course?.id] },
            },
            exercise.exerciseGroup && {
                type: DetailType.Link,
                title: 'artemisApp.exercise.course',
                data: { text: exercise.exerciseGroup?.exam?.course?.title, routerLink: ['/course-management', exercise.exerciseGroup.exam?.course?.id] },
            },
            exercise.exerciseGroup && {
                type: DetailType.Link,
                title: 'artemisApp.exercise.exam',
                data: {
                    text: exercise.exerciseGroup.exam?.title,
                    routerLink: ['/course-management', exercise.exerciseGroup?.exam?.course?.id, 'exams', exercise.exerciseGroup?.exam?.id],
                },
            },
            {
                type: DetailType.Text,
                title: 'artemisApp.exercise.title',
                data: { text: exercise.title },
            },
            {
                type: DetailType.Text,
                title: 'artemisApp.exercise.categories',
                data: { text: exercise.categories?.map((category) => category.category?.toUpperCase()).join(', ') },
            },
        ].filter(Boolean),
    } as DetailOverviewSection;
}

export function getExerciseModeDetailSection(exercise: Exercise): DetailOverviewSection {
    return {
        headline: 'artemisApp.exercise.sections.mode',
        details: [
            {
                type: DetailType.Text,
                title: 'artemisApp.exercise.difficulty',
                data: { text: exercise.difficulty },
            },
            {
                type: DetailType.Text,
                title: 'artemisApp.exercise.mode',
                data: { text: exercise.mode },
            },
            exercise.teamAssignmentConfig && {
                type: DetailType.Text,
                title: 'artemisApp.exercise.teamAssignmentConfig.teamSize',
                data: { text: `Min. ${exercise.teamAssignmentConfig.minTeamSize}, Max. ${exercise.teamAssignmentConfig.maxTeamSize}` },
            },
        ].filter(Boolean),
    } as DetailOverviewSection;
}

export function getExerciseProblemDetailSection(formattedProblemStatement: SafeHtml | null): DetailOverviewSection {
    return {
        headline: 'artemisApp.exercise.sections.problem',
        details: [
            {
                type: DetailType.Markdown,
                data: { innerHtml: formattedProblemStatement },
            },
        ],
    } as DetailOverviewSection;
}

export function getExerciseGradingDefaultDetails(exercise: Exercise) {
    const includedInScoreIsBoolean = exercise.includedInOverallScore != IncludedInOverallScore.INCLUDED_AS_BONUS;
    const includedInScore = {
        type: includedInScoreIsBoolean ? DetailType.Boolean : DetailType.Text,
        title: 'artemisApp.exercise.includedInOverallScore',
        data: { text: 'BONUS', boolean: exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY },
    };
    return [
        { type: DetailType.Date, title: 'artemisApp.exercise.releaseDate', data: { date: exercise.releaseDate } },
        { type: DetailType.Date, title: 'artemisApp.exercise.startDate', data: { date: exercise.startDate } },
        { type: DetailType.Date, title: 'artemisApp.exercise.dueDate', data: { date: exercise.dueDate } },
        { type: DetailType.Date, title: 'artemisApp.exercise.assessmentDueDate', data: { date: exercise.assessmentDueDate } },
        { type: DetailType.Text, title: 'artemisApp.exercise.points', data: { text: exercise.maxPoints } },
        exercise.bonusPoints && { type: DetailType.Text, title: 'artemisApp.exercise.bonusPoints', data: { text: exercise.bonusPoints } },
        includedInScore,
        { type: DetailType.Boolean, title: 'artemisApp.exercise.presentationScoreEnabled.title', data: { boolean: exercise.presentationScoreEnabled } },
    ];
}

export function getExerciseGradingInstructionsCriteriaDetails(exercise: Exercise, formattedGradingInstructions: SafeHtml | null) {
    return [
        exercise.gradingInstructions && {
            type: DetailType.Markdown,
            title: 'artemisApp.exercise.assessmentInstructions',
            data: { innerHtml: formattedGradingInstructions },
        },
        exercise.gradingCriteria && {
            type: DetailType.GradingCriteria,
            title: 'artemisApp.exercise.structuredAssessmentInstructions',
            data: { gradingCriteria: exercise.gradingCriteria },
        },
    ];
}

export function getExerciseMarkdownSolution(exercise: Exercise, formattedExampleSolution: SafeHtml | null) {
    return {
        headline: 'artemisApp.exercise.sections.solution',
        details: [
            {
                title: 'artemisApp.exercise.sections.solution',
                type: DetailType.Markdown,
                data: { innerHtml: formattedExampleSolution },
            },
            {
                title: 'artemisApp.exercise.exampleSolutionPublicationDate',
                type: DetailType.Date,
                data: { date: exercise.exampleSolutionPublicationDate },
            },
        ],
    };
}
