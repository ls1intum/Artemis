import { TestBed } from '@angular/core/testing';
import { Link, LinkifyService } from 'app/shared/link-preview/services/linkify.service';

describe('LinkifyService', () => {
    let service: LinkifyService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LinkifyService],
        });
        service = TestBed.inject(LinkifyService);
    });

    it('find should return array of links in the given text', () => {
        const text = 'Check out this link: https://example.com';
        const expectedLinks: Link[] = [
            {
                type: 'url',
                value: 'https://example.com',
                href: 'https://example.com',
                isLink: true,
                end: 40,
                start: 21,
                isLinkPreviewRemoved: false,
            },
        ];

        const links = service.find(text);
        expect(links).toEqual(expectedLinks);
    });

    it('should mark isLinkPreviewRemoved to true when links are wrapped with <>', () => {
        const text = 'Check out this link: <https://example.com>';
        const expectedLinks: Link[] = []; // should be empty because link preview is removed

        const links = service.find(text);
        expect(links).toEqual(expectedLinks);
    });
});
