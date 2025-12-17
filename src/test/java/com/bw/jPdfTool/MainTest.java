package com.bw.jPdfTool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

class MainTest {

    @Test
    void test_help() {
        Main main = new Main();
        int exitCode = new CommandLine(main).execute("-h");
        Assertions.assertEquals(0, exitCode);
        Assertions.assertTrue(main.isHelpRequested());
    }

    @Test
    void test_append() {
        Assertions.assertDoesNotThrow(() -> {

            Path testPdf = Paths.get(Objects.requireNonNull(MainTest.class.getResource("/Test.pdf")).toURI());

            long orgFileSize = Files.size(testPdf);
            System.out.println("Using File: " + testPdf + " size " + orgFileSize + " bytes");

            Path outPdf = Files.createTempFile("jPdfToolTest", ".pdf");


            Main main = new Main();
            int exitCode = new CommandLine(main).execute("-pw", "testOwner",
                    "-upw", "test123",
                    "--no-canPrint",
                    "-out", outPdf.toString()
                    , testPdf.toString()
                    , testPdf.toString());
            Assertions.assertEquals(0, exitCode);

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

    @Test
    void test_split() {
        Assertions.assertDoesNotThrow(() -> {
            Path testPdf = Paths.get(Objects.requireNonNull(MainTest.class.getResource("/Test.pdf")).toURI());

            long orgFileSize = Files.size(testPdf);
            System.out.println("Using File: " + testPdf + " size " + orgFileSize + " bytes");

            Path outPdf = Files.createTempFile("jPdfToolTest", ".pdf");

            String baseFileName = outPdf.getFileName().toString();
            baseFileName = baseFileName.substring(0, baseFileName.length() - 4);

            Path outPdf1 = outPdf.getParent().resolve(baseFileName + "001.pdf");
            Path outPdf2 = outPdf.getParent().resolve(baseFileName + "002.pdf");

            Main main = new Main();
            int exitCode = new CommandLine(main).execute("-pw", "testOwner",
                    "-upw", "test123",
                    "--no-canPrint",
                    "--split", "1",
                    "-out", outPdf.toString()
                    , testPdf.toString()
                    , testPdf.toString());
            Assertions.assertEquals(0, exitCode);

            Assertions.assertTrue(main.isCli());
            int code = main.executeCommands();
            Assertions.assertEquals(0, code);

            Assertions.assertTrue(Files.exists(outPdf1));
            Assertions.assertTrue(Files.exists(outPdf2));

            long newFileSize1 = Files.size(outPdf1);
            long newFileSize2 = Files.size(outPdf2);
            System.out.println("Created Files: " + outPdf + " size " + newFileSize1 + "/ " + newFileSize2 + " bytes");

            Files.delete(outPdf1);
            Files.delete(outPdf2);
        });
    }

    @BeforeEach
    void setUp() {
    }
}