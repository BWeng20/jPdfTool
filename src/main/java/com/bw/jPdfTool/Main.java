package com.bw.jPdfTool;

import com.bw.jPdfTool.cli.CommandExecuter;
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
@Command(name = "jPdfTool", mixinStandardHelpOptions = true, version = "jPdfBox 2.0",
        footer = {
                "",
                "Permission flags have only effect if owner- or user-password is given.",
                "If only the user password is given, the owner password will be set to a random value."
        })
public class Main implements Callable<Integer> {

    @Option(names = {"-out", "--out"}, required = true, description = "The path to the output file.")
    private String out;

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

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call(){
        this.cli = true;

        if (this.out != null) {
            this.outfile = Paths.get(this.out);
        }

        for (String f : fileArguments) {
            Path p = Paths.get(f);
            if (Files.exists(p))
                this.files.add(p);
            else {
                Log.error("File '%s' doesn't exists.", f);
            }
        }
        return 0;
    }

    private boolean cli = false;

    private Path outfile;
    private final List<Path> files = new ArrayList<>();

    // TODO: Add cli for that
    private Path signaturefile;
    // TODO: Add cli for that
    private char[] signaturePassword;

    public boolean isCli() {
        return cli;
    }

    public static void main(String[] args) {

        Main main = new Main();
        if (args.length > 0) {
            int exitCode = new CommandLine(main).execute(args);
            if (exitCode != 0)
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

            // TODO: Currently all input files are merged and protected.
            executer.loadDocuments(password2Load);

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

            executer.save(ownerpassword, userpassword, ap, encryptionKeyLength, outfile,
                    signaturefile, signaturePassword);


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
