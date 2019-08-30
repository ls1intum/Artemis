import { ShowdownExtension } from 'showdown';
import { Observable } from 'rxjs';

/**
 * The idea of this interface is to provide more information for an extension.
 * By implementing the interface, the extension can use data that is in the closure of the class (e.g. this.latestResult).
 * 1) The component that uses the extension can request it from the wrapper class by using getExtension.
 * 2) In some cases it might also be necessary to inject content after the html is loaded, as async data fetching is necessary.
 *    Therefore, the component can subscribe for injectable elements.
 *
 */
export interface ArtemisShowdownExtensionWrapper {
    getExtension: () => ShowdownExtension;
    subscribeForInjectableElementsFound: () => Observable<() => void>;
}
