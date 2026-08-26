import { Exercise, ExerciseMode, IncludedInOverallScore, ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TimelineStatus } from 'app/shared-ui/timeline/timeline.component';
import type { ExerciseUpdatePlagiarismComponent } from 'app/plagiarism/manage/exercise-update-plagiarism/exercise-update-plagiarism.component';

const MIN_POINTS = 1;
const MAX_POINTS = 9999;
const MIN_BONUS_POINTS = 0;
const MAX_BONUS_POINTS = 9999;
const MIN_TEAM_SIZE = 1;
const MAX_TEAM_SIZE = 99;

/** Facts a form's validity depends on that cannot be read off the exercise model itself. */
export interface ExerciseValidationViewState {
    isExamMode: boolean;
    /** Omit when the form does not enforce a minimum title length. */
    minTitleLength?: number;
    isTitleDisallowed: boolean;
    isChannelNameRequired: boolean;
    timelineStatus: TimelineStatus;
    isExampleSolutionPublicationDateInputValid: boolean;
}

/** The checks every exercise type shares; type-specific ones are added by the calling component. */
export function getCommonExerciseInvalidReasons(exercise: Exercise, viewState: ExerciseValidationViewState): ValidationReason[] {
    const reasons: ValidationReason[] = [];

    validateTitle(exercise, viewState, reasons);
    validateChannelName(exercise, viewState, reasons);
    validateTeamSize(exercise, reasons);
    validatePoints(exercise, reasons);
    validateBonusPoints(exercise, reasons);

    if (!viewState.isExamMode) {
        validateExampleSolutionPublicationDate(exercise, viewState, reasons);
        reasons.push(...getTimelineInvalidReasons(viewState.timelineStatus));
    }

    return reasons;
}

export function getPlagiarismInvalidReasons(plagiarismComponent: ExerciseUpdatePlagiarismComponent | undefined): ValidationReason[] {
    if (!plagiarismComponent || plagiarismComponent.isFormValid()) {
        return [];
    }

    const controlMessageMap: Record<string, string> = {
        similarityThreshold: 'artemisApp.exercise.form.continuousPlagiarismControl.similarityThreshold.pattern',
        minimumScore: 'artemisApp.exercise.form.continuousPlagiarismControl.minimumScore.customMin',
        minimumSize: 'artemisApp.exercise.form.continuousPlagiarismControl.minimumSize.customMin',
        continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod:
            'artemisApp.exercise.form.continuousPlagiarismControl.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod.pattern',
    };

    const controls = plagiarismComponent.form.controls;
    return Object.entries(controlMessageMap)
        .filter(([controlName]) => controls[controlName as keyof typeof controls]?.invalid)
        .map(([, translateKey]) => ({ translateKey, translateValues: {} }));
}

function getTimelineInvalidReasons(timelineStatus: TimelineStatus): ValidationReason[] {
    return timelineStatus.invalidItems.map((item) => ({ translateKey: item.reasonKey, translateValues: { dateName: item.dateName } }));
}

function validateTitle(exercise: Exercise, viewState: ExerciseValidationViewState, reasons: ValidationReason[]): void {
    const title = exercise.title;
    if (title === undefined || title === '') {
        reasons.push({ translateKey: 'artemisApp.exercise.form.title.undefined', translateValues: {} });
        return;
    }

    const minTitleLength = viewState.minTitleLength;
    if (minTitleLength !== undefined && title.length < minTitleLength) {
        reasons.push({ translateKey: 'artemisApp.exercise.form.title.minlength', translateValues: { min: minTitleLength } });
        return;
    }

    if (viewState.isTitleDisallowed) {
        reasons.push({ translateKey: 'artemisApp.exercise.form.title.disallowedValue', translateValues: {} });
    }
}

function validateChannelName(exercise: Exercise, viewState: ExerciseValidationViewState, reasons: ValidationReason[]): void {
    if (viewState.isChannelNameRequired && !exercise.channelName) {
        reasons.push({ translateKey: 'artemisApp.exercise.form.channelName.empty', translateValues: {} });
    }
}

function validateTeamSize(exercise: Exercise, reasons: ValidationReason[]): void {
    if (exercise.mode !== ExerciseMode.TEAM) {
        return;
    }

    const config = exercise.teamAssignmentConfig;
    validateTeamSizeBound(config?.minTeamSize, 'minTeamSize', reasons);
    validateTeamSizeBound(config?.maxTeamSize, 'maxTeamSize', reasons);
}

function validateTeamSizeBound(teamSize: number | undefined, field: 'minTeamSize' | 'maxTeamSize', reasons: ValidationReason[]): void {
    if (teamSize === undefined || teamSize === null) {
        reasons.push({ translateKey: `artemisApp.exercise.form.${field}.required`, translateValues: {} });
    } else if (teamSize < MIN_TEAM_SIZE) {
        reasons.push({ translateKey: `artemisApp.exercise.form.${field}.min`, translateValues: {} });
    } else if (teamSize > MAX_TEAM_SIZE) {
        reasons.push({ translateKey: `artemisApp.exercise.form.${field}.max`, translateValues: {} });
    }
}

function validatePoints(exercise: Exercise, reasons: ValidationReason[]): void {
    const maxPoints = exercise.maxPoints;
    if (maxPoints === undefined || maxPoints === null) {
        reasons.push({ translateKey: 'artemisApp.exercise.form.points.undefined', translateValues: {} });
    } else if (maxPoints < MIN_POINTS) {
        reasons.push({ translateKey: 'artemisApp.exercise.form.points.customMin', translateValues: {} });
    } else if (maxPoints > MAX_POINTS) {
        reasons.push({ translateKey: 'artemisApp.exercise.form.points.customMax', translateValues: {} });
    }
}

function validateBonusPoints(exercise: Exercise, reasons: ValidationReason[]): void {
    const bonusPoints = exercise.bonusPoints;
    if (bonusPoints === undefined || bonusPoints === null) {
        if (exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY) {
            reasons.push({ translateKey: 'artemisApp.exercise.form.bonusPoints.undefined', translateValues: {} });
        }
    } else if (bonusPoints < MIN_BONUS_POINTS) {
        reasons.push({ translateKey: 'artemisApp.exercise.form.bonusPoints.customMin', translateValues: {} });
    } else if (bonusPoints > MAX_BONUS_POINTS) {
        reasons.push({ translateKey: 'artemisApp.exercise.form.bonusPoints.customMax', translateValues: {} });
    }
}

function validateExampleSolutionPublicationDate(exercise: Exercise, viewState: ExerciseValidationViewState, reasons: ValidationReason[]): void {
    if (exercise.exampleSolutionPublicationDateError) {
        reasons.push({ translateKey: 'artemisApp.exercise.exampleSolutionPublicationDateError', translateValues: {} });
    } else if (!viewState.isExampleSolutionPublicationDateInputValid) {
        reasons.push({ translateKey: 'artemisApp.exercise.form.exampleSolutionPublicationDate.invalidInput', translateValues: {} });
    }
}
