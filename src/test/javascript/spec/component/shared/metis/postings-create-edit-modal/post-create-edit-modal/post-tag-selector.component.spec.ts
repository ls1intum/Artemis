import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { PostTagSelectorComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-tag-selector/post-tag-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { metisTags } from '../../../../../helpers/sample/metis-sample-data';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatSelectModule } from '@angular/material/select';

describe('PostTagSelectorComponent', () => {
    let component: PostTagSelectorComponent;
    let fixture: ComponentFixture<PostTagSelectorComponent>;
    let metisService: MetisService;
    let metisServiceGetTagSpy: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(MatChipsModule), MockModule(MatIconModule), MockModule(MatAutocompleteModule), MockModule(MatSelectModule), MockModule(FormsModule)],
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
        expect(metisServiceGetTagSpy).toHaveBeenCalledOnce();
        component.existingPostTags.subscribe((tags) => {
            expect(tags).toEqual(metisTags);
        });
    }));

    // TODO: implement a test which removes a category and one which adds a category
});
