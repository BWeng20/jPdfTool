package com.bw.jPdfTool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

class MainTest {

    @Test
    void test_append() {

        Assertions.assertDoesNotThrow(() -> {

            Path testPdf = Paths.get(Objects.requireNonNull(MainTest.class.getResource("/Test.pdf")).toURI());

            long orgFileSize = Files.size(testPdf);
            System.out.println("Using File: " + testPdf + " size " + orgFileSize + " bytes");

            Path outPdf = Files.createTempFile("jPdfToolTest", ".pdf");


            Main main = new Main();
            main.parseArguments(new String[]{
                    "-orginalpassword", "testOwner",
                    "-ownerpassword", "testOwner2",
                    "-userpassword", "testUser",
                    "-out", outPdf.toString()
                    , testPdf.toString()
                    , testPdf.toString()
            });
            Assertions.assertTrue(main.isCli());
            int code = main.executeCommands();
            Assertions.assertEquals(0, code);

            long newFileSize = Files.size(outPdf);
            System.out.println("Created File: " + outPdf + " size " + newFileSize + " bytes");

            Assertions.assertTrue(Files.exists(outPdf));
            Assertions.assertTrue(newFileSize > orgFileSize);

            Files.delete(outPdf);
        });

    }

    @BeforeEach
    void setUp() {
    }
}