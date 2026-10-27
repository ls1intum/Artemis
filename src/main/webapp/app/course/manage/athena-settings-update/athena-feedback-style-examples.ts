/** One feedback item as Athena's text module returns it for a submission. */
export interface AthenaExampleFeedback {
    title: string;
    description: string;
    credits: number;
    /** The sentence of the submission the feedback is attached to. */
    reference: string;
}

/** A sample submission and, per slider and level (1-3), a feedback item Athena gave on it with that course default. */
export interface AthenaFeedbackStyleExamples {
    submission: string;
    defaultFeedbackDetail: Record<number, AthenaExampleFeedback>;
    defaultFeedbackFormality: Record<number, AthenaExampleFeedback>;
}

/**
 * Example feedback for the feedback style sliders on the Athena settings page, per language.
 *
 * Real Athena output rather than hand-written text: a text exercise was set up with a problem statement, example
 * solution and grading instructions, a student without feedback preferences of their own submitted the answer below,
 * and feedback was requested once per level with the course default set to it, keeping the other slider at neutral.
 * Athena returned a few items per request; each level keeps the one it credited least, which is where the level shows
 * best. That item points at the last sentence for every level but the English "detailed" one, whose run attached its
 * items to the earlier sentences. The item is kept as Athena returned it: its credits, and the "Next step" part of the
 * description in English even for the German submission, since that is how the text module writes it.
 */
export const ATHENA_FEEDBACK_STYLE_EXAMPLES: Readonly<Record<string, AthenaFeedbackStyleExamples>> = {
    en: {
        submission:
            'Agile teams welcome changing requirements. They deliver working software in short iterations, so the customer can give feedback early. This helps developers find a better solution.',
        defaultFeedbackDetail: {
            1: {
                title: 'Justification Needs Improvement',
                description:
                    'The justification for why early feedback leads to a better product is vague and does not clearly connect to the benefits.\n\nNext step: Provide a more specific explanation of how early feedback improves the final product.',
                credits: 2,
                reference: 'This helps developers find a better solution.',
            },
            2: {
                title: 'Justification for early feedback needs improvement',
                description:
                    'While you touched on the benefits of early feedback, the justification provided is vague. It lacks a clear connection to why early feedback directly leads to a better product. This area requires further development.\n\nNext step: Revise your explanation to explicitly connect early feedback with improved outcomes in product development.',
                credits: 2,
                reference: 'This helps developers find a better solution.',
            },
            3: {
                title: 'Justification of Agile Benefits',
                description:
                    "While you correctly point out that delivering working software allows for early customer feedback, your response does not fully explore the justification of why this approach leads to improved results. It's important to articulate how early feedback can prevent misunderstandings and misaligned efforts, ensuring that the development aligns closely with user expectations. This could be a powerful argument in favor of agile methods.\n\nNext step: Review the concept of iterative feedback in agile methodologies, focusing on how this practice helps to refine requirements and correct course during development. Consider including examples or scenarios where early feedback has significantly improved project outcomes.",
                credits: 3,
                reference: 'They deliver working software in short iterations, so the customer can give feedback early.',
            },
        },
        defaultFeedbackFormality: {
            1: {
                title: 'Insufficient Justification for Early Feedback Benefits',
                description:
                    "Your justification for why early feedback leads to better products is unclear. The statement made does not adequately support the concept, which affects the overall strength of your argument.\n\nNext step: Revise your explanation to provide a more detailed analysis of how early feedback specifically contributes to improving the product's quality.",
                credits: 2,
                reference: 'This helps developers find a better solution.',
            },
            2: {
                title: 'Justification for early feedback needs improvement',
                description:
                    'While you touched on the benefits of early feedback, the justification provided is vague. It lacks a clear connection to why early feedback directly leads to a better product. This area requires further development.\n\nNext step: Revise your explanation to explicitly connect early feedback with improved outcomes in product development.',
                credits: 2,
                reference: 'This helps developers find a better solution.',
            },
            3: {
                title: 'A bit more justification needed for feedback impact 🤔',
                description:
                    "You've made a good attempt at discussing the importance of early feedback, but the reasoning could be stronger. The statement about finding a 'better solution' is a bit vague, and it would be beneficial to explain how early feedback specifically enhances the product. Don't worry; this is a great opportunity for growth! 🌱\n\nNext step: Try to provide specific examples of how early feedback leads to improvements in the final product.",
                credits: 2,
                reference: 'This helps developers find a better solution.',
            },
        },
    },
    de: {
        submission:
            'Agile Teams begrüßen sich ändernde Anforderungen. Sie liefern in kurzen Iterationen funktionierende Software, sodass der Kunde früh Feedback geben kann. Das hilft den Entwicklern, eine bessere Lösung zu finden.',
        defaultFeedbackDetail: {
            1: {
                title: 'Begründung für frühes Feedback unvollständig',
                description:
                    'Die Begründung, warum frühes Feedback zu besseren Ergebnissen führt, ist vorhanden, jedoch nicht ausreichend detailliert.\n\nNext step: Erweitere deine Erklärung, um detaillierter darzustellen, wie frühes Feedback die Produktentwicklung verbessert.',
                credits: 2,
                reference: 'Das hilft den Entwicklern, eine bessere Lösung zu finden.',
            },
            2: {
                title: 'Begründung für besseres Produkt benötigt Verbesserung',
                description:
                    'Die Aussage über die Verbesserung der Lösung durch Feedback ist unvollständig. Es fehlt die klare Verbindung zwischen dem frühzeitigen Feedback und der Qualität des Endprodukts.\n\nNext step: Überlege, wie du die Verbindung zwischen frühem Feedback und einem besseren Produkt klarer darlegen kannst.',
                credits: 2,
                reference: 'Das hilft den Entwicklern, eine bessere Lösung zu finden.',
            },
            3: {
                title: 'Begründung für frühes Feedback benötigt Verbesserung',
                description:
                    'In deinem aktuellen Ansatz fehlt eine klare Begründung, warum frühes Feedback zu einem besseren Produkt führt. Deine Aussage, dass es den Entwicklern hilft, eine bessere Lösung zu finden, ist zwar ein Schritt in die richtige Richtung, aber sie bleibt vage und könnte durch spezifische Beispiele oder Argumente verstärkt werden.\n\nNext step: Überlege, wie du die Vorteile des frühen Feedbacks genauer darlegen kannst. Zum Beispiel könntest du diskutieren, wie frühes Feedback hilft, Missverständnisse zu klären oder unerwartete Anforderungen zu identifizieren, was letztlich zu einem Produkt führt, das besser auf die tatsächlichen Bedürfnisse der Nutzer abgestimmt ist.',
                credits: 2,
                reference: 'Das hilft den Entwicklern, eine bessere Lösung zu finden.',
            },
        },
        defaultFeedbackFormality: {
            1: {
                title: 'Unzureichende Begründung für besseres Produkt durch frühes Feedback',
                description:
                    'Ihre Erklärung, dass frühes Feedback den Entwicklern hilft, eine bessere Lösung zu finden, bleibt vage und liefert keine detaillierte Begründung dafür, warum dies zu einem besseren Produkt führt. Es ist wichtig, klar zu formulieren, wie frühes Feedback spezifisch zur Verbesserung des Endprodukts beiträgt.\n\nNext step: Überarbeiten Sie Ihre Erklärung, um konkrete Beispiele oder Argumente zu integrieren, die aufzeigen, wie frühes Feedback die Produktqualität verbessert.',
                credits: 2,
                reference: 'Das hilft den Entwicklern, eine bessere Lösung zu finden.',
            },
            2: {
                title: 'Begründung für besseres Produkt benötigt Verbesserung',
                description:
                    'Die Aussage über die Verbesserung der Lösung durch Feedback ist unvollständig. Es fehlt die klare Verbindung zwischen dem frühzeitigen Feedback und der Qualität des Endprodukts.\n\nNext step: Überlege, wie du die Verbindung zwischen frühem Feedback und einem besseren Produkt klarer darlegen kannst.',
                credits: 2,
                reference: 'Das hilft den Entwicklern, eine bessere Lösung zu finden.',
            },
            3: {
                title: 'Verbesserungspotenzial bei der Begründung 😊',
                description:
                    'Du hast zwar erwähnt, dass frühes Feedback den Entwicklern hilft, eine bessere Lösung zu finden, aber die Begründung könnte klarer sein. Es wäre hilfreich, genauer zu formulieren, wie dieses Feedback konkret zu besseren Ergebnissen führt. Das ist ein wichtiger Punkt! 😊\n\nNext step: Überlege dir, wie frühes Feedback dazu beiträgt, Missverständnisse zu vermeiden und Anpassungen rechtzeitig vorzunehmen. Das wird deine Argumentation stärken! 🚀',
                credits: 2,
                reference: 'Das hilft den Entwicklern, eine bessere Lösung zu finden.',
            },
        },
    },
};
