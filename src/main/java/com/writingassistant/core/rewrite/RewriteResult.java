package com.writingassistant.core.rewrite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result container for rewrite operations containing candidate alternatives and mode metadata.
 */
public class RewriteResult {

    private final String originalText;
    private final RewriteMode mode;
    private final List<String> alternatives;
    private final List<RewriteOption> options;

    public RewriteResult(String originalText, RewriteMode mode, List<String> alternatives) {
        this.originalText = originalText != null ? originalText : "";
        this.mode = mode != null ? mode : RewriteMode.CLARITY;
        this.alternatives = alternatives != null ? List.copyOf(alternatives) : Collections.emptyList();

        List<RewriteOption> opts = new ArrayList<>();
        for (String alt : this.alternatives) {
            opts.add(new RewriteOption(alt, mode.getDisplayName()));
        }
        this.options = Collections.unmodifiableList(opts);
    }

    public static RewriteResult ofOptions(String originalText, RewriteMode mode, List<RewriteOption> options) {
        List<String> alts = new ArrayList<>();
        if (options != null) {
            for (RewriteOption opt : options) {
                if (opt != null && opt.getText() != null) {
                    alts.add(opt.getText());
                }
            }
        }
        return new RewriteResult(originalText, mode, alts);
    }

    public String getOriginalText() {
        return originalText;
    }

    public RewriteMode getMode() {
        return mode;
    }

    /**
     * Returns the clean list of alternative rewritten strings.
     */
    public List<String> getAlternatives() {
        return alternatives;
    }

    /**
     * Returns structured RewriteOption objects.
     */
    public List<RewriteOption> getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return "RewriteResult{" +
                "mode=" + mode +
                ", alternativesCount=" + alternatives.size() +
                '}';
    }
}
