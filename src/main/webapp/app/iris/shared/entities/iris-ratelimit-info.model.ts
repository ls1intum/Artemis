export class IrisRateLimitInformation {
    constructor(
        public currentMessageCount: number,
        public rateLimit: number,
        public rateLimitTimeframeHours: number,
    ) {}
}
