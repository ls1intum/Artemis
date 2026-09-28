import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, output, signal } from '@angular/core';
import { TumAetUiFormFieldComponent, TumAetUiInputDirective } from '@tumaet/ui-angular';
import { isCommunicationEnabled } from 'app/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

const CHANNEL_NAME_PREFIX = 'lecture-';
const CHANNEL_NAME_MAX_LENGTH = 30;

/**
 * Formats a channel name the way the server accepts it: lowercase letters, digits and hyphens, at most 30 characters.
 *
 * @param name the name to format
 * @param allowDuplicateHyphens whether a run of special characters may turn into several hyphens, which keeps typing in the field predictable
 * @param removeTrailingHyphen whether to drop a trailing hyphen, so a name derived from a title does not end in one
 */
export function formatLectureChannelName(name: string, allowDuplicateHyphens = true, removeTrailingHyphen = false): string {
    const specialCharacters = allowDuplicateHyphens ? /[^a-z0-9-]+/g : /[^a-z0-9]+/g;
    const withoutSpecialCharacters = name.toLowerCase().replaceAll(specialCharacters, '-');
    const formattedName = removeTrailingHyphen ? withoutSpecialCharacters.replace(/-$/, '') : withoutSpecialCharacters;
    return formattedName.slice(0, CHANNEL_NAME_MAX_LENGTH);
}

@Component({
    selector: 'jhi-lecture-title-channel-name',
    templateUrl: './lecture-title-channel-name.component.html',
    imports: [TumAetUiFormFieldComponent, TumAetUiInputDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LectureTitleChannelNameComponent {
    readonly lecture = input.required<Lecture>();

    readonly lectureChange = output<Lecture>();

    protected readonly channelNameMaxLength = CHANNEL_NAME_MAX_LENGTH;

    readonly hideChannelNameInput = computed(() => !this.requiresChannelName(this.lecture()));

    readonly title = linkedSignal(() => this.lecture()?.title);
    /**
     * A new lecture shows the channel name derived from its title right away. It reaches the lecture with the first edit
     * of either field, so an untouched creation form counts as unchanged.
     */
    readonly channelName = linkedSignal(() => {
        const lecture = this.lecture();
        return lecture?.id === undefined && lecture?.channelName === undefined ? this.channelNameFromTitle(lecture?.title) : lecture?.channelName;
    });

    /** Errors show only once the instructor has been in a field, so an empty form does not open with red fields. */
    protected readonly titleTouched = signal(false);
    protected readonly channelNameTouched = signal(false);

    protected readonly isTitleValid = computed(() => !!this.title()?.trim());
    protected readonly isChannelNameValid = computed(() => !!this.channelName());

    readonly isValid = computed(() => this.isTitleValid() && (this.hideChannelNameInput() || this.isChannelNameValid()));

    onTitleInput(newTitle: string): void {
        this.title.set(newTitle);
        this.channelName.set(this.channelNameFromTitle(newTitle));
        this.emitChange();
    }

    onChannelNameInput(field: HTMLInputElement): void {
        const formattedName = formatLectureChannelName(field.value);
        // Write the formatted name back even when it equals the current value, e.g. a character cut off at the length limit.
        field.value = formattedName;
        this.channelName.set(formattedName);
        this.emitChange();
    }

    private channelNameFromTitle(title: string | undefined): string {
        return formatLectureChannelName(CHANNEL_NAME_PREFIX + (title ?? ''), false, !!title);
    }

    private emitChange(): void {
        const changedLecture = deepClone(this.lecture());
        changedLecture.title = this.title();
        changedLecture.channelName = this.channelName();
        this.lectureChange.emit(changedLecture);
    }

    /**
     * Determines whether the provided lecture should have a channel name. This is not the case, if messaging in the course is disabled.
     * If messaging is enabled, a channel name should exist for newly created and imported lectures.
     *
     * @param lecture the lecture under consideration
     * @return boolean true if the channel name is required, else false
     */
    private requiresChannelName(lecture: Lecture): boolean {
        // not required if messaging and communication is disabled
        if (!isCommunicationEnabled(lecture?.course)) {
            return false;
        }

        // required on create (messaging is enabled)
        if (lecture?.id === undefined) {
            return true;
        }

        // when editing, it is required if the lecture has a channel
        return lecture?.channelName !== undefined;
    }
}
