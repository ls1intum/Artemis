import { Provider } from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';

// Angular currently marks the legacy animation providers as deprecated while transitioning to the new animation API.
// For unit tests we still need to install the compatibility provider, so we encapsulate the call here and suppress the warning once.
// eslint-disable-next-line @typescript-eslint/no-deprecated
export const provideTestAnimations = (): Provider => provideAnimations();
