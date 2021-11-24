/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NativeLibraryLoaderTest {

    @Test
    public void testFileNotFound() {
        try {
            NativeLibraryLoader.load(UUID.randomUUID().toString(), NativeLibraryLoaderTest.class.getClassLoader());
            fail();
        } catch (UnsatisfiedLinkError error) {
            assertTrue(error.getCause() instanceof FileNotFoundException);
            if (PlatformDependent.javaVersion() >= 7) {
                verifySuppressedException(error, UnsatisfiedLinkError.class);
            }
        }
    }

    @Test
    public void testFileNotFoundWithNullClassLoader() {
        try {
            NativeLibraryLoader.load(UUID.randomUUID().toString(), null);
            fail();
        } catch (UnsatisfiedLinkError error) {
            assertTrue(error.getCause() instanceof FileNotFoundException);
            if (PlatformDependent.javaVersion() >= 7) {
                verifySuppressedException(error, ClassNotFoundException.class);
            }
        }
    }

    @Test
    void testMultipleResourcesInTheClassLoader() throws MalformedURLException {
        URL url1 = new File("src/test/data/NativeLibraryLoader/1").toURI().toURL();
        URL url2 = new File("src/test/data/NativeLibraryLoader/2").toURI().toURL();
        URLClassLoader loader = new URLClassLoader(new URL[] {url1, url2});
        String resourceName = "test";
        try {
            NativeLibraryLoader.load(resourceName, loader);
            fail("Expected IllegalStateException because there are more than one resources with the same name");
        } catch (IllegalStateException ise) {
            assertTrue(ise.getMessage()
                    .contains("Multiple resources found for 'META-INF/native/lib" + resourceName + ".so'"));
        }
    }

    @SuppressJava6Requirement(reason = "uses Java 7+ Throwable#getSuppressed but is guarded by version checks")
    private static void verifySuppressedException(UnsatisfiedLinkError error,
            Class<?> expectedSuppressedExceptionClass) {
        try {
            Throwable[] suppressed = error.getCause().getSuppressed();
            assertTrue(suppressed.length == 1);
            assertTrue(suppressed[0] instanceof UnsatisfiedLinkError);
            suppressed = (suppressed[0]).getSuppressed();
            assertTrue(suppressed.length == 1);
            assertTrue(expectedSuppressedExceptionClass.isInstance(suppressed[0]));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
