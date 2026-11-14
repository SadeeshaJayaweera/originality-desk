package com.writingassistant.identity.saml;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Validated SAML 2.0 Assertion claims and attributes.
 */
public class SamlAssertionClaims {
    private final String subjectNameId;
    private final String nameIdFormat;
    private final String issuer;
    private final String email;
    private final String displayName;
    private final String givenName;
    private final String surname;
    private final List<String> affiliations;
    private final Map<String, List<String>> allAttributes;

    public SamlAssertionClaims(String subjectNameId,
                               String nameIdFormat,
                               String issuer,
                               String email,
                               String displayName,
                               String givenName,
                               String surname,
                               List<String> affiliations,
                               Map<String, List<String>> allAttributes) {
        this.subjectNameId = subjectNameId;
        this.nameIdFormat = nameIdFormat;
        this.issuer = issuer;
        this.email = email;
        this.displayName = displayName;
        this.givenName = givenName;
        this.surname = surname;
        this.affiliations = affiliations != null ? Collections.unmodifiableList(affiliations) : Collections.emptyList();
        this.allAttributes = allAttributes != null ? Collections.unmodifiableMap(allAttributes) : Collections.emptyMap();
    }

    public String getSubjectNameId() { return subjectNameId; }
    public String getNameIdFormat() { return nameIdFormat; }
    public String getIssuer() { return issuer; }
    public String getEmail() { return email; }
    public String getDisplayName() {
        if (displayName != null && !displayName.isBlank()) return displayName;
        if (givenName != null || surname != null) {
            String g = givenName != null ? givenName : "";
            String s = surname != null ? surname : "";
            return (g + " " + s).trim();
        }
        return email != null ? email : subjectNameId;
    }
    public String getGivenName() { return givenName; }
    public String getSurname() { return surname; }
    public List<String> getAffiliations() { return affiliations; }
    public Map<String, List<String>> getAllAttributes() { return allAttributes; }

    public List<String> getAttribute(String name) {
        return allAttributes.getOrDefault(name, Collections.emptyList());
    }

    public String getFirstAttribute(String name) {
        List<String> vals = getAttribute(name);
        return vals.isEmpty() ? null : vals.get(0);
    }
}
