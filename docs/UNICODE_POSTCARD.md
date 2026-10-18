# Tamil / Unicode-safe Postcard Messages

The festival "postcard" feature lets visitors write a message (often in Tamil),
store it, and share it via `/festival?msg=<slug>`. Tamil is outside Latin-1, so
every hop — request decode, DB storage, JSON response, and font rendering —
must be UTF-8 end to end. This document records the settings that keep Tamil
text intact instead of showing `?`, mojibake (`à®…`), or `□` tofu boxes.

## The end-to-end chain

| Hop | Risk if wrong | How it is handled |
|-----|---------------|-------------------|
| 1. HTTP request body | Servlet default decode is ISO-8859-1 → mojibake | `om.readValue(req.getInputStream(), …)` — Jackson detects the JSON charset (UTF-8) from the byte stream itself. **Do not** use `req.getReader()`, which applies the servlet's ISO-8859-1 default. |
| 2. JDBC / Postgres | Column not UTF-8 → stored `?` | Postgres default encoding is UTF-8; `message_text` is `TEXT`, `recipient_name`/`sender_name` are `VARCHAR`. The JDBC driver transmits Java `String` as UTF-8. No `characterEncoding` param needed for the pgjdbc driver. |
| 3. JSON response | Writer default ISO-8859-1 → `?` | `SendResponseUtil` sets `Content-Type: application/json;charset=UTF-8` **before** `getWriter()`. Once `getWriter()` is called the charset locks, so order matters. |
| 4. JVM runtime | Platform default charset on some hosts | `Dockerfile` sets `-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8` in `JAVA_TOOL_OPTIONS`. |
| 5. Build | Source files with literals compiled in platform charset | `pom.xml` sets `project.build.sourceEncoding=UTF-8`. |
| 6. Frontend decode | Browser mis-guesses charset | `index.html` and the SSR `middleware.js` both emit `<meta charset="UTF-8">`. The API is JSON with an explicit UTF-8 content-type, so fetch/axios decode correctly. |
| 7. Font rendering | Serif faces have **no Tamil glyphs** → tofu boxes | `Festival.css` postcard text uses a Tamil-capable fallback stack: `Georgia, "Times New Roman", "Noto Sans Tamil", "Latha", "Nirmala UI", "Tamil Sangam MN", serif`. Applied to `.pc`, `.pc__message`, `.pc__to`, `.pc__from-name`. |

## "It shows unicode chars / boxes on the frontend"

This symptom (characters render as boxes `□` or escape sequences) is almost
always hop **7 — font**, not encoding. The bytes arrive correctly as UTF-8, but
the CSS font (`Georgia`/serif) contains no Tamil glyphs, so the browser draws
`.notdef` tofu. The fix is the Tamil-capable font stack above. If you want a
guaranteed-consistent look across devices, self-host *Noto Sans Tamil* via
`@font-face` in `Festival.css` (same pattern as the existing `Amsterdam Four`
greeting font) and put it first in the stack.

If instead you see `?` or `à®…`-style mojibake, that is an **encoding** problem
at hops 1–4 above.

## PNG download note

`downloadPostcardPng()` serialises the live DOM into an SVG `<foreignObject>`
and rasterises it. Because it inlines the **computed** styles (including
`font-family`), Tamil renders in the PNG exactly as on screen — provided the
Tamil font is already loaded (`document.fonts.ready` is awaited before cloning).

## Security

Visitor message text is **user-supplied and unauthenticated** (`POST
/api/banner/admin/messages` is public). Protections:

- **Length / abuse bound** — `BannerController.clampUtf8()` trims and caps
  `messageText` (1000 code points), `recipientName`/`senderName` (120), always
  cutting on a code-point boundary so Tamil grapheme clusters are never split.
- **SQL injection** — all writes go through `PreparedStatement` parameters in
  `FestivalMessageRepository`; no string concatenation.
- **XSS** — the content is stored and served as JSON data and rendered by React
  as text (`{msg.messageText}`), which auto-escapes. There is no
  `dangerouslySetInnerHTML` on this path.
- **Slug** — share slugs are `SecureRandom` Base64-URL (≈281 trillion space),
  not guessable/sequential.
