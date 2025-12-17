package com.bw.jPdfTool;

import com.bw.jPdfTool.cli.CommandExecuter;
import com.bw.jPdfTool.model.MergeOptions;
import com.bw.jPdfTool.ui.UI;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * CLI interface. Accepts file names as arguments and opens the UI or executes commands headless.
 */
@Command(name = "jPdfTool", mixinStandardHelpOptions = true, version = "jPdfBox 2.1",
        footer = {
                "",
                "Permission flags have only effect if owner- or user-password is given.",
                "If only the user password is given, the owner password will be set to a random value."
        })
public class Main implements Callable<Integer> {

    @Option(names = {"-out", "--out"}, required = true, description = "The path to the output file.")
    private String out;

    @Option(names = {"-s", "--split"}, paramLabel = "<pages per file>", required = false, description = "Split resulting document. Output filename is extended by an index for each file.")
    private int splitToPagesPerFile = 0;

    @Option(names = {"-opw", "--ownerpassword"}, paramLabel = "<password>", description = "Owner password for the output PDF.")
    private String ownerpassword = "";

    @Option(names = {"-upw", "--userpassword"}, paramLabel = "<password>", description = "User password for the output PDF.")
    private String userpassword = "";

    @Option(names = {"-pw", "--orginalpassword"}, paramLabel = "<password>", description = "Password for the input PDF.")
    private String password2Load;

    @Parameters(paramLabel = "PDF File", description = "File(s) to load.")
    private List<String> fileArguments;

    @Option(names = {"-cpt", "--canPrint"}, negatable = true, defaultValue = "true", description = "User shall be able to print.")
    private boolean canPrint = true;

    @Option(names = {"-cmd", "--canModify"}, negatable = true, defaultValue = "false", description = "User shall be able to modify the document.")
    private boolean canModify = false;

    @Option(names = {"-cec", "--canExtractContent"}, negatable = true, defaultValue = "true", description = "User shall be able to extract content.")
    private boolean canExtractContent = true;

    @Option(names = {"-cea", "--canExtractForAccessibility"}, negatable = true, defaultValue = "true", description = "User shall be able to extract content for accessibility.")
    private boolean canExtractForAccessibility = true;

    @Option(names = {"-cff", "--canFillInForm"}, negatable = true, defaultValue = "false", description = "User shall be able to fill in forms.")
    private boolean canFillInForm = false;

    @Option(names = {"-cas", "--canAssemble"}, negatable = true, defaultValue = "false", description = "User shall be able to assemble documents.")
    private boolean canAssemble = false;

    @Option(names = {"-cma", "--canModifyAnnotations"}, negatable = true, defaultValue = "false", description = "User shall be able to modify annotations.")
    private boolean canModifyAnnotations = false;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message and exit.")
    private boolean helpRequested = false;

    @Option(names = {"-m", "--merge"}, paramLabel = "<start>-<amount>-<skip>", description =
            "Zipper merge instead of append. '5-2-3' will start before page 5 of the current document, inserts 2 pages of the new document, then skip 3 pages, then inserts again 2 pages, skip again 3 e.t.c. until all new pages are added.")
    private String mergeOptions = "";

    @Option(names = {"-cert", "--certificate"}, paramLabel = "<p12 file>", description =
            "Signs the resulting PDF with a certificate from a p12 file.")
    private String signCertFile = null;

    @Option(names = {"-cpw", "--certificatePassword"}, paramLabel = "<password for p12 file>", description =
            "Password for the certificate.")
    private String signCertPassword = null;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        this.cli = true;

        if (helpRequested)
            return 0;

        if (this.out != null) {
            this.outfile = Paths.get(this.out);
        }

        if (signCertFile != null) {
            signatureFile = Paths.get(signCertFile);
            if (!Files.exists(signatureFile)) {
                System.err.printf("File '%s' doesn't exists.\n", signatureFile);
                return 10;
            }
        }

        if (signCertPassword != null)
            signaturePassword = signCertPassword.toCharArray();
        signCertPassword = null;

        for (String f : fileArguments) {
            Path p = Paths.get(f);
            if (Files.exists(p))
                this.files.add(p);
            else {
                System.err.printf("File '%s' doesn't exists.\n", f);
            }
        }
        return 0;
    }

    private boolean cli = false;

    private Path outfile;
    private final List<Path> files = new ArrayList<>();

    private Path signatureFile;
    private char[] signaturePassword;

    public boolean isCli() {
        return cli;
    }

    public boolean isHelpRequested() {
        return helpRequested;
    }

    public static void main(String[] args) {

        Main main = new Main();
        if (args.length > 0) {
            int exitCode = new CommandLine(main).execute(args);
            if (exitCode != 0 || main.helpRequested)
                System.exit(exitCode);
            else
                System.exit(main.executeCommands());
        } else {
            main.createUI();
        }
    }

    public void usage(PrintStream os) {
        spec.commandLine().usage(os);
    }

    /**
     * Execute the commands from CLI.
     *
     * @return The exit code (0 for success).
     */
    protected int executeCommands() {

        if (files.isEmpty()) {
            System.err.println("No file arguments given.");
            usage(System.err);
            return 2;
        }
        if (outfile == null) {
            System.err.println("No output file given.");
            usage(System.err);
            return 3;
        }
        CommandExecuter executer = new CommandExecuter(files);
        try {
            MergeOptions mo = null;
            if (!mergeOptions.isEmpty()) {
                String[] mergeParams = mergeOptions.split("-");
                if (mergeParams.length != 3) {
                    System.err.println("error in '-merge' argument.");
                    usage(System.err);
                    return 4;
                }
                mo = new MergeOptions();
                try {
                    mo.startPageNb = Integer.parseInt(mergeParams[0]);
                    mo.segmentLength = Integer.parseInt(mergeParams[1]);
                    mo.gapLength = Integer.parseInt(mergeParams[2]);
                } catch (NumberFormatException ne) {
                    System.err.println("illegal number in '-merge' argument.");
                    usage(System.err);
                    return 5;
                }
                System.out.println("Merging " + mo);
            }

            // TODO: Currently all input files are merged and protected.
            executer.loadDocuments(password2Load, mo);

            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(canPrint);
            ap.setCanModify(canModify);
            ap.setCanExtractContent(canExtractContent);
            ap.setCanExtractForAccessibility(canExtractForAccessibility);
            ap.setCanFillInForm(canFillInForm);
            ap.setCanAssembleDocument(canAssemble);
            ap.setCanModifyAnnotations(canModifyAnnotations);

            // TODO: Add cli for that
            int encryptionKeyLength = 256;

            if (ownerpassword.isEmpty() && !userpassword.isEmpty()) {
                ownerpassword = UUID.randomUUID().toString();
                System.out.println("Generated owner password: " + ownerpassword);
            }

            if (this.splitToPagesPerFile > 0) {
                executer.split(this.splitToPagesPerFile,
                        ownerpassword, userpassword, ap, encryptionKeyLength, outfile,
                        signatureFile, signaturePassword);

            } else {
                executer.save(ownerpassword, userpassword, ap, encryptionKeyLength, outfile,
                        signatureFile, signaturePassword);
            }


        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        } finally {
            executer.close();
        }
        return 0;
    }

    protected void createUI() {
        UI.createUI(files);
    }

}
