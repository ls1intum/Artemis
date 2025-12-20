/**
 * Vitest tests for ActivateService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivateService } from 'app/core/account/activate/activate.service';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { HttpClient, HttpParams } from '@angular/common/http';

describe('ActivateService', () => {
    setupTestBed({ zoneless: true });

    let activateService: ActivateService;
    let httpService: HttpClient;
    let getStub: ReturnType<typeof vi.spyOn>;

    const getURL = 'api/core/public/activate';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        })
            .compileComponents()
            .then(() => {
                httpService = TestBed.inject(HttpClient);
                activateService = TestBed.inject(ActivateService);
                getStub = vi.spyOn(httpService, 'get');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should send a request to the server to activate the user', () => {
        const key = 'key';

        activateService.get(key).subscribe();

        expect(getStub).toHaveBeenCalledOnce();
        expect(getStub).toHaveBeenCalledWith(getURL, {
            params: new HttpParams().set('key', key),
        });
    });
});
