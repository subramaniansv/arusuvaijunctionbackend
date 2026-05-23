package com.ecommerce.app.module.iam.models;

import java.util.UUID;

public class RefreshToken {
    private Long id;
    private String tokenHash;
    private UUID userId;
    private String ipAddress;
    private String userAgent;
    private long expiredAt;
    private long createdAt;
    private boolean revoked;
    
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getTokenHash() {
        return tokenHash;
    }
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
    public UUID getUserId() {
        return userId;
    }
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    public String getIpAddress() {
        return ipAddress;
    }
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    public String getUserAgent() {
        return userAgent;
    }
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    public long getexpiredAt() {
        return expiredAt;
    }
    public void setexpiredAt(long expiredAt) {
        this.expiredAt = expiredAt;
    }
    public long getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    public boolean isRevoked() {
        return revoked;
    }
    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }
}
