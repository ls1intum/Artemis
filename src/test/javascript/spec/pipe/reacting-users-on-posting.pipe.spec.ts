import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { PLACEHOLDER_USER_REACTED, ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import { TranslateService } from '@ngx-translate/core';
import { metisTutor, metisUser1, metisUser2 } from '../helpers/sample/metis-sample-data';

describe('ReactingUsersOnPostingsPipe', () => {
    let reactingUsersPipe: ReactingUsersOnPostingPipe;
    let translateService: TranslateService;
    let updateReactingUsersStringSpy: jest.SpyInstance;
    let transformedStringWithReactingUsers: string;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [HtmlForPostingMarkdownPipe],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                translateService = TestBed.inject(TranslateService);
                reactingUsersPipe = new ReactingUsersOnPostingPipe(translateService);
                updateReactingUsersStringSpy = jest.spyOn(reactingUsersPipe, 'updateReactingUsersString');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should return string for one user that is not "you"', fakeAsync(() => {
        const reactingUsers = [metisUser1.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        tick();
        expect(transformedStringWithReactingUsers).toBe(metisUser1.name + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    }));

    it('should return string for one user that is "you"', fakeAsync(() => {
        reactingUsersPipe.transform([PLACEHOLDER_USER_REACTED]).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        tick();
        expect(transformedStringWithReactingUsers).toBe('artemisApp.metis.you');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    }));

    it('should return string for two users that do not include "you"', fakeAsync(() => {
        const reactingUsers = [metisUser1.name!, metisUser2.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        tick();
        expect(transformedStringWithReactingUsers).toBe(metisUser1.name! + 'artemisApp.metis.and' + metisUser2.name! + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    }));

    it('should return string for two users that do include "you"', fakeAsync(() => {
        const reactingUsers = [metisUser1.name!, PLACEHOLDER_USER_REACTED];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        tick();
        expect(transformedStringWithReactingUsers).toBe('artemisApp.metis.you' + 'artemisApp.metis.and' + metisUser1.name! + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    }));

    it('should return string for three users that do include "you" and separate the first two users with comma', fakeAsync(() => {
        const reactingUsers = [metisUser1.name!, PLACEHOLDER_USER_REACTED, metisTutor.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        tick();
        expect(transformedStringWithReactingUsers).toBe(
            'artemisApp.metis.you' + ', ' + metisUser1.name! + 'artemisApp.metis.and' + metisTutor.name! + 'artemisApp.metis.reactedTooltip',
        );
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    }));

    it('should trim list of reacting users but always include "you', fakeAsync(() => {
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
        tick();
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
    }));

    it('should trigger update of reacting users on language change', fakeAsync(() => {
        const reactingUsers = [metisUser1.name!, PLACEHOLDER_USER_REACTED, metisTutor.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        tick();
        translateService.use('de');
        tick();
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(2);
    }));
});
