import { Signal } from '@angular/core';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { SafeHtml } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel } from '@tumaet/apollon';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { BuildPhase } from 'app/programming/shared/entities/build-plan-phases.model';

export type Detail = NotShownDetail | ShownDetail;

export type NotShownDetail = false | undefined;

export type ShownDetail =
    | TextDetail
    | DateDetail
    | LinkDetail
    | ImageDetail
    | DefaultProfilePicture
    | BooleanDetail
    | MarkdownDetail
    | GradingCriteriaDetail
    | ModelingEditorDetail
    | ProgrammingRepositoryBuildOverviewDetail
    | ProgrammingDiffReportDetail
    | ProgrammingProblemStatementDetail
    | ProgrammingTimelineDetail
    | ProgrammingBuildPhasesDetail
    | ExerciseCategoriesDetail;

export interface DetailBase {
    type: DetailType;
    title?: string;
    titleTranslationProps?: Record<string, string>;
    titleHelpText?: string;
}

export interface TextDetail extends DetailBase {
    type: DetailType.Text;
    data: { text?: string | number };
}

export interface DateDetail extends DetailBase {
    type: DetailType.Date;
    data: { date?: dayjs.Dayjs };
}

export interface LinkDetail extends DetailBase {
    type: DetailType.Link;
    data: { text?: string | number; href?: string | false; routerLink?: (string | number | undefined)[]; queryParams?: Record<string, string | number | undefined> };
}

export interface ImageDetail extends DetailBase {
    type: DetailType.Image;
    data: { altText?: string; imageUrl?: string };
}

export interface DefaultProfilePicture extends DetailBase {
    type: DetailType.DefaultProfilePicture;
    data: { color: string; initials: string };
}

export interface BooleanDetail extends DetailBase {
    type: DetailType.Boolean;
    data: { boolean?: boolean };
}

interface MarkdownDetail extends DetailBase {
    type: DetailType.Markdown;
    data: { innerHtml?: SafeHtml | null };
}

interface GradingCriteriaDetail extends DetailBase {
    type: DetailType.GradingCriteria;
    data: { gradingCriteria?: GradingCriterion[] };
}

interface ModelingEditorDetail extends DetailBase {
    type: DetailType.ModelingEditor;
    data: { isApollonEnabled?: boolean; umlModel?: UMLModel; diagramType?: UMLDiagramType; title?: string };
}

/** Data shared by the two live instructor build panels. */
export interface ProgrammingBuildStatusData {
    participation?: TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation;
    // Results load after the detail sections render. The signal schedules zoneless change detection.
    loading: Signal<boolean>;
    exercise: ProgrammingExercise;
    onParticipationChange: () => void;
    type: ProgrammingExerciseParticipationType;
    submissionRouterLink?: (string | number | undefined)[];
}
export interface ProgrammingDiffReportDetail extends DetailBase {
    type: DetailType.ProgrammingDiffReport;
    /**
     * The diff report is populated asynchronously after the detail sections have already been rendered.
     * The fields are therefore signals so that late updates schedule change detection in the zoneless app
     * (in-place mutation of a plain object would leave the view spinning until an unrelated event triggers CD).
     */
    data: {
        repositoryDiffInformation: Signal<RepositoryDiffInformation | undefined>;
        templateFileContentByPath: Signal<Map<string, string>>;
        solutionFileContentByPath: Signal<Map<string, string>>;
        lineChangesLoading: Signal<boolean>;
    };
}

interface ProgrammingProblemStatementDetail extends DetailBase {
    type: DetailType.ProgrammingProblemStatement;
    data: { exercise: ProgrammingExercise };
}

interface ProgrammingTimelineDetail extends DetailBase {
    type: DetailType.ProgrammingTimeline;
    data: { exercise: ProgrammingExercise; isExamMode?: boolean };
}

interface ProgrammingBuildPhasesDetail extends DetailBase {
    type: DetailType.ProgrammingBuildPhases;
    data: { phases: BuildPhase[]; isExamMode?: boolean };
}

export interface ExerciseCategoriesDetail extends DetailBase {
    type: DetailType.ExerciseCategories;
    data: { categories?: ExerciseCategory[] };
}

/** Paired repository/build details. Existing signal-backed status and comparison data retain live updates. */
export interface ProgrammingRepositoryBuildOverviewDetail extends DetailBase {
    type: DetailType.ProgrammingRepositoryBuildOverview;
    data: {
        exercise: ProgrammingExercise;
        localCIEnabled: boolean;
        supportsAuxiliaryRepositories: boolean;
        templateStatus: ProgrammingBuildStatusData;
        solutionStatus: ProgrammingBuildStatusData;
        comparison?: ProgrammingDiffReportDetail;
        editBuildPlanLink?: string[];
        checkConsistency?: () => void;
    };
}
