export class GuidedTourTask {
    labelTranslateKey: string;
    taskComplete: boolean;

    constructor(labelTranslateKey: string, taskComplete: boolean) {
        this.labelTranslateKey = labelTranslateKey;
        this.taskComplete = taskComplete;
    }
}
