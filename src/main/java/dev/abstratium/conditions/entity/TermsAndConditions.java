package dev.abstratium.conditions.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.time.LocalDate;

@Entity
@Table(name = "T_terms_and_conditions")
@Audited
public class TermsAndConditions {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "content_fr", columnDefinition = "TEXT")
    private String contentFr;

    @Column(name = "content_de", columnDefinition = "TEXT")
    private String contentDe;

    @Column(name = "content_en", columnDefinition = "TEXT")
    private String contentEn;

    @Column(name = "current_version", length = 50)
    private String currentVersion;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    public TermsAndConditions() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentFr() {
        return contentFr;
    }

    public void setContentFr(String contentFr) {
        this.contentFr = contentFr;
    }

    public String getContentDe() {
        return contentDe;
    }

    public void setContentDe(String contentDe) {
        this.contentDe = contentDe;
    }

    public String getContentEn() {
        return contentEn;
    }

    public void setContentEn(String contentEn) {
        this.contentEn = contentEn;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveUntil() {
        return effectiveUntil;
    }

    public void setEffectiveUntil(LocalDate effectiveUntil) {
        this.effectiveUntil = effectiveUntil;
    }
}
