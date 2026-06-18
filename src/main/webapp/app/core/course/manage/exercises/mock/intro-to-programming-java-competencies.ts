import { CompetencyContributionCardDTO } from 'app/atlas/shared/entities/competency.model';

/**
 * Mock competencies for the experimental student exercise views. Linked to exercises so the competency
 * contribution cards appear below the problem statement, exactly as they do for real exercises.
 * Dev-only; no server-side counterpart.
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
