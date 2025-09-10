import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';

export class IrisStatusDTO {
    active: boolean;
    rateLimitInfo: IrisRateLimitInformation;
}
