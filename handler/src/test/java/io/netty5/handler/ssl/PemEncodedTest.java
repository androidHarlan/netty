/*
 * Copyright 2016 The Netty Project
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

package io.netty5.handler.ssl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.PrivateKey;

import io.netty5.buffer.api.Resource;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.netty5.buffer.api.DefaultBufferAllocators.offHeapAllocator;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class PemEncodedTest {

    @Test
    public void testPemEncodedOpenSsl() throws Exception {
        testPemEncoded(SslProvider.OPENSSL);
    }

    @Test
    public void testPemEncodedOpenSslRef() throws Exception {
        testPemEncoded(SslProvider.OPENSSL_REFCNT);
    }

    private static void testPemEncoded(SslProvider provider) throws Exception {
        OpenSsl.ensureAvailability();
        assumeFalse(OpenSsl.supportsKeyManagerFactory());
        PemPrivateKey pemKey;
        PemX509Certificate pemCert;
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        try {
            pemKey = PemPrivateKey.valueOf(toByteArray(ssc.privateKey()));
            pemCert = PemX509Certificate.valueOf(toByteArray(ssc.certificate()));
        } finally {
            ssc.delete();
        }

        assertTrue(pemKey.content().readOnly());
        assertTrue(pemCert.content().readOnly());

        SslContext context = SslContextBuilder.forServer(pemKey, pemCert)
                .sslProvider(provider)
                .build();
        assertFalse(pemKey.isDestroyed());
        assertTrue(pemCert.isAccessible());
        try {
            assertTrue(context instanceof ReferenceCountedOpenSslContext);
        } finally {
            ReferenceCountUtil.release(context);
            assertRelease(pemKey);
            assertRelease(pemCert);
        }
    }

    @Test
    public void testEncodedReturnsNull() throws Exception {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                PemPrivateKey.toPEM(offHeapAllocator(), new PrivateKey() {
                    @Override
                    public String getAlgorithm() {
                        return null;
                    }

                    @Override
                    public String getFormat() {
                        return null;
                    }

                    @Override
                    public byte[] getEncoded() {
                        return null;
                    }
                });
            }
        });
    }

    private static void assertRelease(PemEncoded encoded) {
        encoded.close();
        assertFalse(((Resource<?>) encoded).isAccessible());
    }

    private static byte[] toByteArray(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
            } finally {
                baos.close();
            }

            return baos.toByteArray();
        } finally {
            in.close();
        }
    }
}
