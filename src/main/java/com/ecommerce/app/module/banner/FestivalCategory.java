package com.ecommerce.app.module.banner;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Festival category — Pongal, Diwali, Onam, Christmas, Eid, General …
 * Admin manages this list; slug is used in shareable URLs.
 *
 * @JsonProperty("isActive") forces Jackson to serialise the boolean field
 * as "isActive" (not "active") so the frontend receives the exact key
 * it reads: f.isActive.
 */
public class FestivalCategory {

    private UUID      festivalId;
    private String    name;
    private String    slug;
    private int       displayOrder;
    private boolean   isActive;
    private Timestamp createdAt;

    public UUID      getFestivalId()           { return festivalId; }
    public void      setFestivalId(UUID v)     { this.festivalId = v; }

    public String    getName()                 { return name; }
    public void      setName(String v)         { this.name = v; }

    public String    getSlug()                 { return slug; }
    public void      setSlug(String v)         { this.slug = v; }

    public int       getDisplayOrder()         { return displayOrder; }
    public void      setDisplayOrder(int v)    { this.displayOrder = v; }

    @JsonProperty("isActive")
    public boolean   isActive()                { return isActive; }

    @JsonProperty("isActive")
    public void      setActive(boolean v)      { this.isActive = v; }

    public Timestamp getCreatedAt()            { return createdAt; }
    public void      setCreatedAt(Timestamp v) { this.createdAt = v; }
}
