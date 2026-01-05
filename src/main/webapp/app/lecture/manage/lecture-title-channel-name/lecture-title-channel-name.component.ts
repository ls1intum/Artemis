import { Component, OnInit, input, output, viewChild } from '@angular/core';
import { isCommunicationEnabled } from 'app/core/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';

@Component({
    selector: 'jhi-lecture-title-channel-name',
    templateUrl: './lecture-title-channel-name.component.html',
    imports: [TitleChannelNameComponent],
})
export class LectureTitleChannelNameComponent implements OnInit {
    lecture = input.required<Lecture>();

    lectureChange = output<Lecture>();

    titleChannelNameComponent = viewChild.required(TitleChannelNameComponent);

    hideChannelNameInput = false;

    ngOnInit() {
        this.hideChannelNameInput = !this.requiresChannelName(this.lecture());
    }

    onTitleChange(newTitle: string | undefined): void {
        const updatedLecture = { ...this.lecture(), title: newTitle };
        this.lectureChange.emit(updatedLecture);
    }

    onChannelNameChange(newChannelName: string | undefined): void {
        const updatedLecture = { ...this.lecture(), channelName: newChannelName };
        this.lectureChange.emit(updatedLecture);
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
