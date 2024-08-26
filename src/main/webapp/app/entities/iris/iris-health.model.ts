import { IrisRateLimitInformation } from 'app/entities/iris/iris-ratelimit-info.model';

export class IrisStatusDTO {
    active: boolean;
    rateLimitInfo: IrisRateLimitInformation;
}
