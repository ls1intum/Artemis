import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { ProgrammingExerciseInstructorRepositoryType } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { BuildLogStatisticsDTO } from 'app/exercises/programming/manage/build-log-statistics-dto';
import { DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { SafeHtml } from '@angular/platform-browser';
import { UMLModel } from '@ls1intum/apollon/lib/es6/typings';
import dayjs from 'dayjs/esm';

type DetailBase = {
    type: DetailType;
    title?: string;
    titleTranslationProps?: Record<string, string>;
    titleHelpText?: string;
};

export type Detail = NotShownDetail | ShownDetail;

type NotShownDetail = false | undefined;

type ShownDetail = DetailBase &
    (
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
    );

type TextDetail = { type: DetailType.Text; data: { text?: string | number } };

type DateDetail = { type: DetailType.Date; data: { date?: dayjs.Dayjs } };

type LinkDetail = { type: DetailType.Link; data: { text?: string | number; href?: string | false; routerLink?: (string | number | undefined)[] } };

type BooleanDetail = { type: DetailType.Boolean; data: { boolean?: boolean } };

type MarkdownDetail = { type: DetailType.Markdown; data: { innerHtml: SafeHtml | null } };

type GradingCriteriaDetail = { type: DetailType.GradingCriteria; data: { gradingCriteria?: GradingCriterion[] } };

type ModelingEditorDetail = { type: DetailType.ModelingEditor; data: { isApollonProfileActive?: boolean; umlModel?: UMLModel; diagramType?: UMLDiagramType; title?: string } };

type ProgrammingIrisEnabledDetail = { type: DetailType.ProgrammingIrisEnabled; data: { exercise: ProgrammingExercise } };

type ProgrammingRepositoryButtonsDetail = {
    type: DetailType.ProgrammingRepositoryButtons;
    data: {
        exerciseId?: number;
        participation?: TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation;
        showOpenLink?: boolean;
        type: ProgrammingExerciseInstructorRepositoryType;
    };
};

type ProgrammingAuxiliaryRepositoryButtonsDetail = {
    type: DetailType.ProgrammingAuxiliaryRepositoryButtons;
    data: { auxiliaryRepositories: AuxiliaryRepository[]; exerciseId?: number; showOpenLink?: boolean };
};

type ProgrammingTestStatusDetail = {
    type: DetailType.ProgrammingTestStatus;
    data: {
        participation?: TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation;
        loading?: boolean;
        exercise: ProgrammingExercise;
        onParticipationChange: () => void;
        type: ProgrammingExerciseParticipationType;
        submissionRouterLink?: (string | number | undefined)[];
    };
};

type ProgrammingDiffReportDetail = {
    type: DetailType.ProgrammingDiffReport;
    data: { addedLineCount: number; removedLineCount: number; isLoadingDiffReport?: boolean; gitDiffReport?: ProgrammingExerciseGitDiffReport };
};

type ProgrammingProblemStatementDetail = {
    type: DetailType.ProgrammingProblemStatement;
    data: { exercise: ProgrammingExercise };
};

type ProgrammingTimelineDetail = {
    type: DetailType.ProgrammingTimeline;
    data: { exercise: ProgrammingExercise; isExamMode?: boolean };
};

type ProgrammingBuildStatisticsDetail = {
    type: DetailType.ProgrammingBuildStatistics;
    data: {
        buildLogStatistics: BuildLogStatisticsDTO;
    };
};
