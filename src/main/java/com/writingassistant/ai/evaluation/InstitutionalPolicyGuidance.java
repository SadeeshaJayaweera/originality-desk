package com.writingassistant.ai.evaluation;

import java.io.Serializable;
import java.util.List;

/**
 * Institutional policy framework and ethical guidance for academic integrity boards,
 * university administrators, and educators evaluating AI-writing technologies.
 */
public final class InstitutionalPolicyGuidance {

    private InstitutionalPolicyGuidance() {}

    public record PolicyPrinciple(
            String id,
            String title,
            String description,
            String institutionalMandate
    ) implements Serializable {}

    public static List<PolicyPrinciple> getCorePrinciples() {
        return List.of(
                new PolicyPrinciple(
                        "ETH-01",
                        "Presumption of Innocence and Due Process",
                        "Students are entitled to the fundamental presumption of academic honesty. "
                                + "A probabilistic algorithm or statistical text classifier does not possess forensic certainty.",
                        "No student conduct hearing or disciplinary referral may be initiated solely on the basis of an automated AI detection percentage."
                ),
                new PolicyPrinciple(
                        "ETH-02",
                        "Disparate Impact and Equal Protection for ESL Students",
                        "Empirical research demonstrates that students writing in English as an additional language "
                                + "produce lower perplexity writing that is misclassified by statistical detectors at rates exceeding 60%.",
                        "Faculty must cease using commercial detector scores against international or multilingual learners to prevent unlawful discrimination."
                ),
                new PolicyPrinciple(
                        "ETH-03",
                        "Protection of Neurodivergent Writers",
                        "Learners with autism spectrum disorders, ADHD, or learning differences frequently employ structured, "
                                + "literal, and formulaic rhetorical styles that algorithmic models falsely flag as artificial.",
                        "Writing style variations must not be weaponized into misconduct charges without substantiated behavioral evidence."
                ),
                new PolicyPrinciple(
                        "ETH-04",
                        "Transparent and Explainable Evidence",
                        "Academic integrity findings require tangible, verifiable evidence—such as side-by-side textual overlap "
                                + "with verifiable external literature—rather than opaque black-box probability estimates.",
                        "All reports produced by Originality Desk clearly separate objective textual evidence from statistical uncertainty indicators."
                )
        );
    }

    public static List<String> getRecommendedPedagogicalAlternatives() {
        return List.of(
                "Process-based writing portfolios requiring submission of preliminary brainstorms, outlines, and annotated bibliographies.",
                "Brief oral check-ins or viva voce discussions where students explain their thesis, argumentation, and research journey.",
                "Version-controlled drafting history in modern word processors demonstrating incremental revision over time.",
                "Discipline-specific prompts that connect theoretical course concepts with localized, personal, or classroom experiences.",
                "Transparent syllabus guidelines that explicitly define permissible and impermissible uses of generative AI tools."
        );
    }
}
