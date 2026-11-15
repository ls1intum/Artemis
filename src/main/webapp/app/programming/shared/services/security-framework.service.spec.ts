import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SecurityPolicyChange } from 'app/programming/shared/entities/security-policy.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';

const URL = 'api/programming/programming-exercises/42/security-framework';

describe('SecurityFrameworkService', () => {
    let service: SecurityFrameworkService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(SecurityFrameworkService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('reads the policies, the test structure and the template files', () => {
        service.getPolicies(42).subscribe();
        service.getTestStructure(42).subscribe();
        service.getTemplateFiles(42).subscribe();

        expect(http.expectOne({ method: 'GET', url: `${URL}/policies` })).toBeDefined();
        expect(http.expectOne({ method: 'GET', url: `${URL}/test-structure` })).toBeDefined();
        expect(http.expectOne({ method: 'GET', url: `${URL}/template-files` })).toBeDefined();
    });

    it('previews and commits a change and imports a file', () => {
        const change: SecurityPolicyChange = { baseCommitHash: 'abc', commitMessage: 'Update', policies: [], removedPolicyPaths: ['Old.yaml'] };

        service.previewPolicies(42, change).subscribe();
        service.commitPolicies(42, change).subscribe();
        service.importPolicy(42, 'SecurityPolicy.yaml', 'content').subscribe();

        expect(http.expectOne({ method: 'POST', url: `${URL}/policies/preview` }).request.body).toEqual(change);
        expect(http.expectOne({ method: 'PUT', url: `${URL}/policies` }).request.body).toEqual(change);
        expect(http.expectOne({ method: 'POST', url: `${URL}/policies/import` }).request.body).toEqual({ policyPath: 'SecurityPolicy.yaml', content: 'content' });
    });
});
