/* after changing this file run 'yarn run webpack:build' */
/* tslint:disable */
import '../content/scss/vendor.scss';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/of';
import 'rxjs/add/observable/throw';
import { library } from '@fortawesome/fontawesome-svg-core';
import {
    faAngleDown,
    faAngleRight,
    faAngleDoubleDown,
    faAngleDoubleUp,
    faArchive,
    faArrowLeft,
    faArrowRight,
    faArrowsAltV,
    faAsterisk,
    faBan,
    faBars,
    faBell,
    faBook,
    faCalendarAlt,
    faChalkboardTeacher,
    faChartPie,
    faCheck,
    faCheckCircle,
    faChevronDown,
    faChevronLeft,
    faChevronRight,
    faChevronUp,
    faCircle,
    faCircleNotch,
    faClock,
    faCloud,
    faCodeBranch,
    faDownload,
    faEdit,
    faCheckDouble,
    faKeyboard,
    faProjectDiagram,
    faFileUpload,
    faFilePowerpoint,
    faThLarge,
    faAngleUp,
    faSortAmountUp,
    faSortAmountDown,
    faUndo,
    faEraser,
    faExclamationCircle,
    faExclamationTriangle,
    faExternalLinkAlt,
    faEye,
    faFile,
    faFileExport,
    faFlag,
    faFolder,
    faFolderOpen,
    faFont,
    faGraduationCap,
    faHdd,
    faHeart,
    faHome,
    faInfo,
    faInfoCircle,
    faList,
    faListAlt,
    faPencilAlt,
    faPlayCircle,
    faPlus,
    faQuestionCircle,
    faRedo,
    faRoad,
    faSave,
    faSearch,
    faSignal,
    faSignInAlt,
    faSignOutAlt,
    faSort,
    faSortDown,
    faSortUp,
    faSpinner,
    faSync,
    faTachometerAlt,
    faTasks,
    faTerminal,
    faThList,
    faTimes,
    faTimesCircle,
    faTrash,
    faTrashAlt,
    faUnlink,
    faUpload,
    faUser,
    faUserPlus,
    faWrench,
    faBold,
    faItalic,
    faUnderline,
    faLink,
    faHeading,
    faQuoteLeft,
    faCode,
    faCopy,
    faListOl,
    faListUl,
    faImage,
    faPaperclip,
    faGripLinesVertical,
} from '@fortawesome/free-solid-svg-icons';

import {
    faCheckCircle as farCheckCircle,
    faCheckSquare as farCheckSquare,
    faCircle as farCircle,
    faFileAlt as farFileAlt,
    faFileCode as farFileCode,
    faFileImage as farFileImage,
    faListAlt as farListAlt,
    faPlayCircle as farPlayCircle,
    faQuestionCircle as farQuestionCircle,
    faCommentDots as farCommentDots,
    faSave as farSave,
    faSquare as farSquare,
    faTimesCircle as farTimeCircle,
    faDotCircle as farDotCircle,
} from '@fortawesome/free-regular-svg-icons';

// Imports all fontawesome core and solid icons

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
library.add(faArrowRight);
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
library.add(faFilePowerpoint);
library.add(faFont);
library.add(faThLarge);
library.add(faAngleUp);
library.add(faAngleDown);
library.add(faSortAmountUp);
library.add(faSortAmountDown);
library.add(faChalkboardTeacher);
library.add(faCheckCircle);
library.add(faFileExport);
library.add(faCircleNotch);
library.add(faUndo);
library.add(faExclamationTriangle);
library.add(faUnlink);
library.add(faFolder);
library.add(faFolderOpen);
library.add(faPlayCircle);
library.add(faInfo);
library.add(faInfoCircle);
library.add(faGraduationCap);
library.add(faChartPie);
library.add(faExternalLinkAlt);
library.add(faSignal);
library.add(faEdit);
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
library.add(faBold);
library.add(faUnderline);
library.add(faItalic);
library.add(faQuoteLeft);
library.add(faLink);
library.add(faHeading);
library.add(faCode);
library.add(faCopy);
library.add(faListUl);
library.add(faListOl);
library.add(faImage);
library.add(farQuestionCircle);
library.add(farCheckCircle);
library.add(farDotCircle);
library.add(farTimeCircle);
library.add(farCommentDots);
library.add(farListAlt);
library.add(farFileImage);
library.add(farCheckSquare);
library.add(farSquare);
library.add(farFileAlt);
library.add(farPlayCircle);
library.add(farFileCode);
library.add(farCircle);
library.add(farSave);
library.add(faGripLinesVertical);
library.add(faPaperclip);
library.add(faAngleDoubleDown);
library.add(faAngleDoubleUp);
// jhipster-needle-add-element-to-vendor - JHipster will add new menu items here
