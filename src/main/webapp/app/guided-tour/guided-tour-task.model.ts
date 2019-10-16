export class GuidedTourTask {
    key: string;
    labelTranslateKey: string;

    constructor(key: string, labelTranslateKey: string) {
        this.key = key;
        this.labelTranslateKey = labelTranslateKey;
    }
}

export const personUML = {
    name: 'person',
    attribute: 'name: string',
};

export const studentUML = {
    name: 'student',
    attribute: 'major: string',
    method: 'visitlecture()',
};

export const associationUML = {
    name: 'association',
};
