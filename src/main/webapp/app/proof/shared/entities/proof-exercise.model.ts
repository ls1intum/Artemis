import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { MathNode } from './math-node.model';
import { DerivationStep } from './derivation-step.model';
import { DEFAULT_GRADER_TYPE, GraderType } from './grader-type.model';
import { DEFAULT_GOAL_MODE, GoalMode } from './goal-mode.model';

export class ProofExercise extends Exercise {
    public exampleSolution?: string;
    public description?: string;
    public sourceExpression?: MathNode;
    public targetExpression?: MathNode;
    public manualDerivation?: boolean;
    public allowVerification?: boolean;
    public onlyShowApplicableRules?: boolean;
    public partialCreditEnabled?: boolean;
    /** When true the grader treats {@code +} and {@code ·} as commutative/associative for equality comparisons. */
    public acNormalization?: boolean;
    /** Backend grader to dispatch to. Optional in the type so a generic Exercise cast survives; defaults to REWRITE_CHAIN at construction. */
    public graderType?: GraderType = DEFAULT_GRADER_TYPE;
    /** How the goal is encoded — source→target or single equation closed by tautology. Optional in the type for the same reason as graderType. */
    public goalMode?: GoalMode = DEFAULT_GOAL_MODE;
    /** Single goal tree (typically an equality) for EQUATION mode. Unused in TRANSFORMATION mode. */
    public goalExpression?: MathNode;
    public exampleDerivations?: DerivationStep[][];

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.PROOF);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
    }
}
