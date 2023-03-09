import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { CacheableImageService } from 'app/shared/image/cacheable-image.service';
import { CachingStrategy, ImageLoadingStatus, SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { MockComponent } from 'ng-mocks';
import { of } from 'rxjs';
import { MockCacheableImageService } from '../../helpers/mocks/service/mock-cacheable-image.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { ArtemisTestModule } from '../../test.module';

describe('SecuredImageComponent', () => {
    let comp: SecuredImageComponent;
    let fixture: ComponentFixture<SecuredImageComponent>;
    let debugElement: DebugElement;
    let cacheableImageService: CacheableImageService;

    let loadCachedLocalStorageStub: jest.SpyInstance;
    let loadCachedSessionStorageStub: jest.SpyInstance;
    let loadWithoutCacheStub: jest.SpyInstance;

    // @ts-ignore
    global.URL.createObjectURL = jest.fn();

    let endLoadingProcessStub: jest.SpyInstance;

    const src = 'this/is/a/fake/url';
    const base64String =
        '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';

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SecuredImageComponent, UpdatingResultComponent, MockComponent(ResultComponent)],
            providers: [
                { provide: CacheableImageService, useClass: MockCacheableImageService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SecuredImageComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                cacheableImageService = debugElement.injector.get(CacheableImageService);

                loadCachedLocalStorageStub = jest.spyOn(cacheableImageService, 'loadCachedLocalStorage');
                loadCachedSessionStorageStub = jest.spyOn(cacheableImageService, 'loadCachedSessionStorage');
                loadWithoutCacheStub = jest.spyOn(cacheableImageService, 'loadWithoutCache');

                endLoadingProcessStub = jest.spyOn(comp.endLoadingProcess, 'emit');
            });
    });

    afterEach(() => {
        loadCachedSessionStorageStub.mockRestore();
        loadCachedLocalStorageStub.mockRestore();
        loadWithoutCacheStub.mockRestore();
        endLoadingProcessStub.mockRestore();
    });

    it('should not use cache if cache strategy is set to none', () => {
        comp.cachingStrategy = CachingStrategy.NONE;
        loadWithoutCacheStub.mockReturnValue(of(base64String));

        // @ts-ignore
        comp.src = src;
        comp.ngOnInit();
        triggerChanges(comp);
        fixture.detectChanges();

        expect(endLoadingProcessStub).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);
        expect(loadWithoutCacheStub).toHaveBeenCalledWith(src);
        expect(loadCachedSessionStorageStub).not.toHaveBeenCalled();
        expect(loadCachedLocalStorageStub).not.toHaveBeenCalled();
    });

    it('should use the local storage as a cache if selected as the storage strategy', () => {
        comp.cachingStrategy = CachingStrategy.LOCAL_STORAGE;
        loadCachedLocalStorageStub.mockReturnValue(of(base64String));

        // @ts-ignore
        comp.src = src;
        comp.ngOnInit();
        triggerChanges(comp);
        fixture.detectChanges();

        expect(endLoadingProcessStub).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);
        expect(loadWithoutCacheStub).not.toHaveBeenCalled();
        expect(loadCachedSessionStorageStub).not.toHaveBeenCalled();
        expect(loadCachedLocalStorageStub).toHaveBeenCalledWith(src);
    });

    it('should use the session storage as a cache if selected as the storage strategy', () => {
        comp.cachingStrategy = CachingStrategy.SESSION_STORAGE;
        loadCachedSessionStorageStub.mockReturnValue(of(base64String));

        // @ts-ignore
        comp.src = src;
        comp.ngOnInit();
        triggerChanges(comp);
        fixture.detectChanges();

        expect(endLoadingProcessStub).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);
        expect(loadWithoutCacheStub).not.toHaveBeenCalled();
        expect(loadCachedSessionStorageStub).toHaveBeenCalledWith(src);
        expect(loadCachedLocalStorageStub).not.toHaveBeenCalled();
    });

    it('should use the session storage as a cache as the default storage strategy', () => {
        loadCachedSessionStorageStub.mockReturnValue(of(base64String));

        // @ts-ignore
        comp.src = src;
        comp.ngOnInit();
        triggerChanges(comp);
        fixture.detectChanges();

        expect(endLoadingProcessStub).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);
        expect(loadWithoutCacheStub).not.toHaveBeenCalled();
        expect(loadCachedSessionStorageStub).toHaveBeenCalledWith(src);
        expect(loadCachedLocalStorageStub).not.toHaveBeenCalled();
    });
});
