import { signal } from '@angular/core';
import { ScienceCourseConsent } from 'app/account/user/settings/science-settings/science-settings.service';

export class MockScienceService {
    readonly courseConsent = signal<ScienceCourseConsent | undefined>(undefined);
    logEvent = (_type: string, _resourceId?: number, _courseId?: number) => true;
}
