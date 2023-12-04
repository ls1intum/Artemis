import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LinkPreviewComponent } from 'app/shared/link-preview/components/link-preview/link-preview.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockMetisService } from '../../helpers/mocks/service/mock-metis-service.service';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

describe('LinkPreviewComponent', () => {
    let component: LinkPreviewComponent;
    let fixture: ComponentFixture<LinkPreviewComponent>;
    let metisService: MetisService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [LinkPreviewComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ConfirmIconComponent)],
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ], // Add any required dependencies here
        }).compileComponents();

        fixture = TestBed.createComponent(LinkPreviewComponent);
        metisService = TestBed.inject(MetisService);
        component = fixture.componentInstance;
        component.posting = new Post(); // Set up a dummy Posting object if required
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render link preview when linkPreview is provided', () => {
        component.linkPreview = {
            title: 'Test Title',
            description: 'Test Description',
            image: 'test-image.jpg',
            url: 'https://example.com',
            shouldPreviewBeShown: true,
        };
        fixture.detectChanges();

        const previewContainer = fixture.nativeElement.querySelector('.preview-container');
        const previewCard = fixture.nativeElement.querySelector('.preview-card');
        const previewTitle = fixture.nativeElement.querySelector('.preview-title');
        const previewDescription = fixture.nativeElement.querySelector('.preview-description');
        const previewImage = fixture.nativeElement.querySelector('.preview-image img');

        expect(previewContainer).toBeTruthy();
        expect(previewCard).toBeTruthy();
        expect(previewTitle.textContent).toBe('Test Title');
        expect(previewDescription.textContent).toBe('Test Description');
        expect(previewImage.src).toContain('test-image.jpg');
    });

    it('should render link preview without image when multiple links are provided', () => {
        component.linkPreview = {
            title: 'Test Title',
            description: 'Test Description',
            image: 'test-image.jpg',
            url: 'https://example.com',
            shouldPreviewBeShown: true,
        };
        component.multiple = true; // this indicates that there are multiple links in the message
        fixture.detectChanges();

        const previewContainer = fixture.nativeElement.querySelector('.preview-container');
        const previewImage = fixture.nativeElement.querySelector('.preview-image img');

        expect(previewContainer).toBeTruthy();
        expect(previewImage).toBeFalsy();
    });

    it('should not render link preview when linkPreview is not provided', () => {
        const previewContainer = fixture.nativeElement.querySelector('.preview-container');

        expect(previewContainer).toBeFalsy();
    });

    it('should render loading spinner when linkPreview is not loaded and showLoadingsProgress is true', () => {
        component.showLoadingsProgress = true;
        fixture.detectChanges();

        const loadingContainer = fixture.nativeElement.querySelector('.loading-container');
        const loadingSpinner = fixture.nativeElement.querySelector('.loading-spinner');

        expect(loadingContainer).toBeTruthy();
        expect(loadingSpinner).toBeTruthy();
    });

    it('should not render loading spinner when linkPreview is loaded', () => {
        component.loaded = true;
        fixture.detectChanges();

        const loadingContainer = fixture.nativeElement.querySelector('.loading-container');

        expect(loadingContainer).toBeFalsy();
    });

    it('should not render loading spinner when showLoadingsProgress is false', () => {
        component.showLoadingsProgress = false;
        fixture.detectChanges();

        const loadingContainer = fixture.nativeElement.querySelector('.loading-container');

        expect(loadingContainer).toBeFalsy();
    });

    it('should not render error message when hasError is false', () => {
        component.hasError = false;
        fixture.detectChanges();

        const errorContainer = fixture.nativeElement.querySelector('.error-container');

        expect(errorContainer).toBeFalsy();
    });

    it('should initialize isAuthorOfOriginalPost', () => {
        // Modify the metisService to return a desired value for metisUserIsAuthorOfPosting
        const metisServiceSpy = jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(true);

        component.ngOnInit();

        expect(component.isAuthorOfOriginalPost).toBeTrue();
        expect(metisServiceSpy).toHaveBeenCalled();
    });

    it('should remove link preview from message', () => {
        const linkPreview: any = {
            url: 'https://example.com',
        };

        component.isReply = false;
        component.posting = new Post();
        component.posting.content = 'This is a sample post with a link: https://example.com';

        const metisServiceSpy = jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(true);
        const metisServiceUpdatePostSpy = jest.spyOn(metisService, 'updatePost');

        component.ngOnInit();
        component.removeLinkPreview(linkPreview);

        expect(metisServiceSpy).toHaveBeenCalled();
        expect(metisServiceUpdatePostSpy).toHaveBeenCalled();
        expect(component.posting.content).toContain('<https://example.com>');
    });

    it('should remove link preview from reply', () => {
        const linkPreview: any = {
            url: 'https://example.com',
        };

        component.isReply = true;
        component.posting = new AnswerPost();
        component.posting.content = 'This is a sample answer post with a link: https://example.com';

        const metisServiceSpy = jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(true);
        const metisServiceUpdateAnswerPostSpy = jest.spyOn(metisService, 'updateAnswerPost');

        component.ngOnInit();
        component.removeLinkPreview(linkPreview);

        expect(metisServiceSpy).toHaveBeenCalled();
        expect(metisServiceUpdateAnswerPostSpy).toHaveBeenCalled();
        expect(component.posting.content).toContain('<https://example.com>');
    });
});
