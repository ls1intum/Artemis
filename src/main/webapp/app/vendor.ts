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
    faFont,
    faAngleUp,
    faAngleDown,
    faSortAmountUp,
    faSortAmountDown,
    faFileExport,
    faCheck,
} from '@fortawesome/free-solid-svg-icons';

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
library.add(faCheck);

// jhipster-needle-add-element-to-vendor - JHipster will add new menu items here
