export interface Context {
    state: State;
}

export enum SubmissionButtonStates {
    NONE,
    NEW,
    UPDATE,
    EDIT,
}

export enum AssessButtonStates {
    NONE,
    ASSESS,
    CREATE,
    UPDATE,
}

export enum UIStates {
    SUBMISSION,
    ASSESSMENT,
}

export abstract class State {
    protected constructor(
        protected context: Context,
        public readonly ui: UIStates,
        public readonly submissionEditButton: SubmissionButtonStates,
        public readonly assessButton: AssessButtonStates,
    ) {}

    edit(): void {
        this.context.state = new EditState(this.context);
    }

    assess(): void {
        this.context.state = new AssessState(this.context);
    }

    static initialWithContext = (context: Context): State => new NewState(context);
    static forExistingAssessmentWithContext = (context: Context): State => new AssessState(context);
    static forCompletion = (context: Context): State => new CompletionState(context);
}

class NewState extends State {
    constructor(context: Context) {
        super(context, UIStates.SUBMISSION, SubmissionButtonStates.NEW, AssessButtonStates.NONE);
    }
}

class EditState extends State {
    constructor(context: Context) {
        super(context, UIStates.SUBMISSION, SubmissionButtonStates.UPDATE, AssessButtonStates.ASSESS);
    }

    edit() {}
    assess() {
        this.context.state = new NewAssessmentState(this.context);
    }
}

class NewAssessmentState extends State {
    constructor(context: Context) {
        super(context, UIStates.ASSESSMENT, SubmissionButtonStates.EDIT, AssessButtonStates.CREATE);
    }
}

class AssessState extends State {
    constructor(context: Context) {
        super(context, UIStates.ASSESSMENT, SubmissionButtonStates.EDIT, AssessButtonStates.UPDATE);
    }
    assess() {}
}

class CompletionState extends State {
    constructor(context: Context) {
        super(context, UIStates.ASSESSMENT, SubmissionButtonStates.NONE, AssessButtonStates.NONE);
    }
    edit() {}
    assess() {}
}
