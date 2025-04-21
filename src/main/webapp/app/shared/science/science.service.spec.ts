import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventDTO, ScienceEventType } from 'app/shared/science/science.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from 'test/helpers/mocks/service/mock-local-storage.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { of } from 'rxjs';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('ScienceService', () => {
    let scienceService: ScienceService;
    let httpService: HttpClient;
    let featureToggleService: FeatureToggleService;
    let putStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: HttpClient, useClass: MockHttpService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .compileComponents()
            .then(() => {
                httpService = TestBed.inject(HttpClient);
                featureToggleService = TestBed.inject(FeatureToggleService);
                jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
                scienceService = TestBed.inject(ScienceService);
                putStub = jest.spyOn(httpService, 'put');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should send a request to the server to log event', () => {
        const type = ScienceEventType.LECTURE__OPEN;
        scienceService.logEvent(type);
        const event = new ScienceEventDTO();
        event.type = type;
        expect(putStub).toHaveBeenCalledExactlyOnceWith('api/atlas/science', event, { observe: 'response' });
    });
});
