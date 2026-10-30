package com.bw.jPdfTool;

import com.bw.jPdfTool.invoice.EInvoice;
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

import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * CLI interface. Accepts file names as arguments and opens the UI or executes commands headless.
 */
@Command(name = "jPdfTool", mixinStandardHelpOptions = true, version = "jPdfTool " + BuildVersion.VERSION,
        footer = {
                "",
                "Permission flags have only effect if owner- or user-password is given.",
                "If only the user password is given, the owner password will be set to a random value."
        })
public class Main implements Callable<Integer> {

    @Option(names = {"-out", "--out"}, description = "The path to the output file. Without it (and without other options) the UI is opened with the given files.")
    private String out;

    @Option(names = {"-s", "--split"}, paramLabel = "<pages per file>", description = "Split resulting document. Output filename is extended by an index for each file.")
    private int splitToPagesPerFile = 0;

    @Option(names = {"-opw", "--ownerpassword"}, paramLabel = "<password>", description = "Owner password for the output PDF.")
    private String ownerpassword = "";

    @Option(names = {"-upw", "--userpassword"}, paramLabel = "<password>", description = "User password for the output PDF.")
    private String userpassword = "";

    @Option(names = {"-pw", "--originalpassword", "--orginalpassword"}, paramLabel = "<password>", description = "Password for the input PDF. ('--orginalpassword' is the old, misspelled name, kept for compatibility.)")
    private String password2Load;

    @Parameters(paramLabel = "PDF File", description = "File(s) to load.")
    private List<String> fileArguments;

    @Option(names = {"-cpt", "--canPrint"}, negatable = true, defaultValue = "false", description = "User shall be able to print.")
    private boolean canPrint = false;

    @Option(names = {"-cmd", "--canModify"}, negatable = true, defaultValue = "false", description = "User shall be able to modify the document.")
    private boolean canModify = false;

    @Option(names = {"-cec", "--canExtractContent"}, negatable = true, defaultValue = "false", description = "User shall be able to extract content.")
    private boolean canExtractContent = false;

    @Option(names = {"-cea", "--canExtractForAccessibility"}, negatable = true, defaultValue = "false", description = "User shall be able to extract content for accessibility.")
    private boolean canExtractForAccessibility = false;

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

    @Option(names = {"-rt", "--redactText"}, paramLabel = "<regex>", description =
            "Permanently removes all text matching the regular expression. The affected pages are converted to images.")
    private List<String> redactTexts = new ArrayList<>();

    @Option(names = {"-ra", "--redactArea"}, paramLabel = "<page>:<x>,<y>,<width>,<height>", description =
            "Permanently removes an area. Values in points (1/72 inch) from the upper left corner of the page, e.g. '1:50,100,200,20'. The affected pages are converted to images.")
    private List<String> redactAreas = new ArrayList<>();

    @Option(names = {"-ai", "--attachInvoice"}, paramLabel = "<xml file>", description =
            "Attaches an invoice (ZUGFeRD/Factur-X, CII syntax) to the resulting PDF. An existing invoice is replaced. "
                    + "The result is only valid if the PDF is PDF/A-3 compliant, use '-vi' to check that.")
    private String attachInvoice = null;

    @Option(names = {"-ei", "--extractInvoice"}, paramLabel = "<xml file>", description =
            "Writes the invoice (ZUGFeRD/Factur-X) of the loaded document to a file.")
    private String extractInvoice = null;

    @Option(names = {"-ri", "--removeInvoice"}, description =
            "Removes the invoice from the document. Required if you redact a document that contains an invoice.")
    private boolean removeInvoice = false;

    @Option(names = {"-vi", "--validateInvoice"}, description =
            "Validates the resulting PDF and its invoice (PDF/A-3 and EN 16931). Can't be combined with '--split'.")
    private boolean validateInvoice = false;

    @Option(names = {"--debug"}, description = "Shows debug output and stack traces. Also possible with the system property 'jpdftool.debug=true'. Can be used together with file names to start the UI in debug mode.")
    private boolean debug = false;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        if (debug)
            Log.setDebug(true);

        // Only file names: open them in the UI. Any option means CLI (that then needs an output file).
        // "--debug" works for both.
        this.cli = this.out != null || spec.commandLine().getParseResult().matchedOptions().stream()
                .anyMatch(o -> !o.longestName().equals("--debug"));

        if (helpRequested)
            return 0;

        if (this.out != null) {
            this.outfile = Paths.get(this.out);
        }

        if (attachInvoice != null && !Files.exists(Paths.get(attachInvoice))) {
            System.err.printf("File '%s' doesn't exists.\n", attachInvoice);
            return 10;
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

        if (fileArguments == null)
            return 0;
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
            else if (main.isCli())
                System.exit(main.executeCommands());
            else
                main.createUI();
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

            List<Pattern> textPatterns = new ArrayList<>();
            List<Redactor.Region> areas = new ArrayList<>();
            try {
                for (String t : redactTexts)
                    textPatterns.add(Pattern.compile(t));
                for (String a : redactAreas)
                    areas.add(parseRedactArea(a));
            } catch (IllegalArgumentException e) {
                System.err.println("error in redact argument: " + e.getMessage());
                usage(System.err);
                return 6;
            }

            if (validateInvoice && splitToPagesPerFile > 0) {
                System.err.println("'--validateInvoice' can't be combined with '--split'.");
                usage(System.err);
                return 7;
            }

            // TODO: Currently all input files are merged and protected.
            try {
                executer.loadDocuments(password2Load, mo);
            } catch (CommandExecuter.LoadException e) {
                System.err.println(e.getMessage());
                return 14;
            }

            final boolean hasInvoice = executer.hasInvoice();

            if (extractInvoice != null) {
                if (executer.extractInvoice(Paths.get(extractInvoice)))
                    System.out.println("Stored invoice to '" + extractInvoice + "'");
                else {
                    System.err.println("The document contains no invoice.");
                    return 8;
                }
            }

            if (hasInvoice && !removeInvoice && (!textPatterns.isEmpty() || !areas.isEmpty())) {
                System.err.println("The document contains an invoice. Redaction doesn't touch its data, "
                        + "so confidential data may remain in the invoice. Use '--removeInvoice' to drop it.");
                return 9;
            }

            if (!textPatterns.isEmpty() || !areas.isEmpty()) {
                System.out.println("Redacted " + executer.redact(textPatterns, areas) + " areas");
            }

            if (removeInvoice) {
                if (executer.removeInvoice())
                    System.out.println("Removed invoice");
                else
                    System.out.println("The document contains no invoice");
            }

            if (attachInvoice != null) {
                try {
                    executer.attachInvoice(Paths.get(attachInvoice));
                } catch (EInvoice.NotConformException e) {
                    System.err.println(e.getMessage());
                    return 13;
                }
                System.out.println("Attached invoice '" + attachInvoice + "'");
            }

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

            if (validateInvoice) {
                EInvoice.Validation v;
                try {
                    v = executer.validateInvoice(outfile);
                } catch (Exception e) {
                    System.err.println("Validation failed: " + e.getMessage());
                    return 11;
                }
                v.messages().forEach(System.err::println);
                if (!v.valid()) {
                    System.err.println("The resulting document is no valid invoice.");
                    return 12;
                }
                System.out.println("The resulting document is a valid invoice.");
            }


        } catch (Exception e) {
            Log.exception(e);
            return 1;
        } finally {
            executer.close();
        }
        return 0;
    }

    /**
     * Parses "page:x,y,width,height".
     *
     * @throws IllegalArgumentException If the argument is malformed (including NumberFormatException).
     */
    static Redactor.Region parseRedactArea(String arg) {
        String[] pageAndArea = arg.split(":");
        String[] v = pageAndArea.length == 2 ? pageAndArea[1].split(",") : new String[0];
        if (v.length != 4)
            throw new IllegalArgumentException("'" + arg + "' doesn't match <page>:<x>,<y>,<width>,<height>");
        int page = Integer.parseInt(pageAndArea[0].trim());
        double x = Double.parseDouble(v[0].trim());
        double y = Double.parseDouble(v[1].trim());
        double w = Double.parseDouble(v[2].trim());
        double h = Double.parseDouble(v[3].trim());
        return new Redactor.Region(page, new Rectangle2D.Double(x, y, w, h));
    }

    protected void createUI() {
        UI.createUI(files);
    }

}
