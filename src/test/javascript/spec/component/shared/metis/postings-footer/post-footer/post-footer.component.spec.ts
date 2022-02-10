import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PostFooterComponent } from 'app/shared/metis/posting-footer/post-footer/post-footer.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { getElements } from '../../../../../helpers/utils/general.utils';
import { metisPostExerciseUser1, metisPostLectureUser1, metisPostLectureUser2, metisTags } from '../../../../../helpers/sample/metis-sample-data';

describe('PostFooterComponent', () => {
    let component: PostFooterComponent;
    let fixture: ComponentFixture<PostFooterComponent>;
    const updatedTags = ['tag1', 'tag2', 'tag3'];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(ArtemisCoursesRoutingModule)],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [PostFooterComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(PostReactionsBarComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostFooterComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize post tags correctly', () => {
        component.posting = metisPostLectureUser2;
        component.posting.tags = metisTags;
        component.ngOnInit();
        expect(component.tags).toEqual(metisTags);
    });

    it('should initialize post without tags correctly', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        expect(component.tags).toEqual([]);
    });

    it('should update post tags correctly', () => {
        component.posting = metisPostLectureUser1;
        component.ngOnInit();
        component.posting.tags = updatedTags;
        component.ngOnChanges();
        expect(component.tags).toEqual(updatedTags);
    });

    it('should have a tag shown for each post tag', () => {
        component.posting = metisPostLectureUser1;
        component.posting.tags = metisTags;
        component.ngOnInit();
        fixture.detectChanges();
        const tags = getElements(fixture.debugElement, '.post-tag');
        expect(tags).toHaveLength(metisTags.length);
    });
});
