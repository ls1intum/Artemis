import { Component, computed, input } from '@angular/core';
import dayjs from 'dayjs/esm';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBuildingColumns, faCalendar, faCalendarDay, faClock, faFlag, faMapPin, faRotateRight, faTag, faUsers } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

// TODO: translate info-labels in template

@Component({
    selector: 'jhi-new-tutorial-group-detail',
    imports: [ProfilePictureComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './new-tutorial-group-detail.component.html',
    styleUrl: './new-tutorial-group-detail.component.scss',
})
export class NewTutorialGroupDetailComponent {
    private nextSession = computed<TutorialGroupSession | undefined>(() => {
        const sessions = this.tutorialGroup().tutorialGroupSessions;
        if (sessions && sessions.length > 0) {
            const now = dayjs();
            const upcoming = sessions
                .filter((session) => session.start !== undefined && dayjs(session.start).isAfter(now))
                .sort((first, second) => dayjs(first.start!).diff(dayjs(second.start!)));
            return upcoming.length > 0 ? upcoming[0] : undefined;
        }
        return undefined;
    });

    course = input.required<Course>();
    tutorialGroup = input.required<TutorialGroup>();
    teachingAssistantImageUrl = computed(() => addPublicFilePrefix(this.tutorialGroup().teachingAssistantImageUrl));

    readonly faRotateRight = faRotateRight;
    readonly faFlag = faFlag;
    readonly faUsers = faUsers;
    readonly faTag = faTag;
    readonly faCalendarDay = faCalendarDay;
    readonly faCalendar = faCalendar;
    readonly faClock = faClock;
    readonly faMapPin = faMapPin;
    readonly faBuildingColumns = faBuildingColumns;

    constructor() {}

    tutorialGroupLanguage = computed<string>(() => {
        const language = this.tutorialGroup().language;
        if (language) {
            if (language == 'German') {
                return 'artemisApp.generic.german';
            }
            if (language == 'English') {
                return 'artemisApp.generic.english';
            }
        }
        return '-';
    });

    tutorialGroupCapacity = computed<string>(() => String(this.tutorialGroup().capacity ?? '-'));

    tutorialGroupMode = computed<string>(() => {
        const isOnline = this.tutorialGroup().isOnline;
        if (isOnline !== undefined) {
            if (isOnline) {
                return 'artemisApp.generic.online';
            } else {
                return 'artemisApp.generic.offline';
            }
        }
        return '-';
    });

    tutorialGroupCampus = computed<string>(() => {
        const isOnline = this.tutorialGroup().isOnline;
        const campus = this.tutorialGroup()?.campus;
        return !isOnline && campus ? campus : '-';
    });

    nextSessionWeekday = computed<string>(() => {
        const weekDayIndex = this.nextSession()?.start?.day();
        if (weekDayIndex && weekDayIndex >= 1 && weekDayIndex <= 7) {
            const keys = [
                'global.weekdays.monday',
                'global.weekdays.tuesday',
                'global.weekdays.wednesday',
                'global.weekdays.thursday',
                'global.weekdays.friday',
                'global.weekdays.saturday',
                'global.weekdays.sunday',
            ];
            return keys[weekDayIndex - 1];
        }
        return '-';
    });

    nextSessionDate = computed<string>(() => {
        const date = this.nextSession()?.start;
        if (date) {
            return date.format('DD.MM.YYYY');
        }
        return '-';
    });

    nextSessionTime = computed<string>(() => {
        const nextSession = this.nextSession();
        const start = nextSession?.start;
        const end = nextSession?.end;
        if (start && end) {
            return start.format('HH:mm') + '-' + end.format('HH:mm');
        }
        return '-';
    });

    nextSessionLocation = computed<string>(() => this.nextSession()?.location ?? '-');
}
