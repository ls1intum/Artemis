import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import type { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { ProgrammingExerciseInstructorRepositoryType } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { AuxiliaryRepository } from 'app/entities/programming/programming-exercise-auxiliary-repository-model';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming/programming-exercise-participation.model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { BuildLogStatisticsDTO } from 'app/entities/programming/build-log-statistics-dto';
import { DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { SafeHtml } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import dayjs from 'dayjs/esm';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { Course } from 'app/entities/course.model';

export type Detail = NotShownDetail | ShownDetail;

export type NotShownDetail = false | undefined;

export type ShownDetail =
    | TextDetail
    | DateDetail
    | LinkDetail
    | BooleanDetail
    | MarkdownDetail
    | GradingCriteriaDetail
    | ModelingEditorDetail
    | ProgrammingIrisEnabledDetail
    | ProgrammingRepositoryButtonsDetail
    | ProgrammingAuxiliaryRepositoryButtonsDetail
    | ProgrammingTestStatusDetail
    | ProgrammingDiffReportDetail
    | ProgrammingProblemStatementDetail
    | ProgrammingTimelineDetail
    | ProgrammingBuildStatisticsDetail
    | ProgrammingCheckoutDirectoriesDetail;

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
    data: { isApollonProfileActive?: boolean; umlModel?: UMLModel; diagramType?: UMLDiagramType; title?: string };
}

interface ProgrammingIrisEnabledDetail extends DetailBase {
    type: DetailType.ProgrammingIrisEnabled;
    data: { exercise?: ProgrammingExercise; course?: Course; disabled: boolean; subSettingsType: IrisSubSettingsType };
}

export interface ProgrammingRepositoryButtonsDetail extends DetailBase {
    type: DetailType.ProgrammingRepositoryButtons;
    data: {
        exerciseId?: number;
        participation?: TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation;
        type: ProgrammingExerciseInstructorRepositoryType;
    };
}

export interface ProgrammingAuxiliaryRepositoryButtonsDetail extends DetailBase {
    type: DetailType.ProgrammingAuxiliaryRepositoryButtons;
    data: { auxiliaryRepositories: AuxiliaryRepository[]; exerciseId?: number };
}

export interface ProgrammingTestStatusDetail extends DetailBase {
    type: DetailType.ProgrammingTestStatus;
    data: {
        participation?: TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation;
        loading?: boolean;
        exercise: ProgrammingExercise;
        onParticipationChange: () => void;
        type: ProgrammingExerciseParticipationType;
        submissionRouterLink?: (string | number | undefined)[];
    };
}
export interface ProgrammingDiffReportDetail extends DetailBase {
    type: DetailType.ProgrammingDiffReport;
    data: { addedLineCount: number; removedLineCount: number; isLoadingDiffReport?: boolean; gitDiffReport?: ProgrammingExerciseGitDiffReport };
}

interface ProgrammingProblemStatementDetail extends DetailBase {
    type: DetailType.ProgrammingProblemStatement;
    data: { exercise: ProgrammingExercise };
}

interface ProgrammingTimelineDetail extends DetailBase {
    type: DetailType.ProgrammingTimeline;
    data: { exercise: ProgrammingExercise; isExamMode?: boolean };
}

interface ProgrammingBuildStatisticsDetail extends DetailBase {
    type: DetailType.ProgrammingBuildStatistics;
    data: {
        buildLogStatistics: BuildLogStatisticsDTO;
    };
}

interface ProgrammingCheckoutDirectoriesDetail extends DetailBase {
    type: DetailType.ProgrammingCheckoutDirectories;
    data: {
        exercise: ProgrammingExercise;
        programmingLanguage?: ProgrammingLanguage;
        isLocal: boolean;
    };
}
