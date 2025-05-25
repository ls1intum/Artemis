export * from './tutorialGroupApi.service';
import { TutorialGroupApiService } from './tutorialGroupApi.service';
export * from './tutorialGroupFreePeriodApi.service';
import { TutorialGroupFreePeriodApiService } from './tutorialGroupFreePeriodApi.service';
export * from './tutorialGroupSessionApi.service';
import { TutorialGroupSessionApiService } from './tutorialGroupSessionApi.service';
export const APIS = [TutorialGroupApiService, TutorialGroupFreePeriodApiService, TutorialGroupSessionApiService];
