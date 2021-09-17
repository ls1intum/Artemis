import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import * as sinon from 'sinon';
import { spy } from 'sinon';
import { PostTagSelectorComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-tag-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { TagInputModule } from 'ngx-chips';
import { FormsModule } from '@angular/forms';
import { metisTags } from '../../../../../helpers/sample/metis-sample-data';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostTagSelectorComponent', () => {
    let component: PostTagSelectorComponent;
    let fixture: ComponentFixture<PostTagSelectorComponent>;
    let metisService: MetisService;
    let metisServiceSpy: PropertyDescriptor;

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
                metisServiceSpy = spy(metisService, 'tags', ['get']);
                component.postTags = [];
                component.ngOnInit();
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should be initialized with empty list of tags', () => {
        expect(component.tags).to.be.deep.equal([]);
    });

    it('should be initialized with existing list of tags', fakeAsync(() => {
        tick();
        expect(metisServiceSpy.get).to.have.been.called;
        expect(component.existingPostTags).to.be.deep.equal(metisTags);
    }));

    it('should update tags', fakeAsync(() => {
        fixture.detectChanges();
        const onPostTagsChangeSpy = spy(component, 'onPostTagsChange');
        const tagInput = fixture.debugElement.query(By.css('tag-input')).nativeElement;
        tagInput.value = 'new tag';
        tagInput.dispatchEvent(new Event('ngModelChange'));
        fixture.detectChanges();
        expect(onPostTagsChangeSpy).to.have.been.called;
    }));
});
