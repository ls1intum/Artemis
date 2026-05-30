import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ScienceService } from 'app/foundation/science/science.service';
import { ScienceEventDTO, ScienceEventType } from 'app/foundation/science/science.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { of } from 'rxjs';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ScienceService', () => {
    setupTestBed({ zoneless: true });
    let scienceService: ScienceService;
    let httpService: HttpClient;
    let featureToggleService: FeatureToggleService;
    let putStub: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: HttpClient, useClass: MockHttpService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .compileComponents()
            .then(() => {
                httpService = TestBed.inject(HttpClient);
                featureToggleService = TestBed.inject(FeatureToggleService);
                vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
                scienceService = TestBed.inject(ScienceService);
                putStub = vi.spyOn(httpService, 'put');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should send a request to the server to log event', () => {
        const type = ScienceEventType.LECTURE__OPEN;
        scienceService.logEvent(type);
        const event = new ScienceEventDTO();
        event.type = type;
        expect(putStub).toHaveBeenCalledExactlyOnceWith('api/atlas/science', event, { observe: 'response' });
    });
});
