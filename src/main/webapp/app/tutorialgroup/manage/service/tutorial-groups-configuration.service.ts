import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { toISO8601DateString } from 'app/foundation/util/date.utils';
import { TutorialGroupsConfigurationApi } from 'app/openapi/api/tutorial-groups-configuration-api';
import { TutorialGroupConfiguration } from 'app/openapi/model/tutorial-group-configuration';
import type { TutorialGroupsConfigurationFormData } from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';

@Service()
export class TutorialGroupsConfigurationService {
    private readonly api = inject(TutorialGroupsConfigurationApi);

    /** Emits undefined while the course has no configuration: the server then answers with an empty body. */
    getOneOfCourse(courseId: number): Observable<TutorialGroupConfiguration | undefined> {
        return this.api.getOneOfCourse(courseId).pipe(map((configuration) => configuration ?? undefined));
    }

    create(courseId: number, settings: TutorialGroupsConfigurationFormData): Observable<TutorialGroupConfiguration> {
        return this.api.create(courseId, toRequest(settings));
    }

    /** Sends the settings along with the id and the free periods of the configuration as it was loaded. */
    update(
        courseId: number,
        configurationId: number,
        configuration: TutorialGroupConfiguration,
        settings: TutorialGroupsConfigurationFormData,
    ): Observable<TutorialGroupConfiguration> {
        return this.api.update(courseId, configurationId, toRequest(settings, configuration));
    }
}

function toRequest(settings: TutorialGroupsConfigurationFormData, loaded?: TutorialGroupConfiguration): TutorialGroupConfiguration {
    // The form only submits a complete period, see tutorialPeriodRangeValidator.
    const [start, end] = settings.period ?? [];
    return {
        id: loaded?.id,
        tutorialPeriodStartInclusive: toISO8601DateString(start)!,
        tutorialPeriodEndInclusive: toISO8601DateString(end)!,
        useTutorialGroupChannels: settings.useTutorialGroupChannels ?? false,
        usePublicTutorialGroupChannels: settings.usePublicTutorialGroupChannels ?? false,
        tutorialGroupFreePeriods: loaded?.tutorialGroupFreePeriods ?? [],
    };
}
