import dayjs from 'dayjs/esm';

export class TutorialGroupConfigurationDTO {
    public id?: number;
    public tutorialPeriodStartInclusive?: dayjs.Dayjs;
    public tutorialPeriodEndInclusive?: dayjs.Dayjs;
    public useTutorialGroupChannels?: boolean;
    public usePublicTutorialGroupChannels?: boolean;
}
