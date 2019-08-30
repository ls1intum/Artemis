import { NgModule } from '@angular/core';
import { FaIconLibrary, FontAwesomeModule } from '@fortawesome/angular-fontawesome';

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
    faInfoCircle,
    faList,
    faListAlt,
    faPencilAlt,
    faPlayCircle,
    faPlus,
    faQuestionCircle,
    faQuestion,
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
    faGripLines,
    faCompress,
    faEquals,
    faRobot,
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

@NgModule({
    imports: [FontAwesomeModule],
    exports: [FontAwesomeModule],
})
export class ArtemisIconsModule {
    static forRoot() {
        return {
            ngModule: ArtemisIconsModule,
        };
    }
    constructor(private library: FaIconLibrary) {
        // Adds the SVG icon to the library so you can use it in your page
        library.addIcons(faUser);
        library.addIcons(faSort);
        library.addIcons(faSortUp);
        library.addIcons(faSortDown);
        library.addIcons(faSync);
        library.addIcons(faEye);
        library.addIcons(faBan);
        library.addIcons(faTimes);
        library.addIcons(faArrowLeft);
        library.addIcons(faArrowRight);
        library.addIcons(faSave);
        library.addIcons(faPlus);
        library.addIcons(faPencilAlt);
        library.addIcons(faBars);
        library.addIcons(faHome);
        library.addIcons(faThList);
        library.addIcons(faUserPlus);
        library.addIcons(faRoad);
        library.addIcons(faTachometerAlt);
        library.addIcons(faHeart);
        library.addIcons(faList);
        library.addIcons(faListAlt);
        library.addIcons(faBell);
        library.addIcons(faTasks);
        library.addIcons(faBook);
        library.addIcons(faHdd);
        library.addIcons(faFlag);
        library.addIcons(faWrench);
        library.addIcons(faClock);
        library.addIcons(faCloud);
        library.addIcons(faSignOutAlt);
        library.addIcons(faSignInAlt);
        library.addIcons(faCalendarAlt);
        library.addIcons(faSearch);
        library.addIcons(faTrashAlt);
        library.addIcons(faTrash);
        library.addIcons(faAsterisk);
        library.addIcons(faArchive);
        library.addIcons(faEraser);
        library.addIcons(faDownload);
        library.addIcons(faCheckDouble);
        library.addIcons(faKeyboard);
        library.addIcons(faProjectDiagram);
        library.addIcons(faFileUpload);
        library.addIcons(faFilePowerpoint);
        library.addIcons(faFont);
        library.addIcons(faThLarge);
        library.addIcons(faAngleUp);
        library.addIcons(faAngleDown);
        library.addIcons(faSortAmountUp);
        library.addIcons(faSortAmountDown);
        library.addIcons(faChalkboardTeacher);
        library.addIcons(faCheckCircle);
        library.addIcons(faFileExport);
        library.addIcons(faCircleNotch);
        library.addIcons(faUndo);
        library.addIcons(faExclamationTriangle);
        library.addIcons(faUnlink);
        library.addIcons(faFolder);
        library.addIcons(faFolderOpen);
        library.addIcons(faPlayCircle);
        library.addIcons(faInfoCircle);
        library.addIcons(faGraduationCap);
        library.addIcons(faChartPie);
        library.addIcons(faExternalLinkAlt);
        library.addIcons(faSignal);
        library.addIcons(faEdit);
        library.addIcons(faChevronUp);
        library.addIcons(faChevronDown);
        library.addIcons(faChevronLeft);
        library.addIcons(faChevronRight);
        library.addIcons(faRedo);
        library.addIcons(faExclamationCircle);
        library.addIcons(faTerminal);
        library.addIcons(faSpinner);
        library.addIcons(faQuestionCircle);
        library.addIcons(faQuestion);
        library.addIcons(faTimesCircle);
        library.addIcons(faAngleRight);
        library.addIcons(faCheck);
        library.addIcons(faUpload);
        library.addIcons(faArrowsAltV);
        library.addIcons(faFile);
        library.addIcons(faCodeBranch);
        library.addIcons(faCircle);
        library.addIcons(faBold);
        library.addIcons(faUnderline);
        library.addIcons(faItalic);
        library.addIcons(faQuoteLeft);
        library.addIcons(faLink);
        library.addIcons(faHeading);
        library.addIcons(faCode);
        library.addIcons(faCopy);
        library.addIcons(faListUl);
        library.addIcons(faListOl);
        library.addIcons(faImage);
        library.addIcons(faRobot);
        library.addIcons(farQuestionCircle);
        library.addIcons(farCheckCircle);
        library.addIcons(farDotCircle);
        library.addIcons(farTimeCircle);
        library.addIcons(farCommentDots);
        library.addIcons(farListAlt);
        library.addIcons(farFileImage);
        library.addIcons(farCheckSquare);
        library.addIcons(farSquare);
        library.addIcons(farFileAlt);
        library.addIcons(farPlayCircle);
        library.addIcons(farFileCode);
        library.addIcons(farCircle);
        library.addIcons(farSave);
        library.addIcons(faGripLinesVertical);
        library.addIcons(faGripLines);
        library.addIcons(faPaperclip);
        library.addIcons(faAngleDoubleDown);
        library.addIcons(faAngleDoubleUp);
        library.addIcons(faCompress);
        library.addIcons(faEquals);
    }
}
