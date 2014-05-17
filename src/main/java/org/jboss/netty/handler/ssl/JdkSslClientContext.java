/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.jboss.netty.handler.ssl;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

public final class JdkSslClientContext extends JdkSslContext {

    private final SSLContext ctx;
    private final SSLSessionContext sessCtx;

    public JdkSslClientContext() throws SSLException {
        this(null, null, null, null, null, 0, 0);
    }

    public JdkSslClientContext(File certChainFile) throws SSLException {
        this(certChainFile, null);
    }

    public JdkSslClientContext(TrustManagerFactory trustManagerFactory) throws SSLException {
        this(null, trustManagerFactory);
    }

    public JdkSslClientContext(File certChainFile, TrustManagerFactory trustManagerFactory) throws SSLException {
        this(null, certChainFile, trustManagerFactory, null, null, 0, 0);
    }

    /**
     * Creates a new factory that creates a new client-side {@link javax.net.ssl.SSLEngine}.
     */
    public JdkSslClientContext(
            SslBufferPool bufPool, File certChainFile, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, ApplicationProtocolSelector nextProtocolSelector,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        super(bufPool, ciphers);

        if (nextProtocolSelector != null) {
            throw new SSLException("NPN/ALPN unsupported: " + nextProtocolSelector);
        }

        try {
            if (certChainFile == null) {
                ctx = SSLContext.getInstance(PROTOCOL);
                if (trustManagerFactory == null) {
                    ctx.init(null, null, null);
                } else {
                    trustManagerFactory.init((KeyStore) null);
                    ctx.init(null, trustManagerFactory.getTrustManagers(), null);
                }
            } else {
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(null, null);
                CertificateFactory cf = CertificateFactory.getInstance("X.509");

                for (ChannelBuffer buf: PemReader.readCertificates(certChainFile)) {
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(new ChannelBufferInputStream(buf));
                    X500Principal principal = cert.getSubjectX500Principal();
                    ks.setCertificateEntry(principal.getName("RFC2253"), cert);
                }

                // Set up trust manager factory to use our key store.
                if (trustManagerFactory == null) {
                    trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                }
                trustManagerFactory.init(ks);

                // Initialize the SSLContext to work with the trust managers.
                ctx = SSLContext.getInstance(PROTOCOL);
                ctx.init(null, trustManagerFactory.getTrustManagers(), null);
            }

            sessCtx = ctx.getServerSessionContext();
            if (sessionCacheSize > 0) {
                sessCtx.setSessionCacheSize((int) Math.min(sessionCacheSize, Integer.MAX_VALUE));
            }
            if (sessionTimeout > 0) {
                sessCtx.setSessionTimeout((int) Math.min(sessionTimeout, Integer.MAX_VALUE));
            }
        } catch (Exception e) {
            throw new SSLException("failed to initialize the server-side SSL context", e);
        }
    }

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public ApplicationProtocolSelector nextProtocolSelector() {
        return null;
    }

    @Override
    public List<String> nextProtocols() {
        return Collections.emptyList();
    }

    /**
     * Returns the {@link javax.net.ssl.SSLContext} object of this factory.
     */
    @Override
    public SSLContext context() {
        return ctx;
    }
}
