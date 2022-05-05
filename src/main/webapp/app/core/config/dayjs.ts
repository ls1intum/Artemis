import dayjs from 'dayjs/esm';
import customParseFormat from 'dayjs/esm/plugin/customParseFormat';
import duration from 'dayjs/esm/plugin/duration';
import relativeTime from 'dayjs/esm/plugin/relativeTime';
import isoWeek from 'dayjs/esm/plugin/isoWeek';
import utc from 'dayjs/esm/plugin/utc';
import isSameOrBefore from 'dayjs/esm/plugin/isSameOrBefore';
import isSameOrAfter from 'dayjs/esm/plugin/isSameOrAfter';
import isBetween from 'dayjs/esm/plugin/isBetween';
import minMax from 'dayjs/esm/plugin/minMax';
import localizedFormat from 'dayjs/esm/plugin/localizedFormat';
import isoWeeksInYear from 'dayjs/esm/plugin/isoWeeksInYear';
import isLeapYear from 'dayjs/esm/plugin/isLeapYear';

import 'dayjs/esm/locale/en';
import 'dayjs/esm/locale/de';

dayjs.extend(customParseFormat);
dayjs.extend(duration);
dayjs.extend(relativeTime);
dayjs.extend(isoWeek);
dayjs.extend(utc);
dayjs.extend(isSameOrBefore);
dayjs.extend(isSameOrAfter);
dayjs.extend(isBetween);
dayjs.extend(minMax);
dayjs.extend(localizedFormat);
dayjs.extend(isoWeeksInYear);
dayjs.extend(isLeapYear);
