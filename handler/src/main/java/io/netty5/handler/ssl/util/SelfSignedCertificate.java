/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.ssl.util;

import io.netty5.buffer.api.Buffer;
import io.netty5.handler.codec.base64.Base64;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Generates a temporary self-signed certificate for testing purposes.
 * <p>
 * <strong>NOTE:</strong>
 * Never use the certificate and private key generated by this class in production.
 * It is purely for testing purposes, and thus it is very insecure.
 * It even uses an insecure pseudo-random generator for faster generation internally.
 * </p><p>
 * An X.509 certificate file and a EC/RSA private key file are generated in a system's temporary directory using
 * {@link File#createTempFile(String, String)}, and they are deleted when the JVM exits using
 * {@link File#deleteOnExit()}.
 * </p><p>
 * The certificate is generated using <a href="https://www.bouncycastle.org/">Bouncy Castle</a>, which is an
 * <em>optional</em> dependency of Netty.
 * </p>
 */
public final class SelfSignedCertificate {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SelfSignedCertificate.class);

    /** Current time minus 1 year, just in case software clock goes back due to time synchronization */
    private static final Date DEFAULT_NOT_BEFORE = new Date(SystemPropertyUtil.getLong(
            "io.netty5.selfSignedCertificate.defaultNotBefore", System.currentTimeMillis() - 86400000L * 365));
    /** The maximum possible value in X.509 specification: 9999-12-31 23:59:59 */
    private static final Date DEFAULT_NOT_AFTER = new Date(SystemPropertyUtil.getLong(
            "io.netty5.selfSignedCertificate.defaultNotAfter", 253402300799000L));

    /**
     * FIPS 140-2 encryption requires the RSA key length to be 2048 bits or greater.
     * Let's use that as a sane default but allow the default to be set dynamically
     * for those that need more stringent security requirements.
     */
    private static final int DEFAULT_KEY_LENGTH_BITS =
            SystemPropertyUtil.getInt("io.netty5.handler.ssl.util.selfSignedKeyStrength", 2048);

    private final File certificate;
    private final File privateKey;
    private final X509Certificate cert;
    private final PrivateKey key;

    /**
     * Creates a new instance.
     * <p> Algorithm: RSA </p>
     */
    public SelfSignedCertificate() throws CertificateException {
        this(DEFAULT_NOT_BEFORE, DEFAULT_NOT_AFTER, "RSA", DEFAULT_KEY_LENGTH_BITS);
    }

    /**
     * Creates a new instance.
     * <p> Algorithm: RSA </p>
     *
     * @param notBefore Certificate is not valid before this time
     * @param notAfter  Certificate is not valid after this time
     */
    public SelfSignedCertificate(Date notBefore, Date notAfter)
            throws CertificateException {
        this("localhost", notBefore, notAfter, "RSA", DEFAULT_KEY_LENGTH_BITS);
    }

    /**
     * Creates a new instance.
     *
     * @param notBefore Certificate is not valid before this time
     * @param notAfter  Certificate is not valid after this time
     * @param algorithm Key pair algorithm
     * @param bits      the number of bits of the generated private key
     */
    public SelfSignedCertificate(Date notBefore, Date notAfter, String algorithm, int bits)
            throws CertificateException {
        this("localhost", notBefore, notAfter, algorithm, bits);
    }

    /**
     * Creates a new instance.
     * <p> Algorithm: RSA </p>
     *
     * @param fqdn a fully qualified domain name
     */
    public SelfSignedCertificate(String fqdn) throws CertificateException {
        this(fqdn, DEFAULT_NOT_BEFORE, DEFAULT_NOT_AFTER, "RSA", DEFAULT_KEY_LENGTH_BITS);
    }

    /**
     * Creates a new instance.
     *
     * @param fqdn      a fully qualified domain name
     * @param algorithm Key pair algorithm
     * @param bits      the number of bits of the generated private key
     */
    public SelfSignedCertificate(String fqdn, String algorithm, int bits) throws CertificateException {
        this(fqdn, DEFAULT_NOT_BEFORE, DEFAULT_NOT_AFTER, algorithm, bits);
    }

    /**
     * Creates a new instance.
     * <p> Algorithm: RSA </p>
     *
     * @param fqdn      a fully qualified domain name
     * @param notBefore Certificate is not valid before this time
     * @param notAfter  Certificate is not valid after this time
     */
    public SelfSignedCertificate(String fqdn, Date notBefore, Date notAfter) throws CertificateException {
        // Bypass entropy collection by using insecure random generator.
        // We just want to generate it without any delay because it's for testing purposes only.
        this(fqdn, ThreadLocalInsecureRandom.current(), DEFAULT_KEY_LENGTH_BITS, notBefore, notAfter, "RSA");
    }

    /**
     * Creates a new instance.
     *
     * @param fqdn      a fully qualified domain name
     * @param notBefore Certificate is not valid before this time
     * @param notAfter  Certificate is not valid after this time
     * @param algorithm Key pair algorithm
     * @param bits      the number of bits of the generated private key
     */
    public SelfSignedCertificate(String fqdn, Date notBefore, Date notAfter, String algorithm, int bits)
            throws CertificateException {
        // Bypass entropy collection by using insecure random generator.
        // We just want to generate it without any delay because it's for testing purposes only.
        this(fqdn, ThreadLocalInsecureRandom.current(), bits, notBefore, notAfter, algorithm);
    }

    /**
     * Creates a new instance.
     * <p> Algorithm: RSA </p>
     *
     * @param fqdn      a fully qualified domain name
     * @param random    the {@link SecureRandom} to use
     * @param bits      the number of bits of the generated private key
     */
    public SelfSignedCertificate(String fqdn, SecureRandom random, int bits)
            throws CertificateException {
        this(fqdn, random, bits, DEFAULT_NOT_BEFORE, DEFAULT_NOT_AFTER, "RSA");
    }

    /**
     * Creates a new instance.
     *
     * @param fqdn      a fully qualified domain name
     * @param random    the {@link SecureRandom} to use
     * @param algorithm Key pair algorithm
     * @param bits      the number of bits of the generated private key
     */
    public SelfSignedCertificate(String fqdn, SecureRandom random, String algorithm, int bits)
            throws CertificateException {
        this(fqdn, random, bits, DEFAULT_NOT_BEFORE, DEFAULT_NOT_AFTER, algorithm);
    }

    /**
     * Creates a new instance.
     * <p> Algorithm: RSA </p>
     *
     * @param fqdn      a fully qualified domain name
     * @param random    the {@link SecureRandom} to use
     * @param bits      the number of bits of the generated private key
     * @param notBefore Certificate is not valid before this time
     * @param notAfter  Certificate is not valid after this time
     */
    public SelfSignedCertificate(String fqdn, SecureRandom random, int bits, Date notBefore, Date notAfter)
            throws CertificateException {
        this(fqdn, random, bits, notBefore, notAfter, "RSA");
    }

    /**
     * Creates a new instance.
     *
     * @param fqdn      a fully qualified domain name
     * @param random    the {@link SecureRandom} to use
     * @param bits      the number of bits of the generated private key
     * @param notBefore Certificate is not valid before this time
     * @param notAfter  Certificate is not valid after this time
     * @param algorithm Key pair algorithm
     */
    public SelfSignedCertificate(String fqdn, SecureRandom random, int bits, Date notBefore, Date notAfter,
                                 String algorithm) throws CertificateException {

        if (!"EC".equalsIgnoreCase(algorithm) && !"RSA".equalsIgnoreCase(algorithm)) {
            throw new IllegalArgumentException("Algorithm not valid: " + algorithm);
        }

        final KeyPair keypair;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);
            keyGen.initialize(bits, random);
            keypair = keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            // Should not reach here because every Java implementation must have RSA and EC key pair generator.
            throw new Error(e);
        }

        String[] paths;
        try {
            paths = BouncyCastleSelfSignedCertGenerator.generate(
                    fqdn, keypair, random, notBefore, notAfter, algorithm);
        } catch (Throwable throwable) {
            logger.debug("Failed to generate a self-signed X.509 certificate using Bouncy Castle:", throwable);
            throw new CertificateException(
                    "No provider succeeded to generate a self-signed certificate. " +
                    "See debug log for the root cause.", throwable);
        }

        certificate = new File(paths[0]);
        privateKey = new File(paths[1]);
        key = keypair.getPrivate();
        FileInputStream certificateInput = null;
        try {
            certificateInput = new FileInputStream(certificate);
            cert = (X509Certificate) CertificateFactory.getInstance("X509").generateCertificate(certificateInput);
        } catch (Exception e) {
            throw new CertificateEncodingException(e);
        } finally {
            if (certificateInput != null) {
                try {
                    certificateInput.close();
                } catch (IOException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Failed to close a file: " + certificate, e);
                    }
                }
            }
        }
    }

    /**
     * Returns the generated X.509 certificate file in PEM format.
     */
    public File certificate() {
        return certificate;
    }

    /**
     * Returns the generated EC/RSA private key file in PEM format.
     */
    public File privateKey() {
        return privateKey;
    }

    /**
     *  Returns the generated X.509 certificate.
     */
    public X509Certificate cert() {
        return cert;
    }

    /**
     * Returns the generated EC/RSA private key.
     */
    public PrivateKey key() {
        return key;
    }

    /**
     * Deletes the generated X.509 certificate file and EC/RSA private key file.
     */
    public void delete() {
        safeDelete(certificate);
        safeDelete(privateKey);
    }

    static String[] newSelfSignedCertificate(
            String fqdn, PrivateKey key, X509Certificate cert) throws IOException, CertificateEncodingException {
        // Encode the private key into a file.
        final String keyText;
        try (Buffer wrappedBuf = onHeapAllocator().copyOf(key.getEncoded());
             Buffer encodedBuf = Base64.encode(wrappedBuf, true)) {
            keyText = "-----BEGIN PRIVATE KEY-----\n" +
                      encodedBuf.toString(US_ASCII) +
                      "\n-----END PRIVATE KEY-----\n";
        }

        // Change all asterisk to 'x' for file name safety.
        fqdn = fqdn.replaceAll("[^\\w.-]", "x");

        File keyFile = PlatformDependent.createTempFile("keyutil_" + fqdn + '_', ".key", null);
        keyFile.deleteOnExit();

        OutputStream keyOut = new FileOutputStream(keyFile);
        try {
            keyOut.write(keyText.getBytes(US_ASCII));
            keyOut.close();
            keyOut = null;
        } finally {
            if (keyOut != null) {
                safeClose(keyFile, keyOut);
                safeDelete(keyFile);
            }
        }

        final String certText;
        try (Buffer wrappedBuf = onHeapAllocator().copyOf(cert.getEncoded());
             Buffer encodedBuf = Base64.encode(wrappedBuf, true)) {
            // Encode the certificate into a CRT file.
            certText = "-----BEGIN CERTIFICATE-----\n" +
                       encodedBuf.toString(US_ASCII) +
                       "\n-----END CERTIFICATE-----\n";
        }

        File certFile = PlatformDependent.createTempFile("keyutil_" + fqdn + '_', ".crt", null);
        certFile.deleteOnExit();

        OutputStream certOut = new FileOutputStream(certFile);
        try {
            certOut.write(certText.getBytes(US_ASCII));
            certOut.close();
            certOut = null;
        } finally {
            if (certOut != null) {
                safeClose(certFile, certOut);
                safeDelete(certFile);
                safeDelete(keyFile);
            }
        }

        return new String[] { certFile.getPath(), keyFile.getPath() };
    }

    private static void safeDelete(File certFile) {
        if (!certFile.delete()) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to delete a file: " + certFile);
            }
        }
    }

    private static void safeClose(File keyFile, OutputStream keyOut) {
        try {
            keyOut.close();
        } catch (IOException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close a file: " + keyFile, e);
            }
        }
    }
}
