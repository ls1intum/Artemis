import { AfterViewInit, Component, Input } from '@angular/core';
import interact from 'interactjs';
import { Post } from 'app/entities/metis/post.model';
import { faArrowLeft, faChevronLeft, faGripLinesVertical, faXmark } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-thread-sidebar',
    templateUrl: './thread-sidebar.component.html',
    styleUrls: ['./thread-sidebar.component.scss'],
})
export class ThreadSidebarComponent implements AfterViewInit {
    collapsed = true;
    post?: Post;

    @Input() set activePost(activePost: Post) {
        this.collapsed = true;
        if (activePost) {
            this.post = activePost;
            this.collapsed = false;
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
                        min: { width: 375, height: 0 },
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
}
