import { Link } from 'app/communication/link-preview/services/linkify.service';

export class MockLinkifyService {
    find(text: string): Link[] {
        return [];
    }
}
