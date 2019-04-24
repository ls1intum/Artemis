import 'jest-preset-angular';
import './jest-global-mocks';
import { library } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch, faTimesCircle, faCheckCircle, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { faSave as farSave, faPlayCircle as farPlayCircle } from '@fortawesome/free-regular-svg-icons';

// TODO: Is there a solution so that we don't have to import all fa icons here?
library.add(faCircleNotch);
library.add(faTimesCircle);
library.add(faCheckCircle);
library.add(faExclamationTriangle);
library.add(farSave);
library.add(farPlayCircle);
