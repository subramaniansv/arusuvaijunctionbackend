package com.ecommerce.app.module.push;

import java.util.UUID;

/** One browser/device subscription belonging to a logged-in user. */
public class PushSubscription {
    private UUID id;
    private UUID userId;
    private String endpoint;
    private String p256dh;   // base64url, 65-byte uncompressed EC point
    private String auth;     // base64url, 16-byte shared secret
    private String userAgent;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }
    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
