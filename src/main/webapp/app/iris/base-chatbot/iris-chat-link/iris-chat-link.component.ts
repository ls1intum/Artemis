import { Component } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faFile } from '@fortawesome/free-solid-svg-icons';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { Input, OnInit } from '@angular/core';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-iris-chat-link',
    standalone: true,
    imports: [FontAwesomeModule],
    templateUrl: './iris-chat-link.component.html',
    styleUrl: './iris-chat-link.component.scss',
})
export class IrisChatLinkComponent implements OnInit {
    @Input({ required: true }) markdown: string;
    links: { href: string; title: string }[] = [];
    faFile = faFile;
    instruction: string = 'Click to go to exercise'; // TODO: i18n
    constructor(
        private markdownService: ArtemisMarkdownService,
        private router: Router,
    ) {}

    ngOnInit() {
        this.links = this.links.concat(this.markdownService.extractAnchorTagsFromMarkdown(this.markdown));
    }
    navigateToLink(link: { href: string; title: string }) {
        this.router.navigateByUrl(link.href);
    }
}
