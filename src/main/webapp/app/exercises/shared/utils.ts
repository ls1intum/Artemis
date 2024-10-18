import { SafeHtml } from '@angular/platform-browser';
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { Detail } from 'app/detail-overview-list/detail.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';

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
        ],
    };
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
        ],
    };
}

export function getExerciseProblemDetailSection(formattedProblemStatement: SafeHtml | null, exercise: Exercise): DetailOverviewSection {
    const hasCompetencies = !!exercise.competencyLinks?.length;
    const details: Detail[] = [
        {
            title: hasCompetencies ? 'artemisApp.exercise.problemStatement' : undefined,
            type: DetailType.Markdown,
            data: { innerHtml: formattedProblemStatement },
        },
    ];

    if (hasCompetencies) {
        details.push({
            title: 'artemisApp.competency.link.title',
            type: DetailType.Text,
            data: { text: exercise.competencyLinks?.map((competencyLink) => competencyLink.competency?.title).join(', ') },
        });
    }
    return {
        headline: 'artemisApp.exercise.sections.problem',
        details: details,
    };
}

export function getExerciseGradingDefaultDetails(exercise: Exercise): Detail[] {
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
        exercise.type !== ExerciseType.QUIZ && { type: DetailType.Date, title: 'artemisApp.exercise.assessmentDueDate', data: { date: exercise.assessmentDueDate } },
        { type: DetailType.Text, title: 'artemisApp.exercise.points', data: { text: exercise.maxPoints } },
        !!exercise.bonusPoints && { type: DetailType.Text, title: 'artemisApp.exercise.bonusPoints', data: { text: exercise.bonusPoints } },
        includedInScore as Detail,
        exercise.type !== ExerciseType.QUIZ && {
            type: DetailType.Boolean,
            title: 'artemisApp.exercise.presentationScoreEnabled.title',
            data: { boolean: exercise.presentationScoreEnabled },
        },
    ];
}

export function getExerciseGradingInstructionsCriteriaDetails(exercise: Exercise, formattedGradingInstructions: SafeHtml | null): Detail[] {
    return [
        !!exercise.gradingInstructions && {
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

export function getExerciseMarkdownSolution(exercise: Exercise, formattedExampleSolution: SafeHtml | null): DetailOverviewSection {
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
