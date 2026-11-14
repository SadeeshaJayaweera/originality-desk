package com.writingassistant.identity.scim;

import java.io.Serializable;
import java.util.Objects;

/**
 * SCIM 2.0 Name representation according to RFC 7643 Section 4.1.1.
 */
public class ScimName implements Serializable {
    private String formatted;
    private String familyName;
    private String givenName;

    public ScimName() {}

    public ScimName(String formatted, String familyName, String givenName) {
        this.formatted = formatted;
        this.familyName = familyName;
        this.givenName = givenName;
        if (this.formatted == null && (this.givenName != null || this.familyName != null)) {
            this.formatted = ((this.givenName != null ? this.givenName : "") + " "
                    + (this.familyName != null ? this.familyName : "")).trim();
        }
    }

    public String getFormatted() { return formatted; }
    public void setFormatted(String formatted) { this.formatted = formatted; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public String getGivenName() { return givenName; }
    public void setGivenName(String givenName) { this.givenName = givenName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScimName scimName)) return false;
        return Objects.equals(formatted, scimName.formatted)
                && Objects.equals(familyName, scimName.familyName)
                && Objects.equals(givenName, scimName.givenName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(formatted, familyName, givenName);
    }
}
