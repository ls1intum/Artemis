import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MetisService } from 'app/communication/metis.service';
import { MockMetisService } from '../../../../../../../test/javascript/spec/helpers/mocks/service/mock-metis-service.service';
import { PostTagSelectorComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-tag-selector/post-tag-selector.component';
import { metisTags } from '../../../../../../../test/javascript/spec/helpers/sample/metis-sample-data';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';

describe('PostTagSelectorComponent', () => {
    let component: PostTagSelectorComponent;
    let fixture: ComponentFixture<PostTagSelectorComponent>;
    let metisService: MetisService;
    let metisServiceGetTagSpy: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostTagSelectorComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceGetTagSpy = jest.spyOn(metisService, 'tags', 'get');
                component.postTags = [];
                component.ngOnInit();
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should be initialized with empty list of tags', () => {
        expect(component.tags).toEqual([]);
    });

    it('should be initialized with existing list of tags', fakeAsync(() => {
        tick();
        expect(metisServiceGetTagSpy).toHaveBeenCalledOnce();
        component.existingPostTags.subscribe((tags) => {
            expect(tags).toEqual(metisTags);
        });
    }));

    // TODO: implement a test which removes a category and one which adds a category
});
