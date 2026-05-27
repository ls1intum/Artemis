/**
 * Vitest tests for ParseLinks service.
 * Tests the parsing of HTTP Link header values for pagination.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ParseLinks } from 'app/admin/system-notification-management/parse-links.service';

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

    it('should throw an error when passed single link without semicolon', () => {
        expect(() => {
            service.parse('test');
        }).toThrow(new Error('section could not be split on ";"'));
    });

    it('should throw an error when passed without semicolon', () => {
        expect(() => {
            service.parse('<test>;a,<test2>');
        }).toThrow(new Error('section could not be split on ";"'));
    });

    it('should throw an error when passed multiple semicolon per link', () => {
        expect(() => {
            service.parse('<test>;a;a,<test2>;a');
        }).toThrow(new Error('section could not be split on ";"'));
    });

    it('should return links when headers are passed', () => {
        const expectedLinks = { last: 0, first: 1 };
        const parsedLinks = service.parse(' </api/audits?page=0&size=20>; rel="last",</api/audits?page=1&size=20>; rel="first"');
        expect(parsedLinks).toEqual(expectedLinks);
    });
    it('should return links when headers are passed even if link contains ,', () => {
        const expectedLinks = { last: 0, first: 1 };
        const parsedLinks = service.parse(
            '<https://localhost/api/communication/system-notifications' +
                '?sort=notificationDate,desc&sort=id&page=0&size=50>; rel="last",' +
                '<https://localhost/api/communication/system-notifications' +
                '?sort=notificationDate,desc&sort=id&page=1&size=50>; rel="first"',
        );
        expect(parsedLinks).toEqual(expectedLinks);
    });
    // Precaution for cases where matrix parameters are used
    it('should return links when headers are passed even if link contains ;', () => {
        const expectedLinks = { last: 0, first: 1 };
        const parsedLinks = service.parse(
            '<https://localhost/api/communication' +
                ';a=b/system-notifications?sort=notificationDate,desc&sort=id&page=0&size=50>; rel="last",' +
                '<https://localhost/api/communication/system-notifications' +
                '?sort=notificationDate,desc&sort=id&page=1&size=50>; rel="first"',
        );
        expect(parsedLinks).toEqual(expectedLinks);
    });
});
