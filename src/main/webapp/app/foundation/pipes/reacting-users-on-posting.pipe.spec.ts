import { TestBed } from '@angular/core/testing';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PLACEHOLDER_USER_REACTED, ReactingUsersOnPostingPipe } from 'app/foundation/pipes/reacting-users-on-posting.pipe';
import { TranslateService } from '@ngx-translate/core';
import { communicationTutor, communicationUser1, communicationUser2 } from 'test/helpers/sample/communication-sample-data';
import { MockDirective } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ReactingUsersOnPostingsPipe', () => {
    let reactingUsersPipe: ReactingUsersOnPostingPipe;
    let translateService: TranslateService;
    let updateReactingUsersStringSpy: ReturnType<typeof vi.spyOn>;
    let transformedStringWithReactingUsers: string;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockDirective(MarkdownDirective)],
            providers: [ReactingUsersOnPostingPipe, { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                translateService = TestBed.inject(TranslateService);
                reactingUsersPipe = TestBed.inject(ReactingUsersOnPostingPipe);
                updateReactingUsersStringSpy = vi.spyOn(reactingUsersPipe as any, 'updateReactingUsersString');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should return string for one user that is not "you"', () => {
        const reactingUsers = [communicationUser1.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        expect(transformedStringWithReactingUsers).toBe(communicationUser1.name + 'artemisApp.communication.reactedTooltip');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should return string for one user that is "you"', () => {
        reactingUsersPipe.transform([PLACEHOLDER_USER_REACTED]).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        expect(transformedStringWithReactingUsers).toBe('artemisApp.communication.you');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should return string for two users that do not include "you"', () => {
        const reactingUsers = [communicationUser1.name!, communicationUser2.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        expect(transformedStringWithReactingUsers).toBe(
            communicationUser1.name! + 'artemisApp.communication.and' + communicationUser2.name! + 'artemisApp.communication.reactedTooltip',
        );
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should return string for two users that do include "you"', () => {
        const reactingUsers = [communicationUser1.name!, PLACEHOLDER_USER_REACTED];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        expect(transformedStringWithReactingUsers).toBe(
            'artemisApp.communication.you' + 'artemisApp.communication.and' + communicationUser1.name! + 'artemisApp.communication.reactedTooltip',
        );
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should return string for three users that do include "you" and separate the first two users with comma', () => {
        const reactingUsers = [communicationUser1.name!, PLACEHOLDER_USER_REACTED, communicationTutor.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        expect(transformedStringWithReactingUsers).toBe(
            'artemisApp.communication.you' +
                ', ' +
                communicationUser1.name! +
                'artemisApp.communication.and' +
                communicationTutor.name! +
                'artemisApp.communication.reactedTooltip',
        );
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should trim list of reacting users but always include "you', () => {
        const reactingUsers = [
            communicationUser1.name!,
            communicationUser2.name!,
            communicationTutor.name!,
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
        expect(transformedStringWithReactingUsers).toBe(
            'artemisApp.communication.you' +
                ', ' +
                communicationUser1.name! +
                ', ' +
                communicationUser2.name! +
                ', ' +
                communicationTutor.name! +
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
                'artemisApp.communication.reactedTooltipTrimmed',
        );
        expect(updateReactingUsersStringSpy).toHaveBeenCalledOnce();
    });

    it('should trigger update of reacting users on language change', () => {
        const reactingUsers = [communicationUser1.name!, PLACEHOLDER_USER_REACTED, communicationTutor.name!];
        reactingUsersPipe.transform(reactingUsers).subscribe((transformedReactingUsers: string) => {
            transformedStringWithReactingUsers = transformedReactingUsers;
        });
        translateService.use('de');
        expect(updateReactingUsersStringSpy).toHaveBeenCalledTimes(2);
    });
});
