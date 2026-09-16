"""
End-to-end filter test against the live Artemis_SearchableEntities collection.

Inserts a test lecture and a future-dated test lecture_unit into the real collection,
then runs the exact compound OR filter that GlobalSearchResource generates for a student
(lecture disjunct OR lecture_unit disjunct), and verifies:
  - The lecture appears in results (correct)
  - The future lecture_unit does NOT appear via the lecture disjunct leak (was the bug)
  - The future lecture_unit does NOT appear at all (student cannot see unreleased units)

Cleans up inserted test objects at the end regardless of outcome.
"""

import sys
import uuid
from datetime import datetime, timezone, timedelta

import weaviate
import weaviate.classes.query as wvq

WEAVIATE_URL = "localhost"
COLLECTION = "Artemis_SearchableEntities"
TEST_COURSE_ID = 99999          # sentinel course id unlikely to clash with real data
TEST_LECTURE_ID = 88881
TEST_UNIT_ID    = 88882

FUTURE_DATE = (datetime.now(timezone.utc) + timedelta(days=30)).strftime("%Y-%m-%dT%H:%M:%S.000+00:00")
NOW_DATE    = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000+00:00")


def det_uuid(type_val: str, entity_id: int) -> str:
    """Mirror WeaviateUuidUtil.deterministicUuid(type, id) — UUID v5, DNS namespace."""
    return str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{type_val}:{entity_id}"))


def main():
    client = weaviate.connect_to_custom(
        http_host=WEAVIATE_URL, http_port=8001, http_secure=False,
        grpc_host=WEAVIATE_URL, grpc_port=50051, grpc_secure=False,
        headers={"X-OpenAI-Api-Key": "ollama"},
    )

    lecture_uuid = det_uuid("lecture",      TEST_LECTURE_ID)
    unit_uuid    = det_uuid("lecture_unit", TEST_UNIT_ID)
    col = client.collections.get(COLLECTION)

    try:
        # --- insert test objects ---
        col.data.insert(
            uuid=lecture_uuid,
            properties={
                "type":      "lecture",
                "entity_id": TEST_LECTURE_ID,
                "course_id": TEST_COURSE_ID,
                "title":     "TEST_LECTURE (delete me)",
            },
        )
        col.data.insert(
            uuid=unit_uuid,
            properties={
                "type":         "lecture_unit",
                "entity_id":    TEST_UNIT_ID,
                "course_id":    TEST_COURSE_ID,
                "title":        "TEST_UNIT_FUTURE (delete me)",
                "release_date": FUTURE_DATE,
            },
        )
        print(f"Inserted test lecture  {lecture_uuid}")
        print(f"Inserted test unit     {unit_uuid} (release_date={FUTURE_DATE})")
        print()

        now = NOW_DATE

        # --- lecture disjunct (no release_date gate — correct for actual lectures) ---
        lecture_disjunct = wvq.Filter.by_property("type").equal("lecture") & \
                           wvq.Filter.by_property("course_id").equal(TEST_COURSE_ID)

        # --- lecture_unit disjunct with student gate ---
        unit_disjunct = (
            wvq.Filter.by_property("type").equal("lecture_unit") &
            wvq.Filter.by_property("course_id").equal(TEST_COURSE_ID) &
            (wvq.Filter.by_property("release_date").less_or_equal(now) |
             wvq.Filter.by_property("release_date").is_none(True))
        )

        compound_filter = lecture_disjunct | unit_disjunct

        results = col.query.fetch_objects(
            filters=compound_filter,
            limit=50,
            return_properties=["type", "entity_id", "title", "release_date"],
        )

        hits = [(o.properties["type"], o.properties.get("title",""), o.properties.get("release_date","")) for o in results.objects]
        # only look at our test objects
        test_hits = [(t, ti, rd) for t, ti, rd in hits if "TEST_" in ti]

        print("=== Results from compound lecture OR lecture_unit filter (student view) ===")
        if not test_hits:
            print("  (no test objects in results)")
        for t, title, rd in test_hits:
            print(f"  type={t!r}  title={title!r}  release_date={rd!r}")
        print()

        lecture_visible   = any(t == "lecture"      for t, *_ in test_hits)
        unit_visible      = any(t == "lecture_unit" for t, *_ in test_hits)

        print(f"Lecture visible to student:           {lecture_visible}  (expected: True)")
        print(f"Future lecture_unit visible to student: {unit_visible}  (expected: False)")
        print()

        if lecture_visible and not unit_visible:
            print("PASS: lecture is visible, future lecture_unit is hidden.")
            return 0
        elif unit_visible:
            print("FAIL: future lecture_unit is still leaking through — fix did not take effect.")
            return 1
        else:
            print("WARN: lecture not found either — check course_id or collection state.")
            return 1

    finally:
        for u in (lecture_uuid, unit_uuid):
            try:
                col.data.delete_by_id(u)
            except Exception:
                pass
        print("Cleaned up test objects.")
        client.close()


if __name__ == "__main__":
    sys.exit(main())
