package com.ecommerce.app.module.banner;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Background image for a festival category.
 * Stored in the same bucket as product images (festival/ prefix).
 * Admin can upload many per festival and disable stale ones.
 */
public class FestivalImage {

    private UUID      imageId;
    private UUID      festivalId;
    private String    festivalName;   // joined — handy for responses
    private String    festivalSlug;   // joined
    private String    imageUrl;       // CDN public URL
    private String    objectKey;      // storage key (same bucket as product imgs)
    private String    label;          // optional admin note e.g. "2025 Pongal hero"
    private int       displayOrder;
    private boolean   isActive;
    private Timestamp createdAt;
    private UUID      createdBy;

    public UUID      getImageId()              { return imageId; }
    public void      setImageId(UUID v)        { this.imageId = v; }

    public UUID      getFestivalId()           { return festivalId; }
    public void      setFestivalId(UUID v)     { this.festivalId = v; }

    public String    getFestivalName()         { return festivalName; }
    public void      setFestivalName(String v) { this.festivalName = v; }

    public String    getFestivalSlug()         { return festivalSlug; }
    public void      setFestivalSlug(String v) { this.festivalSlug = v; }

    public String    getImageUrl()             { return imageUrl; }
    public void      setImageUrl(String v)     { this.imageUrl = v; }

    public String    getObjectKey()            { return objectKey; }
    public void      setObjectKey(String v)    { this.objectKey = v; }

    public String    getLabel()                { return label; }
    public void      setLabel(String v)        { this.label = v; }

    public int       getDisplayOrder()         { return displayOrder; }
    public void      setDisplayOrder(int v)    { this.displayOrder = v; }

    @JsonProperty("isActive")
    public boolean   isActive()                { return isActive; }

    @JsonProperty("isActive")
    public void      setActive(boolean v)      { this.isActive = v; }

    public Timestamp getCreatedAt()            { return createdAt; }
    public void      setCreatedAt(Timestamp v) { this.createdAt = v; }

    public UUID      getCreatedBy()            { return createdBy; }
    public void      setCreatedBy(UUID v)      { this.createdBy = v; }
}
