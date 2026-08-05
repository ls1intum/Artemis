"""The run matrix: two fixed domain texts and the fourteen configurations.

Both domain texts were written once, before any measured run, and are reused verbatim. They are kept to
a few words because that is what the field realistically receives — an instructor types a domain, not a
specification.

**The domain axis is not near versus far.** For a sorting exercise no domain is genuinely far, since
everything sorts somehow, and calling the axis near/far invites the objection that the "far" domain is
merely a second medium one. The axis this corpus actually implements is sharper and checkable: does the
domain **supply the ordering key**, or does it **force the model to choose one**? Returned books supply
it — a due date is a property you read off each entity. A colour palette does not: hue is circular,
brightness and saturation are competing and equally defensible, and "a sorted palette" has no agreed
meaning. The model has to commit to a key the domain never specified and then keep the whole exercise
coherent with that commitment — problem statement, ``Comparable``, tests, and diagram all agreeing.

That is what makes C4, C13, and C14 harder than C3 in a way a reader can check, and it is a claim about
the pairing of these domains with *this* source exercise, not about the domains in general.

One consequence for the rubric: this contrast is about coherence under an arbitrary decision, which no
automated check can see. C4 and C13 therefore need reading, and the mechanical checks will have nothing
to say about them.
"""

from typing import Dict, List, NamedTuple, Optional

# D-supplied: the domain hands over the ordering key. A due date is a property read off each entity, so
# the source's List<Date> transfers almost unchanged.
D_SUPPLIED = "a library's returned books"

# D-unspecified: the domain names no ordering key. The model must commit to one and stay coherent with it.
D_UNSPECIFIED = "a painter's colour palette"

DOMAIN_TEXTS: Dict[str, str] = {"D-supplied": D_SUPPLIED, "D-unspecified": D_UNSPECIFIED}

DOMAIN_RATIONALE: Dict[str, str] = {
    "D-supplied": (
        "The domain supplies the ordering key. Returned books are a collection of comparable things with an "
        "obvious comparable field — a return or due date — that is a property of each entity, so the source's "
        "List<Date> and its ordering transfer unchanged, and the size of a day's returns varies enough to give "
        "the size-based Policy decision a natural counterpart. The wording never names the ordering outright, "
        "so the model still has to find it, but it does not have to invent it."
    ),
    "D-unspecified": (
        "The domain supplies no ordering key. Hue is circular rather than linear, and brightness and "
        "saturation are competing, equally defensible alternatives, so 'a sorted palette' has no agreed "
        "meaning. The model must commit to a key the domain never specified and then keep the problem "
        "statement, the Comparable implementation, the tests, and the diagram all consistent with that one "
        "commitment. That is a real decision with a visible trace, and it is what the rubric scores here: did "
        "the variant state the key, does the ordering match it, do the tests grade it. A model that quietly "
        "picks brightness and proceeds has not failed — it has made the choice. What is recorded is whether "
        "the rest of the exercise follows that choice or contradicts it."
    ),
}


class Configuration(NamedTuple):
    """One cell of the matrix. ``None`` means the intent field is omitted from the request entirely."""

    config_id: str
    target_difficulty: Optional[str]
    domain_key: Optional[str]
    narrative_style: Optional[str]
    isolates: str

    def request_intents(self) -> Dict[str, str]:
        """The intent fields of the generation request; ``additionalInstructions`` stays empty throughout."""
        intents: Dict[str, str] = {}
        if self.target_difficulty:
            intents["targetDifficulty"] = self.target_difficulty
        if self.domain_key:
            intents["domainText"] = DOMAIN_TEXTS[self.domain_key]
        if self.narrative_style:
            intents["narrativeStyle"] = self.narrative_style
        return intents


CONFIGURATIONS: List[Configuration] = [
    Configuration("C1", "EASY", None, None, "difficulty down, nothing else"),
    Configuration("C2", "HARD", None, None, "difficulty up, nothing else"),
    Configuration("C3", None, "D-supplied", None, "domain change where the domain supplies the ordering key"),
    Configuration("C4", None, "D-unspecified", None, "domain change where the model must choose the ordering key"),
    Configuration("C5", None, None, "TECHNICAL", "narrative only, step 1 of the scale"),
    Configuration("C6", None, None, "REALISTIC", "narrative only, step 2"),
    Configuration("C7", None, None, "CREATIVE", "narrative only, step 3"),
    Configuration("C8", None, None, "IMAGINATIVE", "narrative only, step 4 — no domain, triggers the planner's Greek-mythology fallback"),
    Configuration("C9", None, "D-supplied", "TECHNICAL", "narrative scale, step 1, with a domain to build on"),
    Configuration("C10", None, "D-supplied", "REALISTIC", "narrative scale, step 2, with a domain to build on"),
    Configuration("C11", None, "D-supplied", "CREATIVE", "narrative scale, step 3, with a domain to build on"),
    Configuration("C12", None, "D-supplied", "IMAGINATIVE", "narrative scale, step 4, with a domain to build on"),
    Configuration("C13", None, "D-unspecified", "IMAGINATIVE", "the hard combination: unspecified ordering key, strongest narrative"),
    Configuration("C14", "HARD", "D-unspecified", "CREATIVE", "stacked intents, the stress case"),
]

CONFIGURATIONS_BY_ID: Dict[str, Configuration] = {configuration.config_id: configuration for configuration in CONFIGURATIONS}

EXERCISE_TYPES = ("programming", "quiz")

# If the schedule forces whole configurations out rather than rounds, these go first, from both halves:
# one complete narrative sweep is worth more than two partial ones, and C9-C12 is the sweep to protect.
# It holds the domain fixed at D-supplied, so narrative strength is the only variable across its four
# steps; C5-C8 confounds its top step, because IMAGINATIVE with no domain triggers the planner's
# mythology fallback and changes theme and strength at once. C8's fallback is a curiosity, not a result.
# This must stay consistent with the rubric's deep sweep (rubric.md): read most closely what is dropped
# last, never the reverse.
DROP_FIRST = ("C5", "C6", "C7", "C8")


def run_id(exercise_type: str, config_id: str, round_number: int) -> str:
    return f"{exercise_type}-{config_id}-r{round_number}"
