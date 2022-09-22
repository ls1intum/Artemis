import { AfterViewInit, Component, EventEmitter, Input, Output } from '@angular/core';
import interact from 'interactjs';
import { Post } from 'app/entities/metis/post.model';
import { faArrowLeft, faChevronLeft, faGripLinesVertical, faXmark } from '@fortawesome/free-solid-svg-icons';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

@Component({
    selector: 'jhi-thread-sidebar',
    templateUrl: './thread-sidebar.component.html',
    styleUrls: ['./thread-sidebar.component.scss'],
})
export class ThreadSidebarComponent implements AfterViewInit {
    @Output() closePostThread = new EventEmitter<void>();

    post?: Post;
    createdAnswerPost: AnswerPost;

    @Input() set activePost(activePost: Post) {
        if (activePost) {
            this.post = activePost;
            this.createdAnswerPost = this.createEmptyAnswerPost();
        }
    }

    // Icons
    faXmark = faXmark;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;
    faArrowLeft = faArrowLeft;

    constructor() {}

    /**
     * makes discussion section expandable by configuring 'interact'
     */
    ngAfterViewInit(): void {
        interact('.expanded-thread')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 435, height: 0 },
                        max: { width: 600, height: 4000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }

    /**
     * creates empty default answer post that is needed on initialization of a newly opened modal to edit or create an answer post, with accordingly set resolvesPost flag
     * @return AnswerPost created empty default answer post
     */
    createEmptyAnswerPost(): AnswerPost {
        const answerPost = new AnswerPost();
        answerPost.content = '';
        answerPost.post = this.post;
        return answerPost;
    }
}
