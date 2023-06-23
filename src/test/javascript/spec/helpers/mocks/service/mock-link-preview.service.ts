import { LinkPreview } from 'app/shared/link-preview/services/link-preview.service';
import { Observable, of } from 'rxjs';

export class MockLinkPreviewService {
    fetchLink(url: string): Observable<LinkPreview> {
        return of({} as LinkPreview);
    }
}
