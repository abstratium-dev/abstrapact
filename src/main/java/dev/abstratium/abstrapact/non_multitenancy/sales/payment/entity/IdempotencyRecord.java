package dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Operational record for deduplicating mutating REST requests.
 *
 * <p>When a client sends a mutating request (e.g. {@code POST /accept}) it includes an
 * {@code Idempotency-Key} header. The server caches the result (status code + body) keyed by
 * that header. Retries with the same key replay the cached result without re-executing the
 * business logic.
 *
 * <p>No Envers audit table is needed — this is operational data, not business data.
 *
 * <p>See {@code docs/DESIGN_OF_IDEMPOTENCY.md}.
 */
@Entity
@Table(name = "T_idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "idempotency_key", length = 255, nullable = false)
    private String key;

    @Column(nullable = false, length = 50)
    private String scope;

    @Column(name = "scope_id", length = 36)
    private String scopeId;

    @Column(name = "request_fingerprint", length = 64, nullable = false)
    private String requestFingerprint;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
