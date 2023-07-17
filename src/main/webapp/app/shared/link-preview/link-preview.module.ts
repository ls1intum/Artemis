import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { HttpClientModule } from '@angular/common/http';
import { LinkPreviewService } from 'app/shared/link-preview/services/link-preview.service';
import { LinkifyService } from 'app/shared/link-preview/services/linkify.service';

@NgModule({
    imports: [CommonModule, HttpClientModule],
    providers: [LinkPreviewService, LinkifyService],
})
export class LinkPreviewModule {}
