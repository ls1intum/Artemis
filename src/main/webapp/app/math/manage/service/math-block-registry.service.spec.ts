import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';

describe('MathBlockRegistryService', () => {
    setupTestBed({ zoneless: true });

    let service: MathBlockRegistryService;
    let httpMock: HttpTestingController;

    const sample: BlockDefinitionModel[] = [
        { type: 'add', displaySymbol: '+', layoutCategory: 'BINARY_INFIX' } as unknown as BlockDefinitionModel,
        { type: 'num', displaySymbol: 'n', layoutCategory: 'LEAF' } as unknown as BlockDefinitionModel,
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), MathBlockRegistryService],
        });
        service = TestBed.inject(MathBlockRegistryService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('loads the block registry and caches it in the signal', async () => {
        const promise = service.getBlockRegistry().toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: 'api/math/block-registry' });
        req.flush(sample);
        await promise;

        expect(service.blocks()).toEqual(sample);
    });

    it('resolves descriptorFor by type after load', async () => {
        const promise = service.getBlockRegistry().toPromise();
        httpMock.expectOne({ method: 'GET', url: 'api/math/block-registry' }).flush(sample);
        await promise;

        expect(service.descriptorFor('add')?.displaySymbol).toBe('+');
    });

    it('returns undefined for unknown block types', () => {
        expect(service.descriptorFor('mystery')).toBeUndefined();
    });
});
