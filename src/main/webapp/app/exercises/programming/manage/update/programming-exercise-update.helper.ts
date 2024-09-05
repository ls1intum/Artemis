export enum ProgrammingExerciseInputField {
    title = 'title',
    shortName = 'shortName',
}

export type EditMode = 'SIMPLE' | 'ADVANCED';

export type InputFieldOptions = { editModesToBeDisplayed: EditMode[] };

export type InputFieldEditModeMapping = Record<ProgrammingExerciseInputField, InputFieldOptions>;

export const INPUT_FIELD_EDIT_MODE_MAPPING: InputFieldEditModeMapping = {
    title: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    shortName: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
};
