/* after changing this file run 'yarn run webpack:build' */
/* tslint:disable */
import '../content/scss/vendor.scss';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/of';
import 'rxjs/add/observable/throw';

// Imports all fontawesome core and solid icons

import { library } from '@fortawesome/fontawesome-svg-core';
import {
    faUser,
    faSort,
    faSortUp,
    faSortDown,
    faSync,
    faEye,
    faBan,
    faTimes,
    faArrowLeft,
    faSave,
    faPlus,
    faPencilAlt,
    faBars,
    faThList,
    faUserPlus,
    faRoad,
    faTachometerAlt,
    faHeart,
    faList,
    faListAlt,
    faBell,
    faBook,
    faHdd,
    faFlag,
    faWrench,
    faClock,
    faCloud,
    faSignOutAlt,
    faSignInAlt,
    faCalendarAlt,
    faSearch,
    faTrashAlt,
    faAsterisk,
    faTasks,
    faHome,
    faArchive,
    faEraser,
    faDownload,
    faCheckDouble,
    faKeyboard,
    faProjectDiagram,
    faFileUpload,
    faThLarge,
    faAngleUp,
    faSortAmountUp,
    faSortAmountDown,
    faFileExport,
    faCircleNotch,
    faCheckCircle,
    faUndo,
    faTrash,
    faExclamationTriangle,
    faUnlink,
    faFont,
    faFolder,
    faFolderOpen,
    faPlayCircle,
    faInfoCircle,
    faGraduationCap,
    faChartPie,
    faExternalLinkAlt,
    faSignal,
    faChevronUp,
    faChevronDown,
    faChevronLeft,
    faChevronRight,
    faRedo,
    faExclamationCircle,
    faTerminal,
    faSpinner,
    faQuestionCircle,
    faTimesCircle,
    faAngleRight,
    faAngleDown,
    faCheck,
    faUpload,
    faArrowsAltV,
    faFile,
    faCodeBranch,
    faCircle
} from '@fortawesome/free-solid-svg-icons';

import {
    faQuestionCircle as farQuestionCircle,
    faCheckCircle as farCheckCircle,
    faTimesCircle as farTimeCircle,
    faListAlt as farListAlt,
    faFileImage as farFileImage,
    faCheckSquare as farCheckSquare,
    faSquare as farSquare,
    faFileAlt as farFileAlt,
    faPlayCircle as farPlayCircle,
    faFileCode as farFileCode,
    faCircle as farCircle
} from '@fortawesome/free-regular-svg-icons';

// Adds the SVG icon to the library so you can use it in your page
library.add(faUser);
library.add(faSort);
library.add(faSortUp);
library.add(faSortDown);
library.add(faSync);
library.add(faEye);
library.add(faBan);
library.add(faTimes);
library.add(faArrowLeft);
library.add(faSave);
library.add(faPlus);
library.add(faPencilAlt);
library.add(faBars);
library.add(faHome);
library.add(faThList);
library.add(faUserPlus);
library.add(faRoad);
library.add(faTachometerAlt);
library.add(faHeart);
library.add(faList);
library.add(faListAlt);
library.add(faBell);
library.add(faTasks);
library.add(faBook);
library.add(faHdd);
library.add(faFlag);
library.add(faWrench);
library.add(faClock);
library.add(faCloud);
library.add(faSignOutAlt);
library.add(faSignInAlt);
library.add(faCalendarAlt);
library.add(faSearch);
library.add(faTrashAlt);
library.add(faTrash);
library.add(faAsterisk);
library.add(faArchive);
library.add(faEraser);
library.add(faDownload);
library.add(faCheckDouble);
library.add(faKeyboard);
library.add(faProjectDiagram);
library.add(faFileUpload);
library.add(faFont);
library.add(faThLarge);
library.add(faAngleUp);
library.add(faAngleDown);
library.add(faSortAmountUp);
library.add(faSortAmountDown);
library.add(faFileExport);
library.add(faCircleNotch);
library.add(faCheckCircle);
library.add(faUndo);
library.add(faExclamationTriangle);
library.add(faUnlink);
library.add(faFolder);
library.add(faFolderOpen);
library.add(faPlayCircle);
library.add(faInfoCircle);
library.add(faGraduationCap);
library.add(faChartPie);
library.add(faExternalLinkAlt);
library.add(faSignal);
library.add(faChevronUp);
library.add(faChevronDown);
library.add(faChevronLeft);
library.add(faChevronRight);
library.add(faRedo);
library.add(faExclamationCircle);
library.add(faTerminal);
library.add(faSpinner);
library.add(faQuestionCircle);
library.add(faTimesCircle);
library.add(faAngleRight);
library.add(faCheck);
library.add(faUpload);
library.add(faArrowsAltV);
library.add(faFile);
library.add(faCodeBranch);
library.add(faCircle);

library.add(farQuestionCircle);
library.add(farCheckCircle);
library.add(farTimeCircle);
library.add(farListAlt);
library.add(farFileImage);
library.add(farCheckSquare);
library.add(farSquare);
library.add(farFileAlt);
library.add(farPlayCircle);
library.add(farFileCode);
library.add(farCircle);

// jhipster-needle-add-element-to-vendor - JHipster will add new menu items here
