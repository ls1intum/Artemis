import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { DifficultyLevel, Exercise, ExerciseMode, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { UMLDiagramType } from '@tumaet/apollon';

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
    return ex;
}

// ── Text exercises ─────────────────────────────────────────────────────────────

function text(id: number, title: string, shortName: string, week: number, points: number): TextExercise {
    const ex = new TextExercise(undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week, 10));
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = DifficultyLevel.EASY;
    ex.assessmentType = AssessmentType.MANUAL;
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
    return ex;
}

// ── Quiz exercises ─────────────────────────────────────────────────────────────

function quiz(id: number, title: string, shortName: string, week: number, points: number): QuizExercise {
    const ex = new QuizExercise(undefined, undefined);
    Object.assign(ex, base(id, title, shortName, week, 1));
    ex.maxPoints = points;
    ex.bonusPoints = 0;
    ex.difficulty = DifficultyLevel.EASY;
    ex.quizMode = QuizMode.SYNCHRONIZED;
    ex.status = QuizStatus.VISIBLE;
    ex.randomizeQuestionOrder = true;
    ex.duration = 30;
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
    return ex;
}

// ── Exercise catalogue ─────────────────────────────────────────────────────────

export const INTRO_JAVA_PROGRAMMING_EXERCISES: ProgrammingExercise[] = [
    prog(101, 'Hello World', 'HW', 0, 5, DifficultyLevel.EASY),
    prog(102, 'Variables and Data Types', 'VDT', 1, 10, DifficultyLevel.EASY),
    prog(103, 'Control Flow: if/else and switch', 'CF1', 2, 10, DifficultyLevel.EASY),
    prog(104, 'Loops: for, while, do-while', 'LOOP', 3, 10, DifficultyLevel.EASY),
    prog(105, 'Arrays and ArrayLists', 'ARR', 4, 15, DifficultyLevel.MEDIUM),
    prog(106, 'Methods and Recursion', 'REC', 5, 15, DifficultyLevel.MEDIUM),
    prog(107, 'String Manipulation', 'STR', 6, 10, DifficultyLevel.EASY),
    prog(108, 'Classes and Objects', 'OOP1', 7, 20, DifficultyLevel.MEDIUM),
    prog(109, 'Inheritance and Polymorphism', 'OOP2', 9, 20, DifficultyLevel.MEDIUM),
    prog(110, 'Interfaces and Abstract Classes', 'OOP3', 10, 20, DifficultyLevel.MEDIUM),
    prog(111, 'Exception Handling', 'EXC', 11, 15, DifficultyLevel.MEDIUM),
    prog(112, 'Collections and Generics', 'COL', 12, 20, DifficultyLevel.HARD),
    prog(113, 'File I/O and Streams', 'IO', 13, 15, DifficultyLevel.HARD),
    prog(114, 'Final Project: Student Grade Manager', 'PROJ', 14, 40, DifficultyLevel.HARD),
];

export const INTRO_JAVA_TEXT_EXERCISES: TextExercise[] = [
    text(201, 'Reflection: Programming Paradigms', 'RPP', 1, 5),
    text(202, 'Explain Object-Oriented Design Principles', 'OOD', 8, 10),
    text(203, 'Software Engineering Ethics', 'SEE', 13, 5),
];

export const INTRO_JAVA_MODELING_EXERCISES: ModelingExercise[] = [
    modeling(301, 'Class Diagram: Library System', 'CD1', 7, 15, UMLDiagramType.ClassDiagram),
    modeling(302, 'Activity Diagram: Login Flow', 'AD1', 10, 10, UMLDiagramType.ActivityDiagram),
    modeling(303, 'Class Diagram: Final Project Domain', 'CD2', 14, 15, UMLDiagramType.ClassDiagram),
];

export const INTRO_JAVA_QUIZ_EXERCISES: QuizExercise[] = [
    quiz(401, 'Quiz: Java Basics', 'QB1', 2, 10),
    quiz(402, 'Quiz: OOP Concepts', 'QB2', 8, 10),
    quiz(403, 'Quiz: Collections and Generics', 'QB3', 12, 10),
];

export const INTRO_JAVA_FILE_UPLOAD_EXERCISES: FileUploadExercise[] = [
    fileUpload(501, 'Worksheet: Pseudocode Exercises', 'WS1', 1, 5),
    fileUpload(502, 'Worksheet: Algorithm Analysis', 'WS2', 6, 5),
];

export const INTRO_JAVA_ALL_EXERCISES: Exercise[] = [
    ...INTRO_JAVA_PROGRAMMING_EXERCISES,
    ...INTRO_JAVA_TEXT_EXERCISES,
    ...INTRO_JAVA_MODELING_EXERCISES,
    ...INTRO_JAVA_QUIZ_EXERCISES,
    ...INTRO_JAVA_FILE_UPLOAD_EXERCISES,
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
