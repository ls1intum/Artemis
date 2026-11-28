import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';

import { AccountService } from 'app/core/auth/account.service';
import { TheiaService } from 'app/programming/shared/services/theia.service';

describe('TheiaService', () => {
    let theiaService: TheiaService;
    let newWindow: { location: { href: string } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useValue: { getToolToken: vi.fn().mockReturnValue(of('test-jwt')) } }],
        });
        theiaService = TestBed.inject(TheiaService);
        newWindow = { location: { href: '' } };
        vi.spyOn(window, 'open').mockReturnValue(newWindow as unknown as Window);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should put the Artemis token and a git URI with credentials into the fragment', async () => {
        const gitUri = 'https://student1:vcs-test-token@artemis.example.com/git/PROG/prog-student1.git';

        await theiaService.startOnlineIDE('https://ide.example.com/', 'java-25', gitUri, 'Student One', 'student1@example.com');

        const url = new URL(newWindow.location.href);
        const query = url.searchParams;
        const fragment = new URLSearchParams(url.hash.slice(1));
        expect(url.origin + url.pathname).toBe('https://ide.example.com/');
        expect(Object.fromEntries(query)).toEqual({ appDef: 'java-25', artemisUrl: window.location.origin, gitUser: 'Student One', gitMail: 'student1@example.com' });
        expect(Object.fromEntries(fragment)).toEqual({ artemisToken: 'test-jwt', gitUri });
        expect(url.search).not.toContain('test-token');
        expect(url.search).not.toContain('test-jwt');
    });

    it('should keep a git URI without credentials in the query', async () => {
        const gitUri = 'https://artemis.example.com/git/PROG/prog-student1.git';

        await theiaService.startOnlineIDE('https://ide.example.com/', 'java-25', gitUri);

        const url = new URL(newWindow.location.href);
        expect(Object.fromEntries(url.searchParams)).toEqual({ appDef: 'java-25', artemisUrl: window.location.origin, gitUri });
        expect(Object.fromEntries(new URLSearchParams(url.hash.slice(1)))).toEqual({ artemisToken: 'test-jwt' });
    });

    it('should do nothing if the popup is blocked', async () => {
        vi.mocked(window.open).mockReturnValue(null);
        const getToolToken = TestBed.inject(AccountService).getToolToken;

        await theiaService.startOnlineIDE('https://ide.example.com/', 'java-25', 'https://artemis.example.com/git/x.git');

        expect(getToolToken).not.toHaveBeenCalled();
    });
});
