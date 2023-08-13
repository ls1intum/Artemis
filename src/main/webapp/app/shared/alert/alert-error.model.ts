export class AlertError {
    constructor(public message: string, public translationKey?: string, public translationParams?: { [key: string]: unknown }) {}
}
