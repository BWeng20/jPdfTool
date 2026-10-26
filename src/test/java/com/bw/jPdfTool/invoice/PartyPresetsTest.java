package com.bw.jPdfTool.invoice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loading and saving the party presets of {@link InvoiceData}: one list for sellers and buyers.
 */
class PartyPresetsTest {

    @TempDir
    Path dir;

    @Test
    void file() {
        Assertions.assertEquals("parties.json", InvoiceData.partiesFile().getFileName().toString());
        Assertions.assertEquals(InvoiceData.settingsDirectory(), InvoiceData.partiesFile().getParent());
    }

    @Test
    void missingFileIsEmpty() throws Exception {
        Assertions.assertTrue(InvoiceData.loadParties(dir.resolve("parties.json")).isEmpty());
    }

    @Test
    void saveAndLoad() throws Exception {
        Party a = InvoiceDataTest.sample().seller;
        a.asBuyer = false;
        Party b = InvoiceDataTest.sample().buyer;
        b.asSeller = false;
        Party both = new Party();
        both.name = "Second Testfirma";
        both.country = "AT";

        // The directory is created.
        Path file = dir.resolve("settings").resolve("parties.json");
        InvoiceData.saveParties(file, List.of(a, b, both));
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Assertions.assertTrue(json.contains("\"parties\""), json);
        Assertions.assertFalse(json.contains("null"), "empty values are not written: " + json);
        Assertions.assertTrue(json.contains("\"asBuyer\" : false"), "false flags are written: " + json);

        List<Party> loaded = InvoiceData.loadParties(file);
        Assertions.assertEquals(3, loaded.size());
        Party la = loaded.get(0);
        Assertions.assertEquals(a.name, la.name);
        Assertions.assertEquals(a.city, la.city);
        Assertions.assertEquals(a.vatId, la.vatId);
        Assertions.assertEquals(a.iban, la.iban);
        Assertions.assertEquals(a.contactName, la.contactName);
        Assertions.assertEquals("AT", loaded.get(2).country);
        Assertions.assertNull(loaded.get(2).street);

        // Roles
        Assertions.assertTrue(la.hasRole(Role.SELLER));
        Assertions.assertFalse(la.hasRole(Role.BUYER));
        Assertions.assertFalse(loaded.get(1).hasRole(Role.SELLER));
        Assertions.assertTrue(loaded.get(1).hasRole(Role.BUYER));
        Assertions.assertTrue(loaded.get(2).hasRole(Role.SELLER) && loaded.get(2).hasRole(Role.BUYER),
                "new entries are offered in both roles");
    }

    @Test
    void missingFlagsMeanBothRoles() throws Exception {
        Path file = dir.resolve("parties.json");
        Files.writeString(file, "{\"parties\":[{\"name\":\"X\"}]}");
        Party x = InvoiceData.loadParties(file).get(0);
        Assertions.assertTrue(x.asSeller && x.asBuyer);
    }

    @Test
    void oldSeparateListsAreMerged() throws Exception {
        Path file = dir.resolve("parties.json");
        Files.writeString(file, "{\"sellers\":[{\"name\":\"Own GmbH\"},{\"name\":\"Partner AG\"}],"
                + "\"buyers\":[{\"name\":\"Customer KG\"},{\"name\":\"partner ag\"}]}");
        List<Party> loaded = InvoiceData.loadParties(file);
        Assertions.assertEquals(3, loaded.size(), "same name is merged");
        Assertions.assertTrue(loaded.get(0).asSeller && !loaded.get(0).asBuyer);
        Assertions.assertTrue(loaded.get(1).asSeller && loaded.get(1).asBuyer, "in both lists");
        Assertions.assertTrue(!loaded.get(2).asSeller && loaded.get(2).asBuyer);

        // Saving writes the new format only.
        InvoiceData.saveParties(file, loaded);
        String json = Files.readString(file);
        Assertions.assertFalse(json.contains("\"sellers\"") || json.contains("\"buyers\""), json);
        Assertions.assertEquals(3, InvoiceData.loadParties(file).size());
    }

    @Test
    void otherContentIsKept() throws Exception {
        Path file = dir.resolve("parties.json");
        Files.writeString(file, "{\"version\":2,\"parties\":[{\"name\":\"X\",\"future\":1}]}");
        Assertions.assertEquals("X", InvoiceData.loadParties(file).get(0).name, "unknown properties are ignored");

        Party y = new Party();
        y.name = "Y";
        InvoiceData.saveParties(file, List.of(y));
        String json = Files.readString(file);
        Assertions.assertTrue(json.contains("\"version\" : 2"), json);
        Assertions.assertEquals("Y", InvoiceData.loadParties(file).get(0).name);
    }

    @Test
    void brokenFile() throws Exception {
        Path file = dir.resolve("parties.json");
        Files.writeString(file, "{ not json");
        Assertions.assertThrows(IOException.class, () -> InvoiceData.loadParties(file));

        // Saving replaces it.
        Party y = new Party();
        y.name = "Y";
        InvoiceData.saveParties(file, List.of(y));
        Assertions.assertEquals("Y", InvoiceData.loadParties(file).get(0).name);
    }

    @Test
    void copy() {
        Party a = InvoiceDataTest.sample().seller;
        a.asBuyer = false;
        Party c = a.copy();
        Assertions.assertNotSame(a, c);
        Assertions.assertEquals(a.name, c.name);
        Assertions.assertEquals(a.iban, c.iban);
        Assertions.assertEquals(a.phone, c.phone);
        Assertions.assertFalse(c.asBuyer);
    }
}
