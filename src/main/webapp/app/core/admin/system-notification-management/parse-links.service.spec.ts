/**
 * Vitest tests for ParseLinks service.
 * Tests the parsing of HTTP Link header values for pagination.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ParseLinks } from 'app/core/admin/system-notification-management/parse-links.service';

describe('ParseLinks Service', () => {
    setupTestBed({ zoneless: true });

    let service: ParseLinks;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [ParseLinks],
        }).compileComponents();

        service = TestBed.inject(ParseLinks);
    });

    it('should throw an error when passed an empty string', () => {
        expect(() => {
            service.parse('');
        }).toThrow(new Error('input must not be of zero length'));
    });

    it('should throw an error when passed without comma', () => {
        expect(() => {
            service.parse('test');
        }).toThrow(new Error('section could not be split on ";"'));
    });

    it('should throw an error when passed without semicolon', () => {
        expect(() => {
            service.parse('test,test2');
        }).toThrow(new Error('section could not be split on ";"'));
    });

    it('should return links when headers are passed', () => {
        const expectedLinks = { last: 0, first: 0 };
        const parsedLinks = service.parse(' </api/audits?page=0&size=20>; rel="last",</api/audits?page=0&size=20>; rel="first"');
        expect(parsedLinks).toEqual(expectedLinks);
    });
});
