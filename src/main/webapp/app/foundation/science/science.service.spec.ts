import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { ScienceService } from 'app/foundation/science/science.service';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';

describe('ScienceService', () => {
    let scienceService: ScienceService;
    let httpService: HttpClient;
    let putStub: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                ScienceService,
                { provide: HttpClient, useClass: MockHttpService },
                MockProvider(AccountService, { getAuthenticationState: () => of({} as User) }),
                MockProvider(FeatureToggleService, { getFeatureToggleActive: (feature: FeatureToggle) => of(feature === FeatureToggle.Science) }),
                MockProvider(ScienceSettingsService, {
                    getScienceSettingsUpdates: () => of([]),
                    refreshScienceSettings: () => of([]),
                    eventLoggingAllowed: (courseId?: number) => courseId === 1,
                }),
                MockProvider(Router, { url: '/courses/1/exercises' }),
            ],
        });

        httpService = TestBed.inject(HttpClient);
        scienceService = TestBed.inject(ScienceService);
        putStub = vi.spyOn(httpService, 'put');
    });

    it('should send a course-scoped request to the server to log event', () => {
        const type = ScienceEventType.LECTURE__OPEN;

        scienceService.logEvent(type);

        expect(putStub).toHaveBeenCalledOnce();
        expect(putStub).toHaveBeenCalledWith('api/atlas/science', expect.objectContaining({ type, courseId: 1 }), { observe: 'response' });
    });
});
