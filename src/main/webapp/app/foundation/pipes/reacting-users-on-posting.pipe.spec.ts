import { HtmlForPostingMarkdownPipe } from 'app/foundation/pipes/html-for-posting-markdown.pipe';
import { TestBed } from '@angular/core/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PLACEHOLDER_USER_REACTED, ReactingUsersOnPostingPipe } from 'app/foundation/pipes/reacting-users-on-posting.pipe';
import { TranslateService } from '@ngx-translate/core';
import { metisTutor, metisUser1, metisUser2 } from 'test/helpers/sample/metis-sample-data';
import { MockPipe } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ReactingUsersOnPostingsPipe', () => {
    setupTestBed({ zoneless: true });

    let reactingUsersPipe: ReactingUsersOnPostingPipe;
    let translateService: TranslateService;
    let updateReactingUsersStringSpy: ReturnType<typeof vi.spyOn>;
    let transformedStringWithReactingUsers: string;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockPipe(HtmlForPostingMarkdownPipe)],
            providers: [ReactingUsersOnPostingPipe, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        translateService = TestBed.inject(TranslateService);
        reactingUsersPipe = TestBed.inject(ReactingUsersOnPostingPipe);
        updateReactingUsersStringSpy = vi.spyOn(reactingUsersPipe as any, 'updateReactingUsersString');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should return string for one user that is not "you"', async () => {
        const reactingUsers = [metisUser1.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        await Promise.resolve();
        expect(transformedStringWithReactingUsers).toBe(metisUser1.name + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should return string for one user that is "you"', async () => {
        reactingUsersPipe.transform([PLACEHOLDER_USER_REACTED]).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        await Promise.resolve();
        expect(transformedStringWithReactingUsers).toBe('artemisApp.metis.you');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should return string for two users that do not include "you"', async () => {
        const reactingUsers = [metisUser1.name!, metisUser2.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        await Promise.resolve();
        expect(transformedStringWithReactingUsers).toBe(metisUser1.name! + 'artemisApp.metis.and' + metisUser2.name! + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should return string for two users that do include "you"', async () => {
        const reactingUsers = [metisUser1.name!, PLACEHOLDER_USER_REACTED];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        await Promise.resolve();
        expect(transformedStringWithReactingUsers).toBe('artemisApp.metis.you' + 'artemisApp.metis.and' + metisUser1.name! + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should return string for three users that do include "you" and separate the first two users with comma', async () => {
        const reactingUsers = [metisUser1.name!, PLACEHOLDER_USER_REACTED, metisTutor.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        await Promise.resolve();
        expect(transformedStringWithReactingUsers).toBe(
            'artemisApp.metis.you' + ', ' + metisUser1.name! + 'artemisApp.metis.and' + metisTutor.name! + 'artemisApp.metis.reactedTooltip',
        );
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should trim list of reacting users but always include "you', async () => {
        const reactingUsers = [
            metisUser1.name!,
            metisUser2.name!,
            metisTutor.name!,
            'userA',
            'userB',
            'userC',
            'userD',
            'userE',
            'userF',
            'userG',
            'userH',
            PLACEHOLDER_USER_REACTED,
        ];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        await Promise.resolve();
        expect(transformedStringWithReactingUsers).toBe(
            'artemisApp.metis.you' +
                ', ' +
                metisUser1.name! +
                ', ' +
                metisUser2.name! +
                ', ' +
                metisTutor.name! +
                ', ' +
                'userA' +
                ', ' +
                'userB' +
                ', ' +
                'userC' +
                ', ' +
                'userD' +
                ', ' +
                'userE' +
                ', ' +
                'userF' +
                'artemisApp.metis.reactedTooltipTrimmed',
        );
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should trigger update of reacting users on language change', async () => {
        const reactingUsers = [metisUser1.name!, PLACEHOLDER_USER_REACTED, metisTutor.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        await Promise.resolve();
        translateService.use('de');
        await Promise.resolve();
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(2);
    });
});
