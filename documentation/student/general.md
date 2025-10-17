---
id: general
title: General Information
sidebar_label: General Information
---

import Callout from "../src/components/callout/callout";
import {CalloutVariant} from "../src/components/callout/callout.types";
import menuImg from './general/general-menu-exercise.png';
import sidebarImg from './general/sidebar-collapse-exercise.png';
import exerciseOverviewCurrentImg from './general/exercise-overview-current.png';
import exerciseOverviewPastImg from './general/exercise-overview-past.png';

# General Information

Artemis offers different exercise types that share common characteristics:

- **Release Date:** When the exercise becomes visible to students. If none is set, it is visible immediately.
- **Due Date:** The final date students can submit their solutions.
- **Assessment Due Date:** The date until which tutors complete assessments. Feedback is released after this date.
- **Points:** Each exercise grants points, which may count toward the course score or serve as bonus points.

---

## Exercise Organization and Navigation

Students can access the exercise overview by clicking the **Exercises** tab.

<img src={menuImg} width="250" alt="Exercise Menu" />

The exercise overview categorizes exercises by time periods:

- **Future:** Not yet released.
- **Due Soon:** Active and due within 3 days.
- **Current:** Available, not due within 3 days.
- **Past:** Closed, submission no longer possible.
- **No Date:** Exercises without time restrictions.
  
<img src={sidebarImg} width="250" alt="Sidebar Menu" />

When sections (like *No Date*) contain more than 5 exercises, Artemis may group them by week for easier navigation.

| Current Exercises                                                                    | Past Exercises                                                                 |
|--------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| <img src={exerciseOverviewCurrentImg} width="250" alt="Exercise Overview Current" /> | <img src={exerciseOverviewPastImg} width="250" alt="Exercise Overview Past" /> |

<Callout variant={CalloutVariant.info}>
<p>Tip: Use the sidebar filters to quickly locate upcoming or overdue exercises.</p>
</Callout>
