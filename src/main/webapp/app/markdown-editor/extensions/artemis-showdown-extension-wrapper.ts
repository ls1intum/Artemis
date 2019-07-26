import { ShowdownExtension } from 'showdown';
import { Observable } from 'rxjs';

export interface ArtemisShowdownExtensionWrapper {
    getExtension: () => ShowdownExtension;
    subscribeForInjectableElementsFound: () => Observable<() => void>;
}
