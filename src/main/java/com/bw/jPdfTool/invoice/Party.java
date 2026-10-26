package com.bw.jPdfTool.invoice;

/**
 * Seller or buyer of an invoice. Text fields are null or blank if not set.
 */
public class Party {
    public String name;
    public String street;
    public String zip;
    public String city;
    public String country = "DE";
    public String vatId;
    public String taxId;
    public String email;
    /**
     * Contact person and phone, only used for the seller. XRechnung requires them.
     */
    public String contactName;
    public String phone;
    /**
     * Bank account, only used for the seller (the account the buyer pays to).
     */
    public String iban;
    public String bic;
    public String accountHolder;

    /**
     * Only for presets: in which preset lists of the invoice editor the party is offered.
     */
    public boolean asSeller = true;
    public boolean asBuyer = true;

    /**
     * Checks if the preset is offered for this role.
     */
    public boolean hasRole(Role role) {
        return role == Role.SELLER ? asSeller : asBuyer;
    }

    public void setRole(Role role, boolean enabled) {
        if (role == Role.SELLER)
            asSeller = enabled;
        else
            asBuyer = enabled;
    }

    /**
     * Copies all values of the other party into this one.
     */
    public void copyFrom(Party o) {
        asSeller = o.asSeller;
        asBuyer = o.asBuyer;
        name = o.name;
        street = o.street;
        zip = o.zip;
        city = o.city;
        country = o.country;
        vatId = o.vatId;
        taxId = o.taxId;
        email = o.email;
        contactName = o.contactName;
        phone = o.phone;
        iban = o.iban;
        bic = o.bic;
        accountHolder = o.accountHolder;
    }

    public Party copy() {
        Party p = new Party();
        p.copyFrom(this);
        return p;
    }
}
