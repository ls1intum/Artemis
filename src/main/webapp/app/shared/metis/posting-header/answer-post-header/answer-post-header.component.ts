import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingHeaderDirective } from 'app/shared/metis/posting-header/posting-header.directive';
import { faCheck, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { EmojiPickerComponent } from 'app/shared/metis/emoji/emoji-picker.component';

@Component({
    selector: 'jhi-answer-post-header',
    templateUrl: './answer-post-header.component.html',
    styleUrls: ['../../metis.component.scss'],
    standalone: true,
    imports: [ProfilePictureComponent, ArtemisSharedModule, EmojiPickerComponent],
})
export class AnswerPostHeaderComponent extends PostingHeaderDirective<AnswerPost> implements OnInit, OnChanges {
    @Input()
    isReadOnlyMode = false;

    @Input() lastReadDate?: dayjs.Dayjs;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();

    // Icons
    readonly faCheck = faCheck;
    readonly faPencilAlt = faPencilAlt;

    ngOnInit() {
        super.ngOnInit();
    }

    ngOnChanges() {
        this.setUserProperties();
        this.setUserAuthorityIconAndTooltip();
    }
}
