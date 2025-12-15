package com.bw.jPdfTool;

import com.bw.jPdfTool.cli.CommandExecuter;
import com.bw.jPdfTool.ui.UI;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI interface. Accepts file names as arguments and opens the UI or executes commands headless.
 */
public class Main {

    public static void main(String[] args) {
        Main main = new Main();

        main.parseArguments(args);
        if (main.cli) {
            main.executeCommands();
        } else {
            main.createUI();
        }
    }

    public void parseArguments(String[] args) {
        try {
            this.files = new ArrayList<>(args.length);
            for (int ai = 0; ai < args.length; ai++) {
                String arg = args[ai];
                if (arg.equalsIgnoreCase("-out")) {
                    this.cli = true;
                    this.outfile = Paths.get(args[++ai]);
                } else if (arg.equalsIgnoreCase("-orginalpassword")) {
                    this.cli = true;
                    this.password2Load = args[++ai];
                } else if (arg.equalsIgnoreCase("-ownerpassword")) {
                    this.cli = true;
                    this.ownerpassword = args[++ai];
                } else if (arg.equalsIgnoreCase("-userpassword")) {
                    this.cli = true;
                    this.userpassword = args[++ai];
                } else {
                    Path p = Paths.get(arg);
                    if (Files.exists(p))
                        this.files.add(p);
                    else {
                        Log.error("File '%s' doesn't exists.", arg);
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            System.err.println("Error in arguments");
            usage();
            System.exit(1);
        }

    }

    private boolean cli = false;

    private Path outfile;
    private List<Path> files;
    private String ownerpassword;
    private String userpassword;
    private String password2Load;

    // TODO: Add cli for that
    private Path signaturefile;
    // TODO: Add cli for that
    private char[] signaturePassword;

    protected void usage() {
    }

    protected void executeCommands() {

        if (files.isEmpty()) {
            System.err.println("No file arguments given");
            usage();
            System.exit(2);
        }
        if (outfile == null) {
            System.err.println("No output file given");
            usage();
            System.exit(3);
        }
        try {

            // TODO: Currently all input files are merged and protected.

            CommandExecuter executer = new CommandExecuter(files);
            executer.loadDocuments(password2Load);

            // TODO: Add cli for that
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(true);
            ap.setCanModify(false);
            ap.setCanExtractContent(true);
            ap.setCanExtractForAccessibility(true);
            ap.setCanFillInForm(false);
            ap.setCanAssembleDocument(false);
            ap.setCanModifyAnnotations(false);

            // TODO: Add cli for that
            int encryptionKeyLength = 256;

            executer.save(ownerpassword, userpassword, ap, encryptionKeyLength, outfile,
                    signaturefile, signaturePassword);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    protected void createUI() {
        UI.createUI(files);
    }

}
