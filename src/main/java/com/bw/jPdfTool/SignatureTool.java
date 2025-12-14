package com.bw.jPdfTool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Tools for signing.<br>
 * Based on PdfBox Example <a href="https://svn.apache.org/repos/asf/pdfbox/trunk/examples/src/main/java/org/apache/pdfbox/examples/signature">Signature</a>.
 */
public class SignatureTool implements SignatureInterface {
    /**
     * Initialize the signature creator with a keystore (pkcs12) and pin that should be used for the
     * signature.
     *
     * @param keyStoreIs is a pkcs12 keystore or null.
     * @throws KeyStoreException        if the keystore has not been initialized (loaded)
     * @throws NoSuchAlgorithmException if the algorithm for recovering the key cannot be found
     * @throws CertificateException     if the certificate is not valid as signing time
     * @throws IOException              if no certificate could be found
     */
    public SignatureTool(InputStream keyStoreIs, char[] keyStorePassword)
            throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
        keystore = KeyStore.getInstance("PKCS12");
        keystore.load(keyStoreIs, keyStorePassword);
    }

    KeyStore keystore;

    /**
     * SignatureInterface sample implementation.
     * <p>
     * This method will be called from inside the pdfbox and create the PKCS #7 signature.
     * The given InputStream contains the bytes that are given by the byte range.
     * <p>
     */
    @Override
    public byte[] sign(InputStream content) throws IOException {

        try {
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            X509Certificate cert = (X509Certificate) certificateChain[0];
            ContentSigner sha1Signer = new JcaContentSignerBuilder(cert.getSigAlgName()).build(privateKey);
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(sha1Signer, cert));
            gen.addCertificates(new JcaCertStore(Arrays.asList(certificateChain)));

            CMSProcessableByteArray msg = new CMSProcessableByteArray(content.readAllBytes());
            CMSSignedData signedData = gen.generate(msg, false);
            return signedData.getEncoded();
        } catch (GeneralSecurityException | CMSException | OperatorCreationException e) {
            throw new IOException(e);
        }
    }

    private PrivateKey privateKey;
    private Certificate[] certificateChain;

    public void addKey(InputStream is, char[] pin) throws Exception {
        this.keystore.load(is, pin);
    }

    /**
     * @param pin is the pin for the keystore / private key
     */
    public void addSignature(PDDocument document, String keyAlias, char[] pin)
            throws Exception {
        Enumeration<String> aliases = keystore.aliases();
        Certificate cert = null;
        while (cert == null && aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyAlias == null || Objects.equals(alias, keyAlias)) {
                this.privateKey = (PrivateKey) keystore.getKey(alias, pin);
                Certificate[] certChain = keystore.getCertificateChain(alias);
                if (certChain != null) {
                    this.certificateChain = certChain;
                    cert = certChain[0];
                    if (cert instanceof X509Certificate) {
                        // avoid expired certificate
                        ((X509Certificate) cert).checkValidity();
                    }
                }
            }
        }

        if (cert == null) {
            throw new IOException("Could not find certificate");
        }

        PDSignature signature = new PDSignature();
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        signature.setName("TODO");
        signature.setLocation("TODO");
        signature.setReason("TODO");
        signature.setSignDate(Calendar.getInstance());

        SignatureOptions signatureOptions = new SignatureOptions();
        signatureOptions.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);
        // register signature dictionary and sign interface
        document.addSignature(signature, this, signatureOptions);
    }

    static {
        // Register the Bouncy Castle provider
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

}