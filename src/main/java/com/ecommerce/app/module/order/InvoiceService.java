package com.ecommerce.app.module.order;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;

/**
 * Builds a downloadable PDF invoice for an {@link Order} entirely on the
 * server. All figures (line prices, quantities, totals, shipping address)
 * come from the Order that the caller fetched from the database via
 * {@link OrderService#getOrderById} - never from client-supplied input - so
 * the document cannot be tampered with from the frontend.
 *
 * The seller address is fixed company data, hardcoded here on the server.
 * The logo is loaded from the bundled classpath resource
 * {@code /invoice/favicon.png}.
 */
public class InvoiceService {
    private static final Logger LOG = LoggerFactory.getLogger(InvoiceService.class);

    // ------------------------------------------------------------------
    // Seller (company) details - authoritative server-side constants.
    // ------------------------------------------------------------------
    private static final String COMPANY_NAME    = "Arusuvai Junction";
    private static final String COMPANY_OWNER   = "S. Vallinayagam";
    private static final String COMPANY_ADDR_1  = "6/A, Matha Middle Street";
    private static final String COMPANY_ADDR_2  = "Tirunelveli Town - 627006";
    private static final String COMPANY_PHONE   = "9894014063 / 9843471463";
    private static final String LOGO_RESOURCE   = "/invoice/logo.png";

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    // Brand palette (kept close to the storefront's green accent).
    private static final Color BRAND   = new Color(0x1f, 0x6f, 0x4a);
    private static final Color INK     = new Color(0x1a, 0x1a, 0x1a);
    private static final Color MUTED   = new Color(0x8a, 0x8a, 0x8a);
    private static final Color RULE    = new Color(0xec, 0xec, 0xec);

    /**
     * Render the invoice for the given order to PDF bytes.
     *
     * @param order a fully-hydrated order (with order items) owned by the
     *              requesting user; must be non-null.
     * @return the PDF document as a byte array.
     */
    public byte[] generate(Order order) {
        if (order == null || order.getOrderId() == null) {
            throw new IllegalArgumentException("order is required");
        }

        Document doc = new Document(PageSize.A4, 48, 48, 48, 56);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            addHeader(doc, order);
            addParties(doc, order);
            addItemsTable(doc, order);
            addTotals(doc, order);
            addFooter(doc);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            LOG.error("failed to build invoice pdf for order {}", order.getOrderId(), e);
            throw new RuntimeException("could not generate invoice", e);
        }
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------
    private void addHeader(Document doc, Order order) throws Exception {
        PdfPTable header = new PdfPTable(new float[] { 2f, 1.4f });
        header.setWidthPercentage(100);

        // Left: logo + company block.
        PdfPCell left = borderless();
        Image logo = loadLogo();
        if (logo != null) {
            // The badge already carries the brand name (Tamil + English), so
            // we let the mark stand on its own and only print the address.
            logo.scaleToFit(74, 74);
            Paragraph logoPara = new Paragraph();
            logoPara.add(new Chunk(logo, 0, 0, true));
            logoPara.setSpacingAfter(6);
            left.addElement(logoPara);
        } else {
            left.addElement(new Paragraph(COMPANY_NAME, font(16, Font.BOLD, BRAND)));
        }
        left.addElement(new Paragraph(COMPANY_OWNER, font(10.5f, Font.BOLD, INK)));
        left.addElement(new Paragraph(COMPANY_ADDR_1, font(9.5f, Font.NORMAL, MUTED)));
        left.addElement(new Paragraph(COMPANY_ADDR_2, font(9.5f, Font.NORMAL, MUTED)));
        left.addElement(new Paragraph("Ph: " + COMPANY_PHONE, font(9.5f, Font.NORMAL, MUTED)));
        header.addCell(left);

        // Right: invoice meta.
        PdfPCell right = borderless();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph("INVOICE", font(20, Font.BOLD, INK));
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
        right.addElement(rightLine("Invoice No: " + shortId(order)));
        right.addElement(rightLine("Date: " + formatDate(order)));
        right.addElement(rightLine("Status: " + safe(order.getStatus() == null ? "" : order.getStatus().name())));
        header.addCell(right);

        doc.add(header);
        doc.add(spacer(14));
        doc.add(rule());
        doc.add(spacer(12));
    }

    private void addParties(Document doc, Order order) throws Exception {
        PdfPTable t = new PdfPTable(new float[] { 1f, 1f });
        t.setWidthPercentage(100);

        PdfPCell billTo = borderless();
        billTo.addElement(new Paragraph("BILL TO", font(8.5f, Font.BOLD, MUTED)));
        billTo.addElement(new Paragraph(safe(order.getShippingAddress()), font(10.5f, Font.NORMAL, INK)));
        if (order.getPhone() != null && !order.getPhone().isBlank()) {
            billTo.addElement(new Paragraph("Phone: " + order.getPhone(), font(10, Font.NORMAL, INK)));
        }
        t.addCell(billTo);

        PdfPCell ref = borderless();
        ref.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph refHead = new Paragraph("ORDER REFERENCE", font(8.5f, Font.BOLD, MUTED));
        refHead.setAlignment(Element.ALIGN_RIGHT);
        ref.addElement(refHead);
        ref.addElement(rightLine(safe(order.getOrderId().toString())));
        t.addCell(ref);

        doc.add(t);
        doc.add(spacer(16));
    }

    private void addItemsTable(Document doc, Order order) throws Exception {
        PdfPTable table = new PdfPTable(new float[] { 0.5f, 3.4f, 0.8f, 1.1f, 1.2f });
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        addHeadCell(table, "#", Element.ALIGN_LEFT);
        addHeadCell(table, "Item", Element.ALIGN_LEFT);
        addHeadCell(table, "Qty", Element.ALIGN_RIGHT);
        addHeadCell(table, "Price", Element.ALIGN_RIGHT);
        addHeadCell(table, "Amount", Element.ALIGN_RIGHT);

        List<OrderItem> items = order.getOrderItems();
        int i = 1;
        if (items != null) {
            for (OrderItem it : items) {
                double lineTotal = it.getPrice() * it.getQuantity();

                addBodyCell(table, String.valueOf(i++), Element.ALIGN_LEFT, false);

                // Item name (+ optional variant label on a second line).
                PdfPCell nameCell = bodyCell();
                Paragraph name = new Paragraph(safe(it.getProductName()), font(10, Font.NORMAL, INK));
                nameCell.addElement(name);
                if (it.getVariantLabel() != null && !it.getVariantLabel().isBlank()) {
                    nameCell.addElement(new Paragraph(it.getVariantLabel(), font(8.5f, Font.NORMAL, MUTED)));
                }
                table.addCell(nameCell);

                addBodyCell(table, String.valueOf(it.getQuantity()), Element.ALIGN_RIGHT, false);
                addBodyCell(table, money(it.getPrice()), Element.ALIGN_RIGHT, false);
                addBodyCell(table, money(lineTotal), Element.ALIGN_RIGHT, false);
            }
        }

        doc.add(table);
    }

    private void addTotals(Document doc, Order order) throws Exception {
        double shipping = order.getShippingFee();
        double subtotal = order.getTotalAmount() - shipping;

        PdfPTable wrap = new PdfPTable(new float[] { 1.6f, 1f });
        wrap.setWidthPercentage(100);
        wrap.addCell(borderless()); // empty left spacer

        PdfPTable totals = new PdfPTable(new float[] { 1.2f, 1f });
        totals.setWidthPercentage(100);
        totalsRow(totals, "Subtotal", money(subtotal), false);
        totalsRow(totals, "Shipping", shipping > 0 ? money(shipping) : "Free", false);
        totalsRow(totals, "Total", money(order.getTotalAmount()), true);

        PdfPCell totalsCell = borderless();
        totalsCell.addElement(totals);
        wrap.addCell(totalsCell);

        doc.add(spacer(6));
        doc.add(wrap);
    }

    private void addFooter(Document doc) throws Exception {
        doc.add(spacer(26));
        doc.add(rule());
        doc.add(spacer(8));
        Paragraph p = new Paragraph(
                "Thank you for shopping with " + COMPANY_NAME + ".\n"
                        + "This is a computer-generated invoice and does not require a signature.",
                font(8.5f, Font.NORMAL, MUTED));
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    /**
     * The logo never changes, so we parse the classpath PNG exactly once and
     * keep the decoded master {@link Image} in a static cache. Re-reading and
     * re-decoding a ~286 KB PNG on every invoice request was the dominant
     * per-request cost. {@code Image.getInstance(master)} returns a cheap copy
     * that shares the underlying raw image data but carries its own scaling
     * state, so each request can {@code scaleToFit(..)} independently without
     * mutating the shared master (thread-safe).
     */
    private static volatile Image cachedLogo;
    private static volatile boolean logoMissing;

    private Image loadLogo() {
        Image master = cachedLogo;
        if (master == null && !logoMissing) {
            synchronized (InvoiceService.class) {
                master = cachedLogo;
                if (master == null && !logoMissing) {
                    try (InputStream in = InvoiceService.class.getResourceAsStream(LOGO_RESOURCE)) {
                        if (in == null) {
                            LOG.warn("invoice logo resource {} not found on classpath", LOGO_RESOURCE);
                            logoMissing = true;
                            return null;
                        }
                        master = Image.getInstance(in.readAllBytes());
                        cachedLogo = master;
                    } catch (IOException | RuntimeException e) {
                        LOG.warn("could not load invoice logo: {}", e.getMessage());
                        logoMissing = true;
                        return null;
                    }
                }
            }
        }
        if (master == null) {
            return null;
        }
        try {
            // Cheap copy: shares raw image bytes, independent scaling state.
            return Image.getInstance(master);
        } catch (RuntimeException e) {
            LOG.warn("could not copy cached invoice logo: {}", e.getMessage());
            return null;
        }
    }

    private void totalsRow(PdfPTable t, String label, String value, boolean strong) {
        Font lf = strong ? font(11, Font.BOLD, INK) : font(10, Font.NORMAL, MUTED);
        Font vf = strong ? font(12, Font.BOLD, INK) : font(10, Font.NORMAL, INK);

        PdfPCell l = new PdfPCell(new Phrase(label, lf));
        l.setBorder(strong ? Rectangle.TOP : Rectangle.NO_BORDER);
        l.setBorderColor(INK);
        l.setPadding(5);

        PdfPCell v = new PdfPCell(new Phrase(value, vf));
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setBorder(strong ? Rectangle.TOP : Rectangle.NO_BORDER);
        v.setBorderColor(INK);
        v.setPadding(5);

        t.addCell(l);
        t.addCell(v);
    }

    private void addHeadCell(PdfPTable table, String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(8.5f, Font.BOLD, MUTED)));
        c.setHorizontalAlignment(align);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(INK);
        c.setBorderWidthBottom(1.2f);
        c.setPadding(7);
        table.addCell(c);
    }

    private void addBodyCell(PdfPTable table, String text, int align, boolean bold) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(10, bold ? Font.BOLD : Font.NORMAL, INK)));
        c.setHorizontalAlignment(align);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(RULE);
        c.setPadding(7);
        table.addCell(c);
    }

    private PdfPCell bodyCell() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(RULE);
        c.setPadding(7);
        return c;
    }

    private PdfPCell borderless() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0);
        return c;
    }

    private Paragraph rightLine(String text) {
        Paragraph p = new Paragraph(text, font(10, Font.NORMAL, INK));
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    private com.lowagie.text.pdf.PdfPTable rule() {
        com.lowagie.text.pdf.PdfPTable line = new com.lowagie.text.pdf.PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setFixedHeight(1f);
        c.setBackgroundColor(RULE);
        c.setBorder(Rectangle.NO_BORDER);
        line.addCell(c);
        return line;
    }

    private Font font(float size, int style, Color color) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, size, style);
        f.setColor(color);
        return f;
    }

    private String money(double amount) {
        return "Rs. " + String.format(Locale.ENGLISH, "%,.2f", amount);
    }

    private String formatDate(Order order) {
        if (order.getorderedAt() == null) return "";
        try {
            return DATE_FMT.format(order.getorderedAt().toLocalDateTime());
        } catch (RuntimeException e) {
            return order.getorderedAt().toString();
        }
    }

    private String shortId(Order order) {
        String id = order.getOrderId() == null ? "" : order.getOrderId().toString();
        return id.isEmpty() ? "" : id.substring(0, Math.min(8, id.length())).toUpperCase(Locale.ENGLISH);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
