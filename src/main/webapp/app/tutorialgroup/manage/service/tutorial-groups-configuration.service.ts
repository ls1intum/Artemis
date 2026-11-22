import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { toISO8601DateString } from 'app/foundation/util/date.utils';
import { TutorialGroupsConfigurationApi } from 'app/openapi/api/tutorial-groups-configuration-api';
import { TutorialGroupConfiguration } from 'app/openapi/model/tutorial-group-configuration';
import { TutorialGroupConfigurationFreePeriod } from 'app/openapi/model/tutorial-group-configuration-free-period';

/** What the configuration form submits. The form submits only once its validator accepts a complete period. */
export interface TutorialGroupsConfigurationSettings {
    period: [Date, Date];
    useTutorialGroupChannels: boolean;
    usePublicTutorialGroupChannels: boolean;
}

@Service()
export class TutorialGroupsConfigurationService {
    private readonly api = inject(TutorialGroupsConfigurationApi);

    /** Emits undefined while the course has no configuration: the server then answers with an empty body. */
    getOneOfCourse(courseId: number): Observable<TutorialGroupConfiguration | undefined> {
        return this.api.getOneOfCourse(courseId).pipe(map((configuration) => configuration ?? undefined));
    }

    create(courseId: number, settings: TutorialGroupsConfigurationSettings): Observable<TutorialGroupConfiguration> {
        return this.api.create(courseId, toRequest(settings, []));
    }

    /** Sends the configuration's free periods along, so the server rejects a period that no longer contains them. */
    update(
        courseId: number,
        configurationId: number,
        freePeriods: TutorialGroupConfigurationFreePeriod[],
        settings: TutorialGroupsConfigurationSettings,
    ): Observable<TutorialGroupConfiguration> {
        return this.api.update(courseId, configurationId, toRequest(settings, freePeriods, configurationId));
    }
}

function toRequest(settings: TutorialGroupsConfigurationSettings, tutorialGroupFreePeriods: TutorialGroupConfigurationFreePeriod[], id?: number): TutorialGroupConfiguration {
    const [start, end] = settings.period;
    return {
        id,
        tutorialPeriodStartInclusive: toISO8601DateString(start),
        tutorialPeriodEndInclusive: toISO8601DateString(end),
        useTutorialGroupChannels: settings.useTutorialGroupChannels,
        usePublicTutorialGroupChannels: settings.usePublicTutorialGroupChannels,
        tutorialGroupFreePeriods,
    };
}
