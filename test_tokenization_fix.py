"""
Verifies that Tokenization.FIELD fixes the lecture/lecture_unit filter collision.

Creates two temporary collections — one with WORD tokenization (reproduces the bug)
and one with FIELD tokenization (the fix) — inserts the same test data into both,
then runs an Equal filter on type="lecture" and asserts the results are different.

Cleans up both collections at the end.
"""

import sys
import weaviate
import weaviate.classes.config as wvcc
import weaviate.classes.query as wvq

WEAVIATE_URL = "http://localhost:8001"
COLLECTION_WORD = "TestTypeWord"
COLLECTION_FIELD = "TestTypeField"

OBJECTS = [
    {"type": "lecture",      "title": "Lecture A",       "course_id": 1},
    {"type": "lecture",      "title": "Lecture B",       "course_id": 1},
    {"type": "lecture_unit", "title": "Unit (released)", "course_id": 1, "released": True},
    {"type": "lecture_unit", "title": "Unit (future)",   "course_id": 1, "released": False},
]


def make_collection(client: weaviate.WeaviateClient, name: str, tokenization: wvcc.Tokenization):
    if client.collections.exists(name):
        client.collections.delete(name)
    client.collections.create(
        name=name,
        properties=[
            wvcc.Property(
                name="type",
                data_type=wvcc.DataType.TEXT,
                index_searchable=False,
                index_filterable=True,
                tokenization=tokenization,
            ),
            wvcc.Property(name="title",     data_type=wvcc.DataType.TEXT),
            wvcc.Property(name="course_id", data_type=wvcc.DataType.INT, index_filterable=True),
            wvcc.Property(name="released",  data_type=wvcc.DataType.BOOL, index_filterable=True),
        ],
        vectorizer_config=wvcc.Configure.Vectorizer.none(),
    )
    col = client.collections.get(name)
    with col.batch.fixed_size(batch_size=10) as batch:
        for obj in OBJECTS:
            batch.add_object(properties=obj)
    return col


def query_type_equals_lecture(col):
    result = col.query.fetch_objects(
        filters=wvq.Filter.by_property("type").equal("lecture"),
        limit=20,
    )
    return [(o.properties["type"], o.properties["title"]) for o in result.objects]


def main():
    client = weaviate.connect_to_custom(
        http_host="localhost",
        http_port=8001,
        http_secure=False,
        grpc_host="localhost",
        grpc_port=50051,
        grpc_secure=False,
        headers={"X-OpenAI-Api-Key": "ollama"},
    )

    try:
        print("=== Reproducing the BUG (word tokenization) ===")
        word_col = make_collection(client, COLLECTION_WORD, wvcc.Tokenization.WORD)
        word_hits = query_type_equals_lecture(word_col)
        print(f'  type Equal "lecture" returned {len(word_hits)} objects:')
        for t, title in word_hits:
            marker = "  <-- LEAK (lecture_unit slipped through!)" if t == "lecture_unit" else ""
            print(f"    type={t!r}  title={title!r}{marker}")

        bug_present = any(t == "lecture_unit" for t, _ in word_hits)
        print(f"  Bug reproduced: {bug_present}")
        print()

        print("=== Verifying the FIX (field tokenization) ===")
        field_col = make_collection(client, COLLECTION_FIELD, wvcc.Tokenization.FIELD)
        field_hits = query_type_equals_lecture(field_col)
        print(f'  type Equal "lecture" returned {len(field_hits)} objects:')
        for t, title in field_hits:
            print(f"    type={t!r}  title={title!r}")

        bug_fixed = all(t == "lecture" for t, _ in field_hits) and len(field_hits) == 2
        print(f"  Fix verified: {bug_fixed}")
        print()

        if bug_present and bug_fixed:
            print("PASS: word tokenization shows the bug, field tokenization eliminates it.")
            return 0
        elif not bug_present:
            print("WARN: Could not reproduce the bug with word tokenization — check Weaviate version.")
            return 1
        else:
            print("FAIL: Field tokenization did not fix the collision.")
            return 1

    finally:
        for name in (COLLECTION_WORD, COLLECTION_FIELD):
            if client.collections.exists(name):
                client.collections.delete(name)
                print(f"Cleaned up {name}")
        client.close()


if __name__ == "__main__":
    sys.exit(main())
