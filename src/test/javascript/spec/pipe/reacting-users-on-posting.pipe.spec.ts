import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { PLACEHOLDER_USER_REACTED, ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { metisTutor, metisUser1, metisUser2 } from '../helpers/sample/metis-sample-data';

describe('ReactingUsersOnPostingsPipe', () => {
    let reactingUsersPipe: ReactingUsersOnPostingPipe;
    let translateService: TranslateService;
    let updateReactingUsersStringSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [HtmlForPostingMarkdownPipe],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        translateService = TestBed.inject(TranslateService);
        reactingUsersPipe = new ReactingUsersOnPostingPipe(translateService);
        updateReactingUsersStringSpy = jest.spyOn(reactingUsersPipe, 'updateReactingUsersString');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should return string for one user that is not "you"', () => {
        const reactingUsers = [metisUser1.name!];
        const transformedStringWithReactingUsers = reactingUsersPipe.transform(reactingUsers);
        expect(transformedStringWithReactingUsers).toBe(metisUser1.name + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(1);
    });

    it('Should return string for one user that is "you"', () => {
        const transformedStringWithReactingUsers = reactingUsersPipe.transform([PLACEHOLDER_USER_REACTED]);
        expect(transformedStringWithReactingUsers).toBe('artemisApp.metis.you');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(1);
    });

    it('Should return string for two users that do not include "you"', () => {
        const reactingUsers = [metisUser1.name!, metisUser2.name!];
        const transformedStringWithReactingUsers = reactingUsersPipe.transform(reactingUsers);
        expect(transformedStringWithReactingUsers).toBe(metisUser1.name! + 'artemisApp.metis.and' + metisUser2.name! + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(1);
    });

    it('Should return string for two users that do include "you"', () => {
        const reactingUsers = [metisUser1.name!, PLACEHOLDER_USER_REACTED];
        const transformedStringWithReactingUsers = reactingUsersPipe.transform(reactingUsers);
        expect(transformedStringWithReactingUsers).toBe('artemisApp.metis.you' + 'artemisApp.metis.and' + metisUser1.name! + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(1);
    });

    it('Should return string for three users that do include "you" and separate the first two users with comma', () => {
        const reactingUsers = [metisUser1.name!, metisUser2.name!, metisTutor.name!];
        const transformedStringWithReactingUsers = reactingUsersPipe.transform(reactingUsers);
        expect(transformedStringWithReactingUsers).toBe(metisUser1.name! + ', ' + metisUser2.name! + 'artemisApp.metis.and' + metisTutor.name! + 'artemisApp.metis.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(1);
    });

    it('Should return string for three users that do include "you" and separate the first two users with comma', () => {
        const reactingUsers = [metisUser1.name!, PLACEHOLDER_USER_REACTED, metisTutor.name!];
        const transformedStringWithReactingUsers = reactingUsersPipe.transform(reactingUsers);
        expect(transformedStringWithReactingUsers).toBe(
            'artemisApp.metis.you' + ', ' + metisUser1.name! + 'artemisApp.metis.and' + metisTutor.name! + 'artemisApp.metis.reactedTooltip',
        );
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(1);
    });

    it('Should trim list of reacting users but always include "you', () => {
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
        const transformedStringWithReactingUsers = reactingUsersPipe.transform(reactingUsers);
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
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(1);
    });
});
