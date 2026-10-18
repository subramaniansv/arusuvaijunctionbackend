package com.ecommerce.app.module.banner;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * A greeting postcard created by a visitor on the /festival page.
 * The system generates a short unique share_slug so the message can be
 * distributed as:  /festival?msg=<shareSlug>
 *
 * The postcard carries three text fields — TO (recipient), MESSAGE, and
 * FROM (sender) — exactly like a physical postcard. The creator also picks
 * a specific background image (image_id). The public share response
 * hydrates that exact image; if it's missing/disabled we fall back to a
 * random active image from the same festival.
 */
public class FestivalMessage {

    private UUID          messageId;
    private UUID          festivalId;
    private UUID          imageId;         // the bg image the creator chose (nullable)
    private String        festivalName;    // joined
    private String        festivalSlug;    // joined
    private String        recipientName;   // "TO" line on the card (optional)
    private String        messageText;     // the greeting body
    private String        senderName;      // "FROM" line on the card (optional)
    private String        shareSlug;       // short random ID, e.g. "k3pX9mQz"
    private boolean       isActive;
    private Timestamp     createdAt;
    private UUID          createdBy;

    /** Hydrated at read time — the chosen (or fallback) background image. */
    private FestivalImage backgroundImage;

    public UUID          getMessageId()                   { return messageId; }
    public void          setMessageId(UUID v)             { this.messageId = v; }

    public UUID          getFestivalId()                  { return festivalId; }
    public void          setFestivalId(UUID v)            { this.festivalId = v; }

    public UUID          getImageId()                     { return imageId; }
    public void          setImageId(UUID v)               { this.imageId = v; }

    public String        getFestivalName()                { return festivalName; }
    public void          setFestivalName(String v)        { this.festivalName = v; }

    public String        getFestivalSlug()                { return festivalSlug; }
    public void          setFestivalSlug(String v)        { this.festivalSlug = v; }

    public String        getRecipientName()               { return recipientName; }
    public void          setRecipientName(String v)       { this.recipientName = v; }

    public String        getMessageText()                 { return messageText; }
    public void          setMessageText(String v)         { this.messageText = v; }

    public String        getSenderName()                  { return senderName; }
    public void          setSenderName(String v)          { this.senderName = v; }

    public String        getShareSlug()                   { return shareSlug; }
    public void          setShareSlug(String v)           { this.shareSlug = v; }

    @JsonProperty("isActive")
    public boolean       isActive()                       { return isActive; }

    @JsonProperty("isActive")
    public void          setActive(boolean v)             { this.isActive = v; }

    public Timestamp     getCreatedAt()                   { return createdAt; }
    public void          setCreatedAt(Timestamp v)        { this.createdAt = v; }

    public UUID          getCreatedBy()                   { return createdBy; }
    public void          setCreatedBy(UUID v)             { this.createdBy = v; }

    public FestivalImage getBackgroundImage()             { return backgroundImage; }
    public void          setBackgroundImage(FestivalImage v) { this.backgroundImage = v; }
}
