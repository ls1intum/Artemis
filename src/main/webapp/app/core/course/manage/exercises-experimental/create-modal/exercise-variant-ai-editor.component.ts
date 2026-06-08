import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpRightFromSquare, faCheck, faPaperPlane, faRobot, faTimes, faUser, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DifficultyLevel, Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared-ui/components/resizable-panels/resizable-panels.component';
import dayjs from 'dayjs/esm';

// ── Public types ───────────────────────────────────────────────────────────────

export interface AiEditorConfig {
    changeDifficulty: boolean;
    targetDifficulty: DifficultyLevel;
    changeDomain: boolean;
    domainText: string;
    additionalInstructions: string;
}

// ── Internal types ─────────────────────────────────────────────────────────────

interface ChatMessage {
    id: number;
    role: 'user' | 'ai';
    text: string;
}

interface DiffLine {
    type: 'unchanged' | 'added' | 'removed';
    content: string;
}

export type DiffKey = 'title' | 'difficulty' | 'problemStatement';

interface FieldDiff {
    key: DiffKey;
    label: string;
    status: 'pending' | 'accepted' | 'declined';
    oldValue: string;
    newValue: string;
    diffLines: DiffLine[]; // detailed line view used for problemStatement
}

interface EditableFields {
    title: string;
    difficulty: DifficultyLevel;
    durationDays: number;
    problemStatement: string;
}

interface PillOption {
    label: string;
    action: string;
}

// ── Constants ─────────────────────────────────────────────────────────────────

const MAX_TOKENS = 8000;
const SYSTEM_TOKENS = 342;
const TOKENS_PER_USER = 75;
const TOKENS_PER_AI = 160;
const AI_TYPING_DELAY_MS = 1600;

const PILL_OPTIONS: PillOption[] = [
    { label: '↑ Make it harder', action: 'harder' },
    { label: '↓ Make it easier', action: 'easier' },
    { label: '↻ Different domain', action: 'domain' },
];

const DIFFICULTY_LABELS: Record<DifficultyLevel, string> = {
    [DifficultyLevel.EASY]: 'Easy',
    [DifficultyLevel.MEDIUM]: 'Medium',
    [DifficultyLevel.HARD]: 'Hard',
};

// ── Mock AI engine ─────────────────────────────────────────────────────────────

function sourceDurationDays(exercise: Exercise): number {
    if (exercise.releaseDate && exercise.dueDate) {
        return Math.max(1, exercise.dueDate.diff(exercise.releaseDate, 'day'));
    }
    return 7;
}

function buildDiffs(exercise: Exercise, action: string, extra = ''): FieldDiff[] {
    const topic = (exercise.title ?? 'Exercise').split(':')[0].trim();
    const diff = exercise.difficulty ?? DifficultyLevel.MEDIUM;

    if (action === 'harder') {
        const newDiff = diff === DifficultyLevel.EASY ? DifficultyLevel.MEDIUM : DifficultyLevel.HARD;
        const newTitle = `${topic}: Advanced Challenge`;
        const newPS =
            `## ${topic}: Advanced Challenge\n\n` +
            `In this advanced exercise you will tackle a non-trivial ${topic.toLowerCase()} problem ` +
            `requiring careful algorithmic thinking and strict edge-case handling.\n\n` +
            `### Your tasks\n` +
            `1. Implement the solution with O(log n) time complexity.\n` +
            `2. Handle empty input, single-element lists, and integer overflow.\n` +
            `3. Implement a helper method used by the main algorithm.\n` +
            `4. Verify all provided test cases pass without modification.`;
        return [
            {
                key: 'title',
                label: 'Title',
                status: 'pending',
                oldValue: exercise.title ?? '',
                newValue: newTitle,
                diffLines: [
                    { type: 'removed', content: exercise.title ?? '' },
                    { type: 'added', content: newTitle },
                ],
            },
            {
                key: 'difficulty',
                label: 'Difficulty',
                status: 'pending',
                oldValue: diff,
                newValue: newDiff,
                diffLines: [
                    { type: 'removed', content: DIFFICULTY_LABELS[diff] },
                    { type: 'added', content: DIFFICULTY_LABELS[newDiff] },
                ],
            },
            {
                key: 'problemStatement',
                label: 'Problem Statement',
                status: 'pending',
                oldValue: exercise.problemStatement ?? '',
                newValue: newPS,
                diffLines: [
                    { type: 'removed', content: `## ${topic}` },
                    { type: 'added', content: `## ${topic}: Advanced Challenge` },
                    { type: 'unchanged', content: '' },
                    { type: 'removed', content: `In this exercise you will practice the core concepts of ${topic.toLowerCase()}.` },
                    { type: 'added', content: `In this advanced exercise you will tackle a non-trivial ${topic.toLowerCase()} problem` },
                    { type: 'added', content: `requiring careful algorithmic thinking and strict edge-case handling.` },
                    { type: 'unchanged', content: '' },
                    { type: 'unchanged', content: `### Your tasks` },
                    { type: 'removed', content: `1. Implement the required method.` },
                    { type: 'removed', content: `2. Verify your solution against the examples.` },
                    { type: 'added', content: `1. Implement the solution with O(log n) time complexity.` },
                    { type: 'added', content: `2. Handle empty input, single-element lists, and integer overflow.` },
                    { type: 'added', content: `3. Implement a helper method used by the main algorithm.` },
                    { type: 'added', content: `4. Verify all provided test cases pass without modification.` },
                ],
            },
        ];
    }

    if (action === 'easier') {
        const newDiff = diff === DifficultyLevel.HARD ? DifficultyLevel.MEDIUM : DifficultyLevel.EASY;
        const newTitle = `${topic}: Step-by-Step`;
        const newPS =
            `## ${topic}: Step-by-Step\n\n` +
            `In this guided exercise you will learn the fundamentals of ${topic.toLowerCase()} ` +
            `through small, structured steps. Hints are provided along the way.\n\n` +
            `### Your tasks\n` +
            `1. Read the provided skeleton code and understand the structure.\n` +
            `2. Fill in the TODO sections one at a time (hints below each TODO).\n` +
            `3. Run the tests to verify your solution.`;
        return [
            {
                key: 'title',
                label: 'Title',
                status: 'pending',
                oldValue: exercise.title ?? '',
                newValue: newTitle,
                diffLines: [
                    { type: 'removed', content: exercise.title ?? '' },
                    { type: 'added', content: newTitle },
                ],
            },
            {
                key: 'difficulty',
                label: 'Difficulty',
                status: 'pending',
                oldValue: diff,
                newValue: newDiff,
                diffLines: [
                    { type: 'removed', content: DIFFICULTY_LABELS[diff] },
                    { type: 'added', content: DIFFICULTY_LABELS[newDiff] },
                ],
            },
            {
                key: 'problemStatement',
                label: 'Problem Statement',
                status: 'pending',
                oldValue: exercise.problemStatement ?? '',
                newValue: newPS,
                diffLines: [
                    { type: 'removed', content: `## ${topic}` },
                    { type: 'added', content: `## ${topic}: Step-by-Step` },
                    { type: 'unchanged', content: '' },
                    { type: 'removed', content: `In this exercise you will practice the core concepts of ${topic.toLowerCase()}.` },
                    { type: 'added', content: `In this guided exercise you will learn the fundamentals of ${topic.toLowerCase()}` },
                    { type: 'added', content: `through small, structured steps. Hints are provided along the way.` },
                    { type: 'unchanged', content: '' },
                    { type: 'unchanged', content: `### Your tasks` },
                    { type: 'removed', content: `1. Implement the required method.` },
                    { type: 'added', content: `1. Read the provided skeleton code and understand the structure.` },
                    { type: 'added', content: `2. Fill in the TODO sections one at a time (hints below each TODO).` },
                    { type: 'removed', content: `2. Verify your solution against the examples.` },
                    { type: 'added', content: `3. Run the tests to verify your solution.` },
                ],
            },
        ];
    }

    if (action === 'domain') {
        const theme = extra || 'an alternative theme';
        const displayTheme = theme.charAt(0).toUpperCase() + theme.slice(1);
        const newTitle = `${topic}: ${displayTheme}`;
        const newPS =
            `## ${topic}: ${displayTheme}\n\n` +
            `This exercise covers the same ${topic.toLowerCase()} algorithm, but set in the context of ` +
            `${theme}. The algorithmic challenge is identical; only the story changes.\n\n` +
            `### Your tasks\n` +
            `1. Read the scenario carefully.\n` +
            `2. Implement the required method.\n` +
            `3. Verify your solution against the provided examples.`;
        return [
            {
                key: 'title',
                label: 'Title',
                status: 'pending',
                oldValue: exercise.title ?? '',
                newValue: newTitle,
                diffLines: [
                    { type: 'removed', content: exercise.title ?? '' },
                    { type: 'added', content: newTitle },
                ],
            },
            {
                key: 'problemStatement',
                label: 'Problem Statement',
                status: 'pending',
                oldValue: exercise.problemStatement ?? '',
                newValue: newPS,
                diffLines: [
                    { type: 'removed', content: `## ${topic}` },
                    { type: 'added', content: `## ${topic}: ${displayTheme}` },
                    { type: 'unchanged', content: '' },
                    { type: 'removed', content: `This exercise covers the ${topic.toLowerCase()} algorithm in a standard context.` },
                    { type: 'added', content: `This exercise covers the same ${topic.toLowerCase()} algorithm, but set in the context of` },
                    { type: 'added', content: `${theme}. The algorithmic challenge is identical; only the story changes.` },
                    { type: 'unchanged', content: '' },
                    { type: 'unchanged', content: `### Your tasks` },
                    { type: 'unchanged', content: `1. Read the scenario carefully.` },
                    { type: 'unchanged', content: `2. Implement the required method.` },
                    { type: 'unchanged', content: `3. Verify your solution against the provided examples.` },
                ],
            },
        ];
    }

    if (action === 'shorter') {
        const newPS = `## ${topic}\n\n` + `### Your tasks\n` + `1. Implement the required method directly (no design step needed).\n` + `2. Verify the provided test cases pass.`;
        return [
            {
                key: 'problemStatement',
                label: 'Problem Statement',
                status: 'pending',
                oldValue: exercise.problemStatement ?? '',
                newValue: newPS,
                diffLines: [
                    { type: 'unchanged', content: `## ${topic}` },
                    { type: 'unchanged', content: '' },
                    { type: 'unchanged', content: `### Your tasks` },
                    { type: 'removed', content: `1. Analyse the problem and sketch your approach on paper.` },
                    { type: 'removed', content: `2. Implement the required methods step by step.` },
                    { type: 'removed', content: `3. Verify your implementation against the provided sample cases.` },
                    { type: 'added', content: `1. Implement the required method directly (no design step needed).` },
                    { type: 'added', content: `2. Verify the provided test cases pass.` },
                ],
            },
        ];
    }

    // longer
    const newPS =
        `## ${topic}\n\n` +
        `### Your tasks\n` +
        `1. Analyse the problem and sketch your approach on paper.\n` +
        `2. Implement the required methods step by step.\n` +
        `3. Verify your implementation against the provided sample cases.\n` +
        `4. Write a brief report (max 200 words) explaining your design decisions.\n` +
        `5. Refactor your solution for readability and add inline comments.`;
    return [
        {
            key: 'problemStatement',
            label: 'Problem Statement',
            status: 'pending',
            oldValue: exercise.problemStatement ?? '',
            newValue: newPS,
            diffLines: [
                { type: 'unchanged', content: `## ${topic}` },
                { type: 'unchanged', content: '' },
                { type: 'unchanged', content: `### Your tasks` },
                { type: 'unchanged', content: `1. Analyse the problem and sketch your approach on paper.` },
                { type: 'unchanged', content: `2. Implement the required methods step by step.` },
                { type: 'unchanged', content: `3. Verify your implementation against the provided sample cases.` },
                { type: 'added', content: `4. Write a brief report (max 200 words) explaining your design decisions.` },
                { type: 'added', content: `5. Refactor your solution for readability and add inline comments.` },
            ],
        },
    ];
}

function aiResponseText(action: string, exerciseTitle: string): string {
    const topic = exerciseTitle.split(':')[0].trim();
    switch (action) {
        case 'harder':
            return `I've made **${topic}** more challenging. I increased the difficulty level and rewrote the problem statement to require a more efficient algorithm and stricter edge-case handling. Review the proposed changes on the right.`;
        case 'easier':
            return `I've simplified **${topic}**. I reduced the difficulty and restructured the tasks into smaller guided steps with hints. Review the changes on the right.`;
        case 'domain':
            return `I've updated the application domain for **${topic}**. The algorithmic challenge is identical — only the real-world story changes. Review the changes on the right.`;
        case 'shorter':
            return `I've shortened **${topic}**. I reduced the submission window and trimmed the task list to focus on the core algorithm. Review the changes on the right.`;
        case 'longer':
            return `I've extended **${topic}**. I increased the submission window and added a reflection and refactoring step. Review the changes on the right.`;
        default:
            return `I've updated **${topic}** based on your instructions. Review the proposed changes on the right.`;
    }
}

function detectAction(text: string): string {
    const lower = text.toLowerCase();
    if (lower.includes('hard') || lower.includes('difficult') || lower.includes('complex') || lower.includes('challenging')) return 'harder';
    if (lower.includes('easy') || lower.includes('simpl') || lower.includes('basic') || lower.includes('beginner')) return 'easier';
    if (lower.includes('short') || lower.includes('quick') || lower.includes('brief') || lower.includes('less time')) return 'shorter';
    if (lower.includes('long') || lower.includes('extend') || lower.includes('more time') || lower.includes('deeper')) return 'longer';
    if (lower.includes('domain') || lower.includes('theme') || lower.includes('topic') || lower.includes('context') || lower.includes('about')) return 'domain';
    return 'generic';
}

function extractDomainFromMessage(text: string): string {
    const lower = text.toLowerCase();
    const keywords = ['about ', 'theme ', 'domain ', 'context ', 'topic '];
    for (const kw of keywords) {
        const idx = lower.indexOf(kw);
        if (idx !== -1) return text.slice(idx + kw.length).trim();
    }
    return text.trim();
}

function configToMessage(config: AiEditorConfig): string {
    const parts: string[] = [];
    if (config.changeDifficulty) parts.push(`change difficulty to ${config.targetDifficulty.toLowerCase()}`);
    if (config.changeDomain && config.domainText) parts.push(`change domain to "${config.domainText}"`);
    if (config.additionalInstructions) parts.push(config.additionalInstructions);
    return parts.join(', ');
}

function editableFieldsFromExercise(exercise: Exercise): EditableFields {
    return {
        title: exercise.title ?? '',
        difficulty: exercise.difficulty ?? DifficultyLevel.MEDIUM,
        durationDays: sourceDurationDays(exercise),
        problemStatement: exercise.problemStatement ?? '',
    };
}

function buildVariantFromFields(source: Exercise, fields: EditableFields): ProgrammingExercise {
    const variant = new ProgrammingExercise(undefined, undefined);
    Object.assign(variant, source);
    variant.id = 9500 + Math.floor(Math.random() * 400);
    variant.title = fields.title;
    variant.difficulty = fields.difficulty;
    variant.problemStatement = fields.problemStatement;
    variant.shortName = fields.title.split(':')[0].replace(/\s+/g, '').substring(0, 5).toUpperCase() + 'V2';
    variant.programmingLanguage = ProgrammingLanguage.JAVA;
    variant.projectType = ProjectType.MAVEN_MAVEN;
    variant.assessmentType = AssessmentType.AUTOMATIC;
    variant.categories = [new ExerciseCategory('Java', '#6f42c1'), new ExerciseCategory('Algorithms', '#198754')];
    const start = source.releaseDate ?? dayjs();
    variant.releaseDate = start;
    variant.startDate = start;
    variant.dueDate = start.add(fields.durationDays, 'day');
    variant.assessmentDueDate = start.add(fields.durationDays + 3, 'day');
    return variant;
}

// ── Component ─────────────────────────────────────────────────────────────────

@Component({
    selector: 'jhi-exercise-variant-ai-editor',
    templateUrl: './exercise-variant-ai-editor.component.html',
    styleUrl: './exercise-variant-ai-editor.component.scss',
    imports: [DialogModule, InputTextModule, FormsModule, FaIconComponent, DecimalPipe, NgTemplateOutlet, ResizablePanelsComponent, PanelDirective],
})
export class ExerciseVariantAiEditorComponent implements OnInit, OnDestroy, AfterViewChecked {
    readonly visible = input<boolean>(false);
    readonly sourceExercise = input.required<Exercise>();
    readonly mode = input<'custom' | 'v1'>('custom');
    readonly initialConfig = input<AiEditorConfig | null>(null);
    readonly displayMode = input<'dialog' | 'page'>('dialog');
    readonly courseId = input<number | undefined>(undefined);

    readonly visibleChange = output<boolean>();
    readonly variantAdded = output<Exercise>();

    readonly chatScrollEl = viewChild<ElementRef<HTMLDivElement>>('chatScroll');

    readonly chatMessages = signal<ChatMessage[]>([]);
    readonly chatInput = signal('');
    readonly diffs = signal<FieldDiff[]>([]);
    readonly editableFields = signal<EditableFields>({ title: '', difficulty: DifficultyLevel.MEDIUM, durationDays: 7, problemStatement: '' });
    readonly tokensUsed = signal(SYSTEM_TOKENS);
    readonly aiTyping = signal(false);
    readonly pillsVisible = signal(false);
    readonly awaitingDomain = signal(false);

    readonly pendingDiffCount = computed(() => this.diffs().filter((d) => d.status === 'pending').length);
    readonly hasChanges = computed(() => {
        const src = this.sourceExercise();
        const f = this.editableFields();
        return f.title !== (src.title ?? '') || f.difficulty !== (src.difficulty ?? DifficultyLevel.MEDIUM) || f.problemStatement !== (src.problemStatement ?? '');
    });

    protected readonly faRobot = faRobot;
    protected readonly faUser = faUser;
    protected readonly faPaperPlane = faPaperPlane;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly getIcon = getIcon;
    protected readonly ExerciseType = ExerciseType;
    protected readonly pills = PILL_OPTIONS;
    protected readonly maxTokens = MAX_TOKENS;
    protected readonly difficultyOptions = [
        { value: DifficultyLevel.EASY, label: 'Easy' },
        { value: DifficultyLevel.MEDIUM, label: 'Medium' },
        { value: DifficultyLevel.HARD, label: 'Hard' },
    ];

    private msgId = 0;
    private typingTimer: ReturnType<typeof setTimeout> | null = null;
    private shouldScrollToBottom = false;

    private readonly mockService = inject(ExerciseManagementMockService);
    private readonly router = inject(Router);

    ngOnInit(): void {
        if (this.displayMode() === 'page' || this.visible()) {
            this.initialize();
        }
    }

    ngOnDestroy(): void {
        this.clearTimer();
    }

    ngAfterViewChecked(): void {
        if (this.shouldScrollToBottom) {
            this.scrollToBottom();
            this.shouldScrollToBottom = false;
        }
    }

    onDialogShow(): void {
        this.initialize();
    }

    close(): void {
        this.clearTimer();
        this.visibleChange.emit(false);
    }

    onClose(visible: boolean): void {
        if (!visible) this.close();
    }

    openAsPage(): void {
        const exercise = this.sourceExercise();
        const cId = this.courseId();
        if (cId !== undefined && exercise.id !== undefined) {
            this.router.navigate(['/course-management', cId, 'exercises', 'experimental', 'create-variant', exercise.id]);
            this.close();
        }
    }

    diffFor(key: DiffKey): FieldDiff | undefined {
        return this.diffs().find((d) => d.key === key);
    }

    updateField(key: keyof EditableFields, value: string | number | DifficultyLevel): void {
        this.editableFields.update((f) => ({ ...f, [key]: value }));
    }

    sendMessage(): void {
        const text = this.chatInput().trim();
        if (!text || this.aiTyping()) return;
        this.chatInput.set('');
        this.pillsVisible.set(false);
        this.addUserMessage(text);

        if (this.awaitingDomain()) {
            this.awaitingDomain.set(false);
            this.triggerAiResponse('domain', extractDomainFromMessage(text));
            return;
        }

        const action = detectAction(text);
        if (action === 'domain') {
            this.awaitingDomain.set(true);
            this.triggerAiMessage('Sure! What real-world theme should I use? (e.g. space exploration, cooking, hospital management)');
        } else {
            this.triggerAiResponse(action);
        }
    }

    clickPill(pill: PillOption): void {
        this.pillsVisible.set(false);
        this.addUserMessage(pill.label.replace(/^[↑↓↻⏱]\s*/, ''));
        if (pill.action === 'domain') {
            this.awaitingDomain.set(true);
            this.triggerAiMessage('Sure! What real-world theme should I use? (e.g. space exploration, cooking, hospital management)');
        } else {
            this.triggerAiResponse(pill.action);
        }
    }

    acceptDiff(key: DiffKey): void {
        const diff = this.diffs().find((d) => d.key === key);
        if (!diff) return;
        this.diffs.update((ds) => ds.map((d) => (d.key === key ? { ...d, status: 'accepted' } : d)));
        const val = diff.newValue;
        if (key === 'difficulty') {
            this.editableFields.update((f) => ({ ...f, difficulty: val as DifficultyLevel }));
        } else if (key === 'title') {
            this.editableFields.update((f) => ({ ...f, title: val }));
        } else if (key === 'problemStatement') {
            this.editableFields.update((f) => ({ ...f, problemStatement: val }));
        }
    }

    declineDiff(key: DiffKey): void {
        this.diffs.update((ds) => ds.map((d) => (d.key === key ? { ...d, status: 'declined' } : d)));
    }

    acceptAll(): void {
        const pending = this.diffs().filter((d) => d.status === 'pending');
        for (const d of pending) {
            this.acceptDiff(d.key);
        }
    }

    declineAll(): void {
        this.diffs.update((ds) => ds.map((d) => (d.status === 'pending' ? { ...d, status: 'declined' } : d)));
    }

    applyAndCreate(): void {
        if (!this.hasChanges()) return;
        const variant = buildVariantFromFields(this.sourceExercise(), this.editableFields());
        const group = this.mockService.findGroupForExercise(this.sourceExercise().id ?? -1);
        if (group?.id !== undefined) {
            this.mockService.addVariantToGroup(variant, group.id);
        } else {
            const src = this.sourceExercise();
            this.mockService.addVariantWithNewGroup(variant, src, {
                title: (src.title ?? 'Exercise').split(':')[0].trim(),
                maxPoints: src.maxPoints,
                releaseDate: src.releaseDate,
                startDate: src.startDate,
                dueDate: src.dueDate,
                assessmentDueDate: src.assessmentDueDate,
            });
        }
        this.variantAdded.emit(variant);
        this.close();
    }

    private initialize(): void {
        this.chatMessages.set([]);
        this.diffs.set([]);
        this.chatInput.set('');
        this.tokensUsed.set(SYSTEM_TOKENS);
        this.aiTyping.set(false);
        this.pillsVisible.set(false);
        this.awaitingDomain.set(false);
        this.msgId = 0;
        this.clearTimer();
        this.editableFields.set(editableFieldsFromExercise(this.sourceExercise()));

        const config = this.initialConfig();
        if (this.mode() === 'v1' && config) {
            const msg = configToMessage(config);
            if (msg) {
                this.typingTimer = setTimeout(() => {
                    this.addUserMessage(msg);
                    const action = config.changeDifficulty ? (config.targetDifficulty === DifficultyLevel.EASY ? 'easier' : 'harder') : config.changeDomain ? 'domain' : 'generic';
                    this.triggerAiResponse(action, config.changeDomain ? config.domainText : '');
                }, 300);
                return;
            }
        }

        this.typingTimer = setTimeout(() => {
            const title = this.sourceExercise().title ?? 'this exercise';
            this.addAiMessage(`Hi! I'll help you create a variant of **${title}**. What would you like to change?`);
            this.pillsVisible.set(true);
        }, 400);
    }

    private addUserMessage(text: string): void {
        this.chatMessages.update((msgs) => [...msgs, { id: ++this.msgId, role: 'user', text }]);
        this.tokensUsed.update((t) => t + TOKENS_PER_USER);
        this.shouldScrollToBottom = true;
    }

    private addAiMessage(text: string): void {
        this.chatMessages.update((msgs) => [...msgs, { id: ++this.msgId, role: 'ai', text }]);
        this.tokensUsed.update((t) => t + TOKENS_PER_AI);
        this.shouldScrollToBottom = true;
    }

    private triggerAiMessage(text: string): void {
        this.aiTyping.set(true);
        this.shouldScrollToBottom = true;
        this.typingTimer = setTimeout(() => {
            this.aiTyping.set(false);
            this.addAiMessage(text);
        }, AI_TYPING_DELAY_MS);
    }

    private triggerAiResponse(action: string, extra = ''): void {
        this.aiTyping.set(true);
        this.shouldScrollToBottom = true;
        this.typingTimer = setTimeout(() => {
            this.aiTyping.set(false);
            const title = this.sourceExercise().title ?? 'this exercise';
            this.addAiMessage(aiResponseText(action, title));
            const newDiffs = buildDiffs(this.sourceExercise(), action, extra);
            // Merge: keep non-pending diffs, replace with new ones for the same keys
            this.diffs.update((current) => {
                const kept = current.filter((d) => d.status !== 'pending' && !newDiffs.some((nd) => nd.key === d.key));
                return [...kept, ...newDiffs];
            });
            this.pillsVisible.set(false);
        }, AI_TYPING_DELAY_MS);
    }

    private scrollToBottom(): void {
        const el = this.chatScrollEl()?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
    }

    private clearTimer(): void {
        if (this.typingTimer !== null) {
            clearTimeout(this.typingTimer);
            this.typingTimer = null;
        }
    }
}
