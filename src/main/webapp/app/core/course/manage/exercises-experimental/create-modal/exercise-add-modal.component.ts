import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faArrowRight, faCode, faFileAlt, faFileUpload, faLayerGroup, faPencilAlt, faQuestion } from '@fortawesome/free-solid-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';

export type AddModalMode = 'create' | 'import' | 'export' | 'unified';

interface ExerciseTypeCard {
    type: string;
    label: string;
    description: string;
    icon: typeof faCode;
    accentClass: string;
    routeSegment: string;
}

interface MockImportExercise {
    id: number;
    title: string;
    course: string;
    type: string;
}

interface MockImportGroup {
    id: number;
    title: string;
    course: string;
    exerciseCount: number;
}

const MOCK_EXPORT_QUIZ_EXERCISES: MockImportExercise[] = [
    { id: 1, title: 'Java Basics Quiz', course: 'This course', type: 'quiz' },
    { id: 3, title: 'OOP Concepts Quiz', course: 'This course', type: 'quiz' },
    { id: 7, title: 'Sorting & Complexity Quiz', course: 'This course', type: 'quiz' },
];

const MOCK_IMPORT_GROUPS: MockImportGroup[] = [
    { id: 601, title: 'Loops', course: 'Intro to CS (WS 23/24)', exerciseCount: 3 },
    { id: 602, title: 'Arrays and Lists', course: 'Intro to CS (WS 23/24)', exerciseCount: 2 },
    { id: 603, title: 'OOP Fundamentals', course: 'Software Engineering (SS 24)', exerciseCount: 4 },
    { id: 604, title: 'Sorting Algorithms', course: 'Algorithms (WS 24/25)', exerciseCount: 3 },
];

const MOCK_IMPORT_EXERCISES: Record<string, MockImportExercise[]> = {
    programming: [
        { id: 101, title: 'Hello World', course: 'Intro to CS (WS 23/24)', type: 'programming' },
        { id: 102, title: 'Fibonacci Sequence', course: 'Intro to CS (WS 23/24)', type: 'programming' },
        { id: 103, title: 'Binary Search Tree', course: 'Data Structures (SS 24)', type: 'programming' },
        { id: 104, title: 'Sorting Algorithms', course: 'Data Structures (SS 24)', type: 'programming' },
        { id: 105, title: 'Graph Traversal (BFS/DFS)', course: 'Algorithms (WS 24/25)', type: 'programming' },
    ],
    quiz: [
        { id: 201, title: 'Java Basics Quiz', course: 'Intro to CS (WS 23/24)', type: 'quiz' },
        { id: 202, title: 'OOP Concepts Quiz', course: 'Intro to CS (WS 23/24)', type: 'quiz' },
        { id: 203, title: 'Complexity Theory Quiz', course: 'Algorithms (WS 24/25)', type: 'quiz' },
    ],
    modeling: [
        { id: 301, title: 'Class Diagram: Library', course: 'Software Engineering (SS 24)', type: 'modeling' },
        { id: 302, title: 'Use Case Diagram: Online Shop', course: 'Software Engineering (SS 24)', type: 'modeling' },
        { id: 303, title: 'Sequence Diagram: Authentication', course: 'Software Engineering (SS 24)', type: 'modeling' },
    ],
    text: [
        { id: 401, title: 'Reflection on Clean Code', course: 'Software Engineering (SS 24)', type: 'text' },
        { id: 402, title: 'Essay: Agile vs Waterfall', course: 'Project Management (SS 24)', type: 'text' },
    ],
    'file-upload': [
        { id: 501, title: 'Worksheet 1: Variables', course: 'Intro to CS (WS 23/24)', type: 'file-upload' },
        { id: 502, title: 'Worksheet 2: Control Flow', course: 'Intro to CS (WS 23/24)', type: 'file-upload' },
    ],
};

const EXERCISE_TYPE_CARDS: ExerciseTypeCard[] = [
    {
        type: 'programming',
        label: 'Programming',
        description: 'Automated grading with test suites. Supports Java, Python, C, and more.',
        icon: faCode,
        accentClass: 'card--programming',
        routeSegment: 'programming-exercises/new',
    },
    {
        type: 'quiz',
        label: 'Quiz',
        description: 'Multiple choice, short answer, and drag-and-drop questions.',
        icon: faQuestion,
        accentClass: 'card--quiz',
        routeSegment: 'quiz-exercises/new',
    },
    {
        type: 'modeling',
        label: 'Modeling',
        description: 'UML diagrams and model-based exercises with semi-automatic assessment.',
        icon: faPencilAlt,
        accentClass: 'card--modeling',
        routeSegment: 'modeling-exercises/new',
    },
    {
        type: 'text',
        label: 'Text',
        description: 'Free-text essays and open-ended questions with manual review.',
        icon: faFileAlt,
        accentClass: 'card--text',
        routeSegment: 'text-exercises/new',
    },
    {
        type: 'file-upload',
        label: 'File Upload',
        description: 'Worksheet or document submissions reviewed by instructors.',
        icon: faFileUpload,
        accentClass: 'card--fileupload',
        routeSegment: 'file-upload-exercises/new',
    },
];

@Component({
    selector: 'jhi-exercise-add-modal',
    templateUrl: './exercise-add-modal.component.html',
    styleUrl: './exercise-add-modal.component.scss',
    imports: [DialogModule, ButtonModule, CheckboxModule, FormsModule, FaIconComponent],
})
export class ExerciseAddModalComponent {
    readonly visible = input<boolean>(false);
    readonly mode = input<AddModalMode>('create');
    readonly courseId = input<number | undefined>(undefined);

    readonly visibleChange = output<boolean>();
    readonly groupCreate = output<void>();

    protected readonly exerciseTypeCards = EXERCISE_TYPE_CARDS;

    readonly activeTab = signal<'create' | 'import' | 'export'>('create');
    readonly importSelectedType = signal<string | null>(null);
    readonly importSelectedIds = signal<Set<number>>(new Set());
    readonly importGroupSelectedIds = signal<Set<number>>(new Set());
    readonly exportSelectedIds = signal<Set<number>>(new Set());
    readonly exportSelectAll = signal(false);

    protected readonly faArrowRight = faArrowRight;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faLayerGroup = faLayerGroup;

    private readonly router = inject(Router);

    get dialogHeader(): string {
        switch (this.mode()) {
            case 'create':
                return 'Create Exercise';
            case 'import':
                return 'Import';
            case 'export':
                return 'Export';
            case 'unified':
                return 'Manage Exercises';
            default:
                return 'Manage Exercises';
        }
    }

    close(): void {
        this.visibleChange.emit(false);
    }

    navigateToCreate(card: ExerciseTypeCard): void {
        const id = this.courseId();
        if (id !== undefined) {
            this.router.navigate(['/course-management', id, card.routeSegment]);
        }
        this.close();
    }

    setActiveTab(tab: 'create' | 'import' | 'export'): void {
        this.activeTab.set(tab);
        this.importSelectedType.set(null);
        this.importSelectedIds.set(new Set());
        this.importGroupSelectedIds.set(new Set());
        this.exportSelectedIds.set(new Set());
        this.exportSelectAll.set(false);
    }

    selectImportType(type: string): void {
        this.importSelectedType.set(type);
        this.importSelectedIds.set(new Set());
        this.importGroupSelectedIds.set(new Set());
    }

    backToImportTypeSelection(): void {
        this.importSelectedType.set(null);
        this.importSelectedIds.set(new Set());
        this.importGroupSelectedIds.set(new Set());
    }

    importExercisesForType(type: string): MockImportExercise[] {
        return MOCK_IMPORT_EXERCISES[type] ?? [];
    }

    importGroups(): MockImportGroup[] {
        return MOCK_IMPORT_GROUPS;
    }

    exportQuizExercises(): MockImportExercise[] {
        return MOCK_EXPORT_QUIZ_EXERCISES;
    }

    toggleImportSelection(id: number): void {
        const next = new Set(this.importSelectedIds());
        if (next.has(id)) next.delete(id);
        else next.add(id);
        this.importSelectedIds.set(next);
    }

    toggleImportGroupSelection(id: number): void {
        const next = new Set(this.importGroupSelectedIds());
        if (next.has(id)) next.delete(id);
        else next.add(id);
        this.importGroupSelectedIds.set(next);
    }

    toggleExportSelection(id: number): void {
        const next = new Set(this.exportSelectedIds());
        if (next.has(id)) next.delete(id);
        else next.add(id);
        this.exportSelectedIds.set(next);
        this.exportSelectAll.set(next.size === this.exportQuizExercises().length);
    }

    toggleExportSelectAll(selectAll: boolean): void {
        this.exportSelectAll.set(selectAll);
        if (selectAll) {
            this.exportSelectedIds.set(new Set(this.exportQuizExercises().map((e) => e.id)));
        } else {
            this.exportSelectedIds.set(new Set());
        }
    }

    importTypeLabel(type: string): string {
        return type === 'group' ? 'Group' : (EXERCISE_TYPE_CARDS.find((c) => c.type === type)?.label ?? type);
    }

    importSelectionCount(): number {
        return this.importSelectedType() === 'group' ? this.importGroupSelectedIds().size : this.importSelectedIds().size;
    }

    confirmImport(): void {
        this.close();
    }

    confirmExport(): void {
        this.close();
    }

    createGroup(): void {
        this.groupCreate.emit();
        this.close();
    }
}
