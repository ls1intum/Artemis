import { CompetencyContributionCardDTO, CompetencyTaxonomy, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import {
    CompetencyExerciseLinkResponseDTO,
    CourseCompetencyProgressDTO,
    CourseCompetencyResponseDTO,
    ExerciseForCompetencyDTO,
} from 'app/atlas/shared/dto/course-competency-response.dto';
import { INTRO_JAVA_ALL_EXERCISES, INTRO_JAVA_EXERCISE_GROUPS } from './intro-to-programming-java-exercises';

/**
 * Mock competencies for the experimental student exercise views. Linked to exercises (and to the Loops
 * exercise group's variants) so the competency contribution cards appear below the problem statement,
 * exactly as they do for real exercises. Dev-only; no server-side counterpart.
 */
interface MockCompetency {
    competencyId: number;
    title: string;
}

// Competency ids are arbitrary and kept well clear of the mock exercise ids.
const COMP_LOOPS: MockCompetency = { competencyId: 9001, title: 'Loops & Iteration' };
const COMP_ARRAYS: MockCompetency = { competencyId: 9002, title: 'Arrays & Collections' };
const COMP_OOP: MockCompetency = { competencyId: 9003, title: 'Object-Oriented Design' };
const COMP_RECURSION: MockCompetency = { competencyId: 9004, title: 'Recursion & Self-Reference' };
const COMP_TEAM: MockCompetency = { competencyId: 9005, title: 'Collaborative Software Development' };
const COMP_CONTROL_FLOW: MockCompetency = { competencyId: 9006, title: 'Control Flow & Conditionals' };
const COMP_ALGORITHM: MockCompetency = { competencyId: 9007, title: 'Algorithm Design' };
const COMP_DATA_STRUCT: MockCompetency = { competencyId: 9008, title: 'Data Structures' };
const COMP_COMPLEXITY: MockCompetency = { competencyId: 9009, title: 'Complexity Analysis' };
const COMP_SW_ARCH: MockCompetency = { competencyId: 9010, title: 'Software Architecture' };

/** All mock competencies available in the course — used by the group edit UI. */
export const ALL_MOCK_COMPETENCIES: readonly MockCompetency[] = [
    COMP_LOOPS,
    COMP_CONTROL_FLOW,
    COMP_ALGORITHM,
    COMP_ARRAYS,
    COMP_DATA_STRUCT,
    COMP_RECURSION,
    COMP_COMPLEXITY,
    COMP_OOP,
    COMP_SW_ARCH,
    COMP_TEAM,
];

function contribution(competency: MockCompetency, weight: number, mastery?: number): CompetencyContributionCardDTO {
    const card = new CompetencyContributionCardDTO();
    card.competencyId = competency.competencyId;
    card.title = competency.title;
    card.weight = weight;
    card.mastery = mastery;
    return card;
}

/**
 * Which exercises contribute to which competencies. The Loops exercise group's three variants
 * (1001/1002/1003) all link to the "Loops & Iteration" competency — i.e. the group has a competency —
 * and a few standalone exercises link to competencies as well.
 */
const CONTRIBUTIONS_BY_EXERCISE_ID: Record<number, CompetencyContributionCardDTO[]> = {
    // Each exercise group's variants link to the group's primary competency plus two supporting ones.
    // Loops group (Cars / Planes / Robots).
    1001: [contribution(COMP_LOOPS, 1, 40), contribution(COMP_CONTROL_FLOW, 0.5, 55), contribution(COMP_ALGORITHM, 0.25, 30)],
    1002: [contribution(COMP_LOOPS, 1, 25), contribution(COMP_CONTROL_FLOW, 0.5, 40), contribution(COMP_ALGORITHM, 0.25, 20)],
    1003: [contribution(COMP_LOOPS, 1, 10), contribution(COMP_CONTROL_FLOW, 0.5, 20), contribution(COMP_ALGORITHM, 0.25, 10)],
    // Arrays and Lists group (Cars / Planes).
    1011: [contribution(COMP_ARRAYS, 1, 35), contribution(COMP_DATA_STRUCT, 0.5, 25), contribution(COMP_ALGORITHM, 0.25, 20)],
    1012: [contribution(COMP_ARRAYS, 1, 20), contribution(COMP_DATA_STRUCT, 0.5, 15), contribution(COMP_ALGORITHM, 0.25, 10)],
    // Recursion group (Planes / Robots).
    1021: [contribution(COMP_RECURSION, 1, 15), contribution(COMP_ALGORITHM, 1, 20), contribution(COMP_COMPLEXITY, 0.5, 10)],
    1022: [contribution(COMP_RECURSION, 1, 5), contribution(COMP_ALGORITHM, 1, 10), contribution(COMP_COMPLEXITY, 0.5, 5)],
    // Team Project group (Basic / Advanced).
    1101: [contribution(COMP_TEAM, 1, 10), contribution(COMP_OOP, 0.5, 25), contribution(COMP_SW_ARCH, 0.5, 15)],
    1102: [contribution(COMP_TEAM, 1, 5), contribution(COMP_OOP, 0.5, 20), contribution(COMP_SW_ARCH, 0.5, 10)],
    // Standalone exercises with linked competencies.
    104: [contribution(COMP_LOOPS, 0.5, 60), contribution(COMP_CONTROL_FLOW, 0.5, 70), contribution(COMP_ALGORITHM, 0.25, 45)],
    105: [contribution(COMP_ARRAYS, 1, 30), contribution(COMP_DATA_STRUCT, 0.5, 20), contribution(COMP_ALGORITHM, 0.25, 15)],
    108: [contribution(COMP_OOP, 1, 20), contribution(COMP_SW_ARCH, 0.5, 10), contribution(COMP_ARRAYS, 0.25, 30)],
    109: [contribution(COMP_OOP, 1, 15), contribution(COMP_SW_ARCH, 0.5, 10), contribution(COMP_COMPLEXITY, 0.25, 5)],
    110: [contribution(COMP_OOP, 0.5), contribution(COMP_SW_ARCH, 0.5), contribution(COMP_COMPLEXITY, 0.25)],
};

export function getMockCompetencyContributions(exerciseId: number): CompetencyContributionCardDTO[] {
    return CONTRIBUTIONS_BY_EXERCISE_ID[exerciseId] ?? [];
}

// Lookup: exerciseId → { groupId, groupTitle } for exercises that belong to a group.
const EXERCISE_GROUP_INFO: Record<number, { groupId: number; groupTitle: string }> = {};
for (const group of INTRO_JAVA_EXERCISE_GROUPS) {
    for (const ex of group.exercises ?? []) {
        if (ex.id !== undefined && group.id !== undefined && group.title !== undefined) {
            EXERCISE_GROUP_INFO[ex.id] = { groupId: group.id, groupTitle: group.title };
        }
    }
}

// Reverse map: competencyId → exercises that contribute to it (with weight).
// Built from CONTRIBUTIONS_BY_EXERCISE_ID so the two sources stay in sync.
const EXERCISE_LINKS_BY_COMPETENCY_ID: Record<number, CompetencyExerciseLinkResponseDTO[]> = {};
for (const [exerciseIdStr, contributions] of Object.entries(CONTRIBUTIONS_BY_EXERCISE_ID)) {
    const exerciseId = Number(exerciseIdStr);
    const exercise = INTRO_JAVA_ALL_EXERCISES.find((e) => e.id === exerciseId);
    if (!exercise) continue;
    const exerciseDTO: ExerciseForCompetencyDTO = {
        id: exercise.id!,
        title: exercise.title,
        shortName: exercise.shortName,
        type: exercise.type,
        maxPoints: exercise.maxPoints,
        bonusPoints: exercise.bonusPoints,
        difficulty: exercise.difficulty,
        mode: exercise.mode,
        includedInOverallScore: exercise.includedInOverallScore,
    };
    for (const c of contributions) {
        const cid = c.competencyId!;
        const link: CompetencyExerciseLinkResponseDTO = { weight: c.weight ?? 1, exercise: exerciseDTO };
        const groupInfo = EXERCISE_GROUP_INFO[exerciseId];
        if (groupInfo) {
            link.groupId = groupInfo.groupId;
            link.groupTitle = groupInfo.groupTitle;
        }
        (EXERCISE_LINKS_BY_COMPETENCY_ID[cid] ??= []).push(link);
    }
}

const TAXONOMIES: CompetencyTaxonomy[] = [
    CompetencyTaxonomy.APPLY,
    CompetencyTaxonomy.UNDERSTAND,
    CompetencyTaxonomy.ANALYZE,
    CompetencyTaxonomy.APPLY,
    CompetencyTaxonomy.UNDERSTAND,
    CompetencyTaxonomy.EVALUATE,
    CompetencyTaxonomy.ANALYZE,
    CompetencyTaxonomy.CREATE,
    CompetencyTaxonomy.EVALUATE,
    CompetencyTaxonomy.APPLY,
];

const DESCRIPTIONS: string[] = [
    'Students can write iterative solutions using for, while, and do-while loops.',
    'Students understand collections and can manipulate arrays and lists.',
    'Students can design and implement classes, objects, and inheritance hierarchies.',
    'Students can solve problems by breaking them into self-referential sub-problems.',
    'Students can work effectively in a team using version control and code review.',
    'Students can write conditionals and understand branching logic.',
    'Students can design efficient algorithms and reason about correctness.',
    'Students know common data structures and when to apply each.',
    'Students can analyse time and space complexity using Big-O notation.',
    'Students can decompose a problem into well-defined, loosely coupled modules.',
];

const MOCK_COURSE_INFO = { id: 1, title: 'Introduction to Programming in Java', semester: 'SS25' };

/** Mock competency list for the instructor competency-management page and student competencies view. */
export const MOCK_COURSE_COMPETENCY_RESPONSES: CourseCompetencyResponseDTO[] = ALL_MOCK_COMPETENCIES.map((comp, index) => ({
    id: comp.competencyId,
    title: comp.title,
    description: DESCRIPTIONS[index],
    type: CourseCompetencyType.COMPETENCY,
    taxonomy: TAXONOMIES[index],
    masteryThreshold: 80,
    optional: index >= 8,
    course: MOCK_COURSE_INFO,
    exerciseLinks: EXERCISE_LINKS_BY_COMPETENCY_ID[comp.competencyId] ?? [],
}));

/** Mock course-level progress for the instructor competency-management page. */
export const MOCK_COMPETENCY_PROGRESS: CourseCompetencyProgressDTO[] = ALL_MOCK_COMPETENCIES.map((comp, index) => ({
    competencyId: comp.competencyId,
    numberOfStudents: 120,
    numberOfMasteredStudents: Math.round(120 * (0.3 + index * 0.05)),
    averageStudentScore: Math.round(40 + index * 5),
}));
