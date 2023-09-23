import { TestBed } from '@angular/core/testing';
import { LinkifyService } from 'app/shared/link-preview/services/linkify.service';

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
        const expectedLinks = [
            {
                type: 'url',
                value: 'https://example.com',
                href: 'https://example.com',
                isLink: true,
                end: 40,
                start: 21,
            },
        ];

        const links = service.find(text);
        expect(links).toEqual(expectedLinks);
    });
});
