import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { DifficultyLevel, Exercise, ExerciseMode, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { UMLDiagramType } from '@tumaet/apollon';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';

const cat = (name: string, color: string) => new ExerciseCategory(name, color);
import { CourseExerciseGroup, ExerciseRelation, ExerciseRelationEndpointKind, ExerciseRelationType } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';

// Semester: Summer 2026  (2026-04-13 – 2026-07-24)
const WEEK = (offsetWeeks: number): dayjs.Dayjs => dayjs('2026-04-13').add(offsetWeeks, 'week');

function base(id: number, title: string, shortName: string, week: number, durationDays = 7): Partial<Exercise> {
    return {
        id,
        title,
        shortName,
        releaseDate: WEEK(week),
        startDate: WEEK(week),
        dueDate: WEEK(week).add(durationDays, 'day'),
        assessmentDueDate: WEEK(week).add(durationDays + 3, 'day'),
        mode: ExerciseMode.INDIVIDUAL,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    };
}

// ── Programming exercises ──────────────────────────────────────────────────────

function prog(id: number, title: string, shortName: string, week: number, points: number, difficulty: DifficultyLevel): ProgrammingExercise {
    const ex = new ProgrammingExercise(undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week));
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = difficulty;
    ex.programmingLanguage = ProgrammingLanguage.JAVA;
    ex.projectType = ProjectType.MAVEN_MAVEN;
    ex.packageName = 'de.tum.cit.aet.intro';
    ex.allowOnlineEditor = true;
    ex.allowOfflineIde = true;
    ex.staticCodeAnalysisEnabled = true;
    ex.maxStaticCodeAnalysisPenalty = 20;
    ex.assessmentType = AssessmentType.AUTOMATIC;
    ex.assessmentDueDate = undefined;
    ex.categories = [cat('Java', '#6f42c1')];
    return ex;
}

// A variant of a programming exercise: same topic as its siblings, differing only in difficulty,
// time effort (durationDays), and application theme (reflected in the title).
function progVariant(id: number, title: string, shortName: string, week: number, points: number, difficulty: DifficultyLevel, durationDays: number): ProgrammingExercise {
    const ex = new ProgrammingExercise(undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week, durationDays));
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = difficulty;
    ex.programmingLanguage = ProgrammingLanguage.JAVA;
    ex.projectType = ProjectType.MAVEN_MAVEN;
    ex.packageName = 'de.tum.cit.aet.intro';
    ex.allowOnlineEditor = true;
    ex.allowOfflineIde = true;
    ex.staticCodeAnalysisEnabled = true;
    ex.maxStaticCodeAnalysisPenalty = 20;
    ex.assessmentType = AssessmentType.AUTOMATIC;
    ex.assessmentDueDate = undefined;
    ex.categories = [cat('Java', '#6f42c1'), cat('Algorithms', '#198754')];
    return ex;
}

// ── Variant exercises (grouped) ──────────────────────────────────────────────────
// Within each group the exercises cover the SAME lecture topic; they differ only in difficulty,
// time effort, and application theme (cars / planes / robots — same underlying pattern).

// Topic: Loops
const LOOPS_CARS = progVariant(1001, 'Loops: Counting Cars at a Toll Booth', 'LOOPC', 3, 10, DifficultyLevel.EASY, 7);
const LOOPS_PLANES = progVariant(1002, 'Loops: Boarding Flights by Group', 'LOOPP', 3, 10, DifficultyLevel.MEDIUM, 10);
const LOOPS_ROBOTS = progVariant(1003, 'Loops: Driving Warehouse Robots', 'LOOPR', 3, 10, DifficultyLevel.HARD, 14);

// Topic: Arrays and ArrayLists
const ARRAYS_CARS = progVariant(1011, 'Arrays: Parking-Lot Inventory', 'ARRC', 4, 15, DifficultyLevel.EASY, 7);
const ARRAYS_PLANES = progVariant(1012, 'Arrays: Seat Maps for an Airline', 'ARRP', 4, 15, DifficultyLevel.MEDIUM, 10);

// Topic: Methods and Recursion
const RECURSION_PLANES = progVariant(1021, 'Recursion: Flight-Connection Search', 'RECP', 5, 15, DifficultyLevel.MEDIUM, 10);
const RECURSION_ROBOTS = progVariant(1022, 'Recursion: Robot Maze Solver', 'RECR', 5, 15, DifficultyLevel.HARD, 14);

// ── Text exercises ─────────────────────────────────────────────────────────────

function text(id: number, title: string, shortName: string, week: number, points: number): TextExercise {
    const ex = new TextExercise(undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week, 10));
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = DifficultyLevel.EASY;
    ex.assessmentType = AssessmentType.MANUAL;
    ex.categories = [cat('Writing', '#fd7e14')];
    return ex;
}

// ── Modeling exercises ─────────────────────────────────────────────────────────

function modeling(id: number, title: string, shortName: string, week: number, points: number, diagramType: UMLDiagramType): ModelingExercise {
    const ex = new ModelingExercise(diagramType, undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week, 10));
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = DifficultyLevel.MEDIUM;
    ex.assessmentType = AssessmentType.SEMI_AUTOMATIC;
    ex.categories = [cat('UML', '#0dcaf0')];
    return ex;
}

// ── Quiz exercises ─────────────────────────────────────────────────────────────

function quiz(id: number, title: string, shortName: string, week: number, points: number, mode = QuizMode.SYNCHRONIZED, status = QuizStatus.VISIBLE): QuizExercise {
    const ex = new QuizExercise(undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week, 1));
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = DifficultyLevel.EASY;
    ex.quizMode = mode;
    ex.status = status;
    ex.randomizeQuestionOrder = true;
    ex.duration = 30;
    ex.categories = [cat('Theory', '#6c757d')];
    return ex;
}

// ── File-upload exercises ──────────────────────────────────────────────────────

function fileUpload(id: number, title: string, shortName: string, week: number, points: number): FileUploadExercise {
    const ex = new FileUploadExercise(undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week, 14));
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = DifficultyLevel.EASY;
    ex.assessmentType = AssessmentType.MANUAL;
    ex.filePattern = 'pdf';
    ex.categories = [cat('Worksheet', '#adb5bd')];
    return ex;
}

// ── Team exercises ─────────────────────────────────────────────────────────────

function teamConfig(min: number, max: number): TeamAssignmentConfig {
    const cfg = new TeamAssignmentConfig();
    cfg.minTeamSize = min;
    cfg.maxTeamSize = max;
    return cfg;
}

function teamProg(id: number, title: string, shortName: string, week: number, points: number, difficulty: DifficultyLevel, min = 2, max = 4): ProgrammingExercise {
    const ex = new ProgrammingExercise(undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week, 14));
    ex.mode = ExerciseMode.TEAM;
    ex.teamAssignmentConfig = teamConfig(min, max);
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = difficulty;
    ex.programmingLanguage = ProgrammingLanguage.JAVA;
    ex.projectType = ProjectType.MAVEN_MAVEN;
    ex.packageName = 'de.tum.cit.aet.intro';
    ex.allowOnlineEditor = true;
    ex.allowOfflineIde = true;
    ex.staticCodeAnalysisEnabled = false;
    ex.assessmentType = AssessmentType.SEMI_AUTOMATIC;
    return ex;
}

function teamText(id: number, title: string, shortName: string, week: number, points: number, min = 2, max = 4): TextExercise {
    const ex = new TextExercise(undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week, 10));
    ex.mode = ExerciseMode.TEAM;
    ex.teamAssignmentConfig = teamConfig(min, max);
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = DifficultyLevel.MEDIUM;
    ex.assessmentType = AssessmentType.MANUAL;
    return ex;
}

// Team programming variants — same project scope, different complexity levels
const TEAM_PROJECT_BASIC = teamProg(1101, 'Team Project: Library Management System (Basic)', 'TPLIB1', 8, 30, DifficultyLevel.EASY, 2, 3);
const TEAM_PROJECT_ADVANCED = teamProg(1102, 'Team Project: Library Management System (Advanced)', 'TPLIB2', 8, 30, DifficultyLevel.HARD, 3, 5);

// ── Exercise catalogue ─────────────────────────────────────────────────────────

const progWithBonus = (id: number, title: string, shortName: string, week: number, points: number, bonus: number, difficulty: DifficultyLevel): ProgrammingExercise => {
    const ex = prog(id, title, shortName, week, points, difficulty);
    ex.bonusPoints = bonus;
    return ex;
};

const withCategories = (ex: ProgrammingExercise, categories: ExerciseCategory[]): ProgrammingExercise => {
    ex.categories = categories;
    return ex;
};

export const INTRO_JAVA_PROGRAMMING_EXERCISES: ProgrammingExercise[] = [
    prog(101, 'Hello World', 'HW', 0, 5, DifficultyLevel.EASY),
    prog(102, 'Variables and Data Types', 'VDT', 1, 10, DifficultyLevel.EASY),
    progWithBonus(103, 'Control Flow: if/else and switch', 'CF1', 2, 10, 2, DifficultyLevel.EASY),
    prog(104, 'Loops: for, while, do-while', 'LOOP', 3, 10, DifficultyLevel.EASY),
    prog(105, 'Arrays and ArrayLists', 'ARR', 4, 15, DifficultyLevel.MEDIUM),
    progWithBonus(106, 'Methods and Recursion', 'REC', 5, 15, 5, DifficultyLevel.MEDIUM),
    prog(107, 'String Manipulation', 'STR', 6, 10, DifficultyLevel.EASY),
    withCategories(prog(108, 'Classes and Objects', 'OOP1', 7, 20, DifficultyLevel.MEDIUM), [cat('Java', '#6f42c1'), cat('OOP', '#0d6efd')]),
    withCategories(prog(109, 'Inheritance and Polymorphism', 'OOP2', 9, 20, DifficultyLevel.MEDIUM), [cat('Java', '#6f42c1'), cat('OOP', '#0d6efd')]),
    withCategories(prog(110, 'Interfaces and Abstract Classes', 'OOP3', 10, 20, DifficultyLevel.MEDIUM), [cat('Java', '#6f42c1'), cat('OOP', '#0d6efd')]),
    prog(111, 'Exception Handling', 'EXC', 11, 15, DifficultyLevel.MEDIUM),
    withCategories(prog(112, 'Collections and Generics', 'COL', 12, 20, DifficultyLevel.HARD), [cat('Java', '#6f42c1'), cat('Data Structures', '#dc3545')]),
    prog(113, 'File I/O and Streams', 'IO', 13, 15, DifficultyLevel.HARD),
    withCategories(prog(114, 'Final Project: Student Grade Manager', 'PROJ', 14, 40, DifficultyLevel.HARD), [cat('Java', '#6f42c1'), cat('Project', '#20c997')]),
    LOOPS_CARS,
    LOOPS_PLANES,
    LOOPS_ROBOTS,
    ARRAYS_CARS,
    ARRAYS_PLANES,
    RECURSION_PLANES,
    RECURSION_ROBOTS,
    TEAM_PROJECT_BASIC,
    TEAM_PROJECT_ADVANCED,
];

const textBonus = (id: number, title: string, shortName: string, week: number, points: number): TextExercise => {
    const ex = text(id, title, shortName, week, points);
    ex.bonusPoints = points;
    ex.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
    return ex;
};

export const INTRO_JAVA_TEXT_EXERCISES: TextExercise[] = [
    text(201, 'Reflection: Programming Paradigms', 'RPP', 1, 5),
    text(202, 'Explain Object-Oriented Design Principles', 'OOD', 8, 10),
    textBonus(203, 'Software Engineering Ethics', 'SEE', 13, 5),
    teamText(204, 'Team Design Document: Library System', 'TPDD', 8, 10, 2, 5),
];

export const INTRO_JAVA_MODELING_EXERCISES: ModelingExercise[] = [
    modeling(301, 'Class Diagram: Library System', 'CD1', 7, 15, UMLDiagramType.ClassDiagram),
    modeling(302, 'Activity Diagram: Login Flow', 'AD1', 10, 10, UMLDiagramType.ActivityDiagram),
    modeling(303, 'Class Diagram: Final Project Domain', 'CD2', 14, 15, UMLDiagramType.ClassDiagram),
];

export const INTRO_JAVA_QUIZ_EXERCISES: QuizExercise[] = [
    // Varied statuses and modes to test lifecycle management UI
    quiz(401, 'Quiz: Java Basics', 'QB1', 2, 10, QuizMode.SYNCHRONIZED, QuizStatus.INVISIBLE),
    Object.assign(quiz(402, 'Quiz: OOP Concepts', 'QB2', 8, 10, QuizMode.BATCHED, QuizStatus.ACTIVE), { quizStarted: true }),
    quiz(403, 'Quiz: Collections and Generics', 'QB3', 12, 10, QuizMode.SYNCHRONIZED, QuizStatus.VISIBLE),
    quiz(404, 'Quiz: Algorithms Recap', 'QB4', 10, 5, QuizMode.INDIVIDUAL, QuizStatus.VISIBLE),
];

const fileUploadNotIncluded = (id: number, title: string, shortName: string, week: number, points: number): FileUploadExercise => {
    const ex = fileUpload(id, title, shortName, week, points);
    ex.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;
    return ex;
};

export const INTRO_JAVA_FILE_UPLOAD_EXERCISES: FileUploadExercise[] = [
    fileUploadNotIncluded(501, 'Worksheet: Pseudocode Exercises', 'WS1', 1, 5),
    fileUploadNotIncluded(502, 'Worksheet: Algorithm Analysis', 'WS2', 6, 5),
];

export const INTRO_JAVA_ALL_EXERCISES: Exercise[] = [
    ...INTRO_JAVA_PROGRAMMING_EXERCISES,
    ...INTRO_JAVA_TEXT_EXERCISES,
    ...INTRO_JAVA_MODELING_EXERCISES,
    ...INTRO_JAVA_QUIZ_EXERCISES,
    ...INTRO_JAVA_FILE_UPLOAD_EXERCISES,
];

// ── Exercise groups ──────────────────────────────────────────────────────────────
// Group-level timeline overrides the individual exercise dates of its members.
// maxPoints is intentionally lower than the summed variant points to exercise the cap-at-grade
// behaviour (a student picks one variant; the group contributes at most maxPoints to the course).
const GROUP_LOOPS_ID = 1;
const GROUP_ARRAYS_ID = 2;
const GROUP_RECURSION_ID = 3;
const GROUP_TEAM_PROJECT_ID = 4;

export const INTRO_JAVA_EXERCISE_GROUPS: CourseExerciseGroup[] = [
    {
        id: GROUP_LOOPS_ID,
        title: 'Loops',
        order: 0,
        releaseDate: WEEK(3),
        startDate: WEEK(3),
        dueDate: WEEK(3).add(10, 'day'),
        assessmentDueDate: WEEK(3).add(13, 'day'),
        maxPoints: 10,
        handInLimit: 1,
        exercises: [LOOPS_CARS, LOOPS_PLANES, LOOPS_ROBOTS],
    },
    {
        id: GROUP_ARRAYS_ID,
        title: 'Arrays and Lists',
        order: 1,
        releaseDate: WEEK(4),
        startDate: WEEK(4),
        dueDate: WEEK(4).add(10, 'day'),
        assessmentDueDate: WEEK(4).add(13, 'day'),
        maxPoints: 15,
        exercises: [ARRAYS_CARS, ARRAYS_PLANES],
    },
    {
        id: GROUP_RECURSION_ID,
        title: 'Recursion',
        order: 2,
        // No group-level timeline set: members keep their individual dates.
        maxPoints: 15,
        exercises: [RECURSION_PLANES, RECURSION_ROBOTS],
    },
    {
        id: GROUP_TEAM_PROJECT_ID,
        title: 'Team Project',
        order: 3,
        releaseDate: WEEK(8),
        startDate: WEEK(8),
        dueDate: WEEK(8).add(14, 'day'),
        assessmentDueDate: WEEK(8).add(17, 'day'),
        maxPoints: 30,
        exercises: [TEAM_PROJECT_BASIC, TEAM_PROJECT_ADVANCED],
    },
];

// ── Exercise relations ───────────────────────────────────────────────────────────
const group = (id: number) => ({ kind: ExerciseRelationEndpointKind.GROUP, id });
const exercise = (id: number) => ({ kind: ExerciseRelationEndpointKind.EXERCISE, id });

export const INTRO_JAVA_EXERCISE_RELATIONS: ExerciseRelation[] = [
    // Group-level prerequisites: Recursion builds on Loops and Arrays. Because these are declared
    // on the whole group, every variant inherits the prerequisite without restating it.
    { id: 1, type: ExerciseRelationType.PREREQUISITE, source: group(GROUP_LOOPS_ID), target: group(GROUP_RECURSION_ID) },
    { id: 2, type: ExerciseRelationType.PREREQUISITE, source: group(GROUP_ARRAYS_ID), target: group(GROUP_RECURSION_ID) },
    // Exercise-level difficulty ordering of the Loops variants.
    { id: 3, type: ExerciseRelationType.HARDER_THAN, source: exercise(1002), target: exercise(1001) },
    { id: 4, type: ExerciseRelationType.HARDER_THAN, source: exercise(1003), target: exercise(1002) },
];

export function createIntroToJavaCourse(): Course {
    const course = new Course();
    course.id = 42;
    course.title = 'Introduction to Programming in Java';
    course.shortName = 'INTRO_JAVA';
    course.startDate = WEEK(0);
    course.endDate = WEEK(15);
    course.maxPoints = 280;
    course.exercises = [...INTRO_JAVA_ALL_EXERCISES];
    return course;
}
