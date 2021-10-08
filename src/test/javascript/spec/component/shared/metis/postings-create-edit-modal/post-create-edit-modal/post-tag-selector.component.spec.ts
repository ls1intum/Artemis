import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { PostTagSelectorComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-tag-selector/post-tag-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { TagInputModule } from 'ngx-chips';
import { FormsModule } from '@angular/forms';
import { metisTags } from '../../../../../helpers/sample/metis-sample-data';

describe('PostTagSelectorComponent', () => {
    let component: PostTagSelectorComponent;
    let fixture: ComponentFixture<PostTagSelectorComponent>;
    let metisService: MetisService;
    let metisServiceGetTagSpy: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(TagInputModule), MockModule(FormsModule)],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [PostTagSelectorComponent, MockPipe(ArtemisTranslatePipe)],
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
        expect(metisServiceGetTagSpy).toHaveBeenCalled();
        expect(component.existingPostTags).toEqual(metisTags);
    }));

    it('should update tags', fakeAsync(() => {
        fixture.detectChanges();
        const onPostTagsChangeSpy = jest.spyOn(component, 'onPostTagsChange');
        const tagInput = fixture.debugElement.query(By.css('tag-input')).nativeElement;
        tagInput.value = 'new tag';
        tagInput.dispatchEvent(new Event('ngModelChange'));
        fixture.detectChanges();
        expect(onPostTagsChangeSpy).toHaveBeenCalled();
    }));
});
