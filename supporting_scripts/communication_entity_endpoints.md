# Communication module — entity endpoints

- Source CSV: `supporting_scripts/dto_cov_pkg/dto_coverage.csv`
- Total entity endpoints: **22**

## By resource file (descending)

| Count | File |
|---:|---|
| 6 | `src/main/java/de/tum/cit/aet/artemis/communication/web/ConversationMessageResource.java` |
| 5 | `src/main/java/de/tum/cit/aet/artemis/communication/web/FaqResource.java` |
| 3 | `src/main/java/de/tum/cit/aet/artemis/communication/web/SystemNotificationResource.java` |
| 3 | `src/main/java/de/tum/cit/aet/artemis/communication/web/AnswerMessageResource.java` |
| 3 | `src/main/java/de/tum/cit/aet/artemis/communication/web/admin/AdminSystemNotificationResource.java` |
| 1 | `src/main/java/de/tum/cit/aet/artemis/communication/web/GlobalNotificationSettingResource.java` |
| 1 | `src/main/java/de/tum/cit/aet/artemis/communication/web/conversation/OneToOneChatResource.java` |

## Full list

| File | HTTP | Return type | Label |
|---|---:|---|---|
| `src/main/java/de/tum/cit/aet/artemis/communication/web/ConversationMessageResource.java` | REQUEST | `ResponseEntity<Post>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/ConversationMessageResource.java` | REQUEST | `ResponseEntity<Post>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/ConversationMessageResource.java` | REQUEST | `ResponseEntity<List<Post>>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/ConversationMessageResource.java` | REQUEST | `ResponseEntity<Post>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/ConversationMessageResource.java` | REQUEST | `ResponseEntity<Post>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/ConversationMessageResource.java` | REQUEST | `ResponseEntity<List<Post>>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/GlobalNotificationSettingResource.java` | REQUEST | `ResponseEntity<GlobalNotificationSetting>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/FaqResource.java` | REQUEST | `ResponseEntity<FaqDTO>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/FaqResource.java` | REQUEST | `ResponseEntity<FaqDTO>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/FaqResource.java` | REQUEST | `ResponseEntity<FaqDTO>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/FaqResource.java` | REQUEST | `ResponseEntity<Set<FaqDTO>>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/FaqResource.java` | REQUEST | `ResponseEntity<Set<String>>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/SystemNotificationResource.java` | REQUEST | `ResponseEntity<List<SystemNotification>>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/SystemNotificationResource.java` | REQUEST | `ResponseEntity<List<SystemNotification>>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/SystemNotificationResource.java` | REQUEST | `ResponseEntity<SystemNotification>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/AnswerMessageResource.java` | PUT | `ResponseEntity<AnswerPost>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/AnswerMessageResource.java` | REQUEST | `ResponseEntity<AnswerPost>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/AnswerMessageResource.java` | REQUEST | `ResponseEntity<List<AnswerPost>>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/admin/AdminSystemNotificationResource.java` | REQUEST | `ResponseEntity<SystemNotification>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/admin/AdminSystemNotificationResource.java` | REQUEST | `ResponseEntity<SystemNotification>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/admin/AdminSystemNotificationResource.java` | REQUEST | `ResponseEntity<SystemNotification>` | entity |
| `src/main/java/de/tum/cit/aet/artemis/communication/web/conversation/OneToOneChatResource.java` | REQUEST | `ResponseEntity<OneToOneChatDTO>` | entity |
