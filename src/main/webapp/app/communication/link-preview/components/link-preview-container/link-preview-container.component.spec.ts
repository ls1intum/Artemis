import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockLinkPreviewService } from 'test/helpers/mocks/service/mock-link-preview.service';
import { of } from 'rxjs';
import { LinkPreviewContainerComponent } from 'app/communication/link-preview/components/link-preview-container/link-preview-container.component';
import { LinkPreview, LinkPreviewService } from 'app/communication/link-preview/services/link-preview.service';
import { Link, LinkifyService } from 'app/communication/link-preview/services/linkify.service';

describe('LinkPreviewContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: LinkPreviewContainerComponent;
    let fixture: ComponentFixture<LinkPreviewContainerComponent>;

    let linkPreviewService: LinkPreviewService;
    let linkifyService: LinkifyService;

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: LinkPreviewService, useClass: MockLinkPreviewService },
                { provide: LinkifyService, useClass: LinkifyService },
            ],
        });
        fixture = TestBed.createComponent(LinkPreviewContainerComponent);
        component = fixture.componentInstance;

        linkPreviewService = TestBed.inject(LinkPreviewService);
        linkifyService = TestBed.inject(LinkifyService);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should fetch link previews and update linkPreviews array', () => {
        fixture.componentRef.setInput('data', 'Check out these links: https://example.com/link1 and https://example.com/link2');
        const links: Link[] = [
            { type: '', value: '', href: 'https://example.com/link1' },
            { type: '', value: '', href: 'https://example.com/link2' },
        ];
        const mockLinkPreviews: LinkPreview[] = [
            { url: 'https://example.com/link1', title: 'Link 1', description: 'Description 1', image: 'image1.jpg', shouldPreviewBeShown: true },
            { url: 'https://example.com/link2', title: 'Link 2', description: 'Description 2', image: 'image2.jpg', shouldPreviewBeShown: true },
        ];

        const linkifyServiceSpy = vi.spyOn(linkifyService, 'find').mockReturnValue(links);
        const linkPreviewServiceSpy = vi.spyOn(linkPreviewService, 'fetchLink').mockReturnValueOnce(of(mockLinkPreviews[0])).mockReturnValueOnce(of(mockLinkPreviews[1]));

        component.ngOnInit();

        expect(linkifyServiceSpy).toHaveBeenCalledWith(component.data());
        expect(linkPreviewServiceSpy).toHaveBeenCalledTimes(2);
        expect(linkPreviewServiceSpy).toHaveBeenCalledWith('https://example.com/link1');
        expect(linkPreviewServiceSpy).toHaveBeenCalledWith('https://example.com/link2');
        expect(component.linkPreviews()).toEqual(mockLinkPreviews);
        expect(component.hasError()).toBe(false);
        expect(component.loaded()).toBe(true);
        expect(component.showLoadingsProgress()).toBe(false);
    });

    it('should update existing link preview if it already exists', () => {
        fixture.componentRef.setInput('data', 'Check out these links: https://example.com/link1');
        const links: Link[] = [{ type: '', value: '', href: 'https://example.com/link1' }];
        const existingLinkPreview: LinkPreview = {
            url: 'https://example.com/link1',
            title: 'Existing Link',
            description: 'Existing Description',
            image: 'existing.jpg',
            shouldPreviewBeShown: true,
        };
        const newLinkPreview: LinkPreview = { url: 'https://example.com/link1', title: 'New Link', description: 'New Description', image: 'new.jpg', shouldPreviewBeShown: true };

        const linkifyServiceSpy = vi.spyOn(linkifyService, 'find').mockReturnValue(links);
        const linkPreviewServiceSpy = vi.spyOn(linkPreviewService, 'fetchLink').mockReturnValueOnce(of(newLinkPreview));

        component.linkPreviews().push(existingLinkPreview);

        component.ngOnInit();

        expect(linkifyServiceSpy).toHaveBeenCalledWith(component.data());
        expect(linkPreviewServiceSpy).toHaveBeenCalledOnce();
        expect(linkPreviewServiceSpy).toHaveBeenCalledWith('https://example.com/link1');
        expect(component.linkPreviews()).toHaveLength(1);
        expect(component.linkPreviews()[0]).toEqual(newLinkPreview);
    });
});
