package com.bw.jPdfTool.invoice;

/**
 * Role of a party in an invoice. The presets in {@link InvoiceData#partiesFile()} are one list,
 * each entry says in which roles it is offered (see {@link Party#hasRole(Role)}).
 */
public enum Role {
    SELLER("Seller"),
    BUYER("Buyer");

    public final String title;

    Role(String title) {
        this.title = title;
    }
}
