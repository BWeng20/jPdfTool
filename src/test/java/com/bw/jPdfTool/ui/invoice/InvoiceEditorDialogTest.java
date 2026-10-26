package com.bw.jPdfTool.ui.invoice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

class InvoiceEditorDialogTest {

    @Test
    void parseDate() {
        var de = InvoiceEditorDialog.dateFormatter(Locale.GERMANY, true);
        LocalDate expected = LocalDate.of(2024, 10, 1);
        Assertions.assertEquals(expected, InvoiceEditorDialog.parseDate("01.10.2024", de));
        Assertions.assertEquals(expected, InvoiceEditorDialog.parseDate("1.10.2024", de));
        Assertions.assertEquals(expected, InvoiceEditorDialog.parseDate("2024-10-01", de), "ISO always works");
        Assertions.assertEquals(expected, InvoiceEditorDialog.parseDate("20241001", de));
        Assertions.assertNull(InvoiceEditorDialog.parseDate("32.10.2024", de));
        Assertions.assertNull(InvoiceEditorDialog.parseDate("tomorrow", de));

        var us = InvoiceEditorDialog.dateFormatter(Locale.US, true);
        Assertions.assertEquals(expected, InvoiceEditorDialog.parseDate("10/1/2024", us));
    }

    @Test
    void displayDate() {
        LocalDate d = LocalDate.of(2024, 10, 1);
        Assertions.assertEquals("01.10.2024", InvoiceEditorDialog.dateFormatter(Locale.GERMANY, false).format(d));
        Assertions.assertEquals("10/1/2024", InvoiceEditorDialog.dateFormatter(Locale.US, false).format(d));
        Assertions.assertEquals("dd.MM.yyyy", InvoiceEditorDialog.datePattern(Locale.GERMANY));
    }

    @Test
    void parseDecimal() {
        Assertions.assertEquals(new BigDecimal("12.5"), InvoiceEditorDialog.parseDecimal("12,5", Locale.GERMANY));
        Assertions.assertEquals(new BigDecimal("12.5"), InvoiceEditorDialog.parseDecimal("12.5", Locale.GERMANY), "dot works too");
        Assertions.assertEquals(new BigDecimal("12.5"), InvoiceEditorDialog.parseDecimal("12.5", Locale.US));
        Assertions.assertEquals(new BigDecimal("19"), InvoiceEditorDialog.parseDecimal("19 %", Locale.GERMANY));
        Assertions.assertNull(InvoiceEditorDialog.parseDecimal("  ", Locale.GERMANY));
        Assertions.assertThrows(NumberFormatException.class, () -> InvoiceEditorDialog.parseDecimal("1.234,5", Locale.GERMANY));
        Assertions.assertThrows(NumberFormatException.class, () -> InvoiceEditorDialog.parseDecimal("abc", Locale.US));
    }

    @Test
    void formatDecimal() {
        Assertions.assertEquals("1234,50", InvoiceEditorDialog.formatDecimal(new BigDecimal("1234.5"), 2, Locale.GERMANY));
        Assertions.assertEquals("8", InvoiceEditorDialog.formatDecimal(new BigDecimal("8.000"), 0, Locale.US));
        Assertions.assertEquals("0.125", InvoiceEditorDialog.formatDecimal(new BigDecimal("0.125"), 2, Locale.US));
    }
}
