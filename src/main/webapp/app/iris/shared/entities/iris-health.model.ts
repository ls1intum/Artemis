import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';

export interface IrisStatusDTO {
    active: boolean;
    rateLimitInfo: IrisRateLimitInformation;
}
