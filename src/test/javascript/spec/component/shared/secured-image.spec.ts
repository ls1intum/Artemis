import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import { SinonStub, stub } from 'sinon';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArTEMiSTestModule } from '../../test.module';
import { ResultComponent, UpdatingResultComponent } from 'app/entities/result';
import { ArTEMiSSharedModule, CacheableImageService, CachingStrategy, SecuredImageComponent } from 'app/shared';
import { MockCacheableImageService } from '../../mocks/mock-cacheable-image.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('SecuredImageComponent', () => {
    let comp: SecuredImageComponent;
    let fixture: ComponentFixture<SecuredImageComponent>;
    let debugElement: DebugElement;
    let cacheableImageService: CacheableImageService;

    let loadCachedLocalStorageStub: SinonStub;
    let loadCachedSessionStorageStub: SinonStub;
    let loadWithoutCacheStub: SinonStub;

    const src = 'this/is/a/fake/url';

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, ArTEMiSSharedModule],
            declarations: [UpdatingResultComponent, MockComponent(ResultComponent)],
            providers: [{ provide: CacheableImageService, useClass: MockCacheableImageService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SecuredImageComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                cacheableImageService = debugElement.injector.get(CacheableImageService);

                loadCachedLocalStorageStub = stub(cacheableImageService, 'loadCachedLocalStorage');
                loadCachedSessionStorageStub = stub(cacheableImageService, 'loadCachedSessionStorage');
                loadWithoutCacheStub = stub(cacheableImageService, 'loadWithoutCache');

                // @ts-ignore
                comp.src = src;
            });
    });

    afterEach(() => {
        loadCachedSessionStorageStub.restore();
        loadCachedLocalStorageStub.restore();
        loadWithoutCacheStub.restore();
    });

    it('should not use cache if cache strategy is set to none', fakeAsync(() => {
        comp.cachingStrategy = CachingStrategy.NONE;
        comp.ngOnChanges();

        fixture.detectChanges();
        tick();

        expect(loadWithoutCacheStub).to.have.been.calledOnceWithExactly(src);
    }));
});
