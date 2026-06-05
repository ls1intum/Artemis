import dayjs from 'dayjs/esm';
import { DifficultyLevel, Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';

export type PlacementChoice = 'existing-group' | 'new-group' | 'standalone';

export interface GenerationStepDef {
    label: string;
    loop?: boolean; // part of the iterative build/verify loop
}

export const GENERATION_STEPS: GenerationStepDef[] = [
    { label: 'Analysing source exercise' },
    { label: 'Adapting problem statement' },
    { label: 'Building solution repository', loop: true },
    { label: 'Building template repository', loop: true },
    { label: 'Building test repository', loop: true },
    { label: 'Verifying 100% test score', loop: true },
    { label: 'Consistency check', loop: true },
    { label: 'Finalising' },
];
export const STEP_INTERVAL_MS = 700;

const DOMAIN_SUFFIXES: Array<[string, string]> = [
    ['space', 'Tracking Satellite Orbits'],
    ['rocket', 'Counting Rocket Launches'],
    ['cook', 'Managing Recipe Ingredients'],
    ['restaurant', 'Processing Restaurant Orders'],
    ['sport', 'Counting Goals in a Match'],
    ['music', 'Processing Playlist Entries'],
    ['hospital', 'Managing Patient Queues'],
    ['bank', 'Processing Bank Transactions'],
    ['school', 'Tracking Student Grades'],
    ['library', 'Managing Book Checkouts'],
    ['traffic', 'Monitoring City Traffic'],
    ['train', 'Scheduling Train Departures'],
    ['ship', 'Routing Cargo Ships'],
    ['farm', 'Managing Crop Yields'],
    ['game', 'Processing Game Scores'],
];

function deriveSuffix(domainText: string): string {
    const lower = domainText.toLowerCase();
    for (const [key, label] of DOMAIN_SUFFIXES) {
        if (lower.includes(key)) return label;
    }
    if (domainText.trim()) {
        return domainText.trim().charAt(0).toUpperCase() + domainText.trim().slice(1);
    }
    return 'Alternative Scenario';
}

function mockProblemStatement(title: string): string {
    return (
        `## ${title}\n\n` +
        'In this exercise you apply the same algorithmic pattern as in the source exercise, but in a different real-world context.\n\n' +
        '### Background\n' +
        'Read through the scenario carefully and make sure you understand the expected inputs and outputs before coding.\n\n' +
        '### Your tasks\n' +
        '1. Analyse the problem and sketch your approach on paper.\n' +
        '2. Implement the required methods step by step.\n' +
        '3. Verify your implementation against the provided sample cases.\n\n' +
        '### Example\n' +
        '```\n' +
        'Input:  [4, 2, 8, 1]\n' +
        'Output: 15\n' +
        '```\n\n' +
        '> **Hint:** Pay close attention to edge cases — what happens when the input is empty or contains only one element?'
    );
}

export function generateVariant(
    source: Exercise,
    opts: {
        changeDifficulty: boolean;
        targetDifficulty: DifficultyLevel;
        changeDomain: boolean;
        domainText: string;
    },
): ProgrammingExercise {
    const topic = (source.title ?? 'Exercise').split(':')[0].trim();
    const suffix = opts.changeDomain && opts.domainText.trim() ? deriveSuffix(opts.domainText) : 'AI Variant';
    const newTitle = `${topic}: ${suffix}`;

    const variant = new ProgrammingExercise(undefined, undefined);
    Object.assign(variant, source);

    variant.id = 9000 + Math.floor(Math.random() * 900);
    variant.title = newTitle;
    variant.shortName = topic.replace(/\s+/g, '').substring(0, 5).toUpperCase() + 'V';

    if (opts.changeDifficulty) {
        variant.difficulty = opts.targetDifficulty;
    }

    const durationDays = source.dueDate && source.releaseDate ? Math.max(1, source.dueDate.diff(source.releaseDate, 'day')) : 7;

    const start = source.releaseDate ?? dayjs();
    variant.releaseDate = start;
    variant.startDate = start;
    variant.dueDate = start.add(durationDays, 'day');
    variant.assessmentDueDate = start.add(durationDays + 3, 'day');

    variant.programmingLanguage = ProgrammingLanguage.JAVA;
    variant.projectType = ProjectType.MAVEN_MAVEN;
    variant.assessmentType = AssessmentType.AUTOMATIC;
    variant.categories = [new ExerciseCategory('Java', '#6f42c1'), new ExerciseCategory('Algorithms', '#198754')];
    variant.problemStatement = mockProblemStatement(newTitle);

    return variant;
}

export function difficultyLabel(d: DifficultyLevel): string {
    return d.charAt(0) + d.slice(1).toLowerCase();
}

export function difficultyBadgeClass(d: DifficultyLevel | undefined): string {
    switch (d) {
        case DifficultyLevel.EASY:
            return 'bg-success';
        case DifficultyLevel.MEDIUM:
            return 'bg-warning';
        case DifficultyLevel.HARD:
            return 'bg-danger';
        default:
            return 'bg-secondary';
    }
}

export function durationDays(exercise: Exercise): number {
    if (exercise.releaseDate && exercise.dueDate) {
        return Math.max(1, exercise.dueDate.diff(exercise.releaseDate, 'day'));
    }
    return 7;
}
