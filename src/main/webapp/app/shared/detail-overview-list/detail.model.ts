import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { SafeHtml } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel } from '@tumaet/apollon';
import dayjs from 'dayjs/esm';
import { IrisSubSettingsType } from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';

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
    | ProgrammingIrisEnabledDetail
    | ProgrammingRepositoryButtonsDetail
    | ProgrammingAuxiliaryRepositoryButtonsDetail
    | ProgrammingTestStatusDetail
    | ProgrammingDiffReportDetail
    | ProgrammingProblemStatementDetail
    | ProgrammingTimelineDetail
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
        type: RepositoryType;
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
    data: {
        repositoryDiffInformation?: RepositoryDiffInformation;
        templateFileContentByPath: Map<string, string>;
        solutionFileContentByPath: Map<string, string>;
        lineChangesLoading?: boolean;
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

interface ProgrammingCheckoutDirectoriesDetail extends DetailBase {
    type: DetailType.ProgrammingCheckoutDirectories;
    data: {
        exercise: ProgrammingExercise;
        programmingLanguage?: ProgrammingLanguage;
        isLocal: boolean;
    };
}
