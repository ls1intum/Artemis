// export enum ProgrammingExerciseInputField {
//     // General section
//     title = 'title',
//     channelName = 'channelName',
//     shortName = 'shortName',
//     //
// }

export type ProgrammingExerciseInputField = 'title' | 'channelName' | 'shortName';

export type EditMode = 'SIMPLE' | 'ADVANCED';

export type InputFieldOptions = { editModesToBeDisplayed: EditMode[] };

export type InputFieldEditModeMapping = Record<ProgrammingExerciseInputField, InputFieldOptions>;

export const INPUT_FIELD_EDIT_MODE_MAPPING: InputFieldEditModeMapping = {
    title: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    channelName: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    shortName: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
};
