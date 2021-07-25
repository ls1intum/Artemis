import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import * as sinon from 'sinon';
import { spy } from 'sinon';
import { PostTagSelectorComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-tag-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostTagSelectorComponent', () => {
    let component: PostTagSelectorComponent;
    let fixture: ComponentFixture<PostTagSelectorComponent>;
    let metisService: MetisService;
    let metisServiceSpy: PropertyDescriptor;
    const existingTags = ['tag1', 'tag2'];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [PostTagSelectorComponent, MockPipe(ArtemisTranslatePipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostTagSelectorComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceSpy = spy(metisService, 'tags', ['get']);
                component.postTags = [];
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should init with empty list of tags', () => {
        component.ngOnInit();
        expect(component.tags).to.be.deep.equal([]);
    });

    it('should init with existing list of tags', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(metisServiceSpy.get).to.have.been.called;
        expect(component.existingPostTags).to.be.deep.equal(existingTags);
    }));
});
