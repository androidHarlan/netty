/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.http.multipart;

import org.jboss.netty.buffer.ChannelBuffer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Extended interface for InterfaceHttpData
 */
public interface HttpData extends InterfaceHttpData {
    /**
     * Set the maxSize for this HttpData. When limit will be reached, an exception will be raised.
     * Setting it to (-1) means no limitation.
     *
     * By default, to be set from the HttpDataFactory.
     * @param maxSize
     */
    void setMaxSize(long maxSize);

    /**
     * Check if the new size is not reaching the max limit allowed.
     * The limit is always computed in term of bytes.
     * @param newSize
     * @throws IOException
     */
    void checkSize(long newSize) throws IOException;

    /**
     * Set the content from the ChannelBuffer (erase any previous data)
     *
     * @param buffer
     *            must be not null
     * @exception IOException
     */
    void setContent(ChannelBuffer buffer) throws IOException;

    /**
     * Add the content from the ChannelBuffer
     *
     * @param buffer
     *            must be not null except if last is set to False
     * @param last
     *            True of the buffer is the last one
     * @exception IOException
     */
    void addContent(ChannelBuffer buffer, boolean last) throws IOException;

    /**
     * Set the content from the file (erase any previous data)
     *
     * @param file
     *            must be not null
     * @exception IOException
     */
    void setContent(File file) throws IOException;

    /**
     * Set the content from the inputStream (erase any previous data)
     *
     * @param inputStream
     *            must be not null
     * @exception IOException
     */
    void setContent(InputStream inputStream) throws IOException;

    /**
     *
     * @return True if the InterfaceHttpData is completed (all data are stored)
     */
    boolean isCompleted();

    /**
     * Returns the size in byte of the InterfaceHttpData
     *
     * @return the size of the InterfaceHttpData
     */
    long length();

    /**
     * Returns the defined length of the HttpData.
     *
     * If no Content-Length is provided in the request, the defined length is
     * always 0 (whatever during decoding or in final state).
     *
     * If Content-Length is provided in the request, this is this given defined
     * length. This value does not change, whatever during decoding or in the final state.
     *
     * This method could be used for instance to know the amount of bytes to be transmitted
     * for one particular HttpData, for example one {@link FileUpload} or any known big {@link Attribute}.
     *
     * @return the defined length of the HttpData
     */
    long definedLength();

    /**
     * Deletes the underlying storage for a file item, including deleting any
     * associated temporary disk file.
     */
    void delete();

    /**
     * Returns the contents of the file item as an array of bytes.
     *
     * @return the contents of the file item as an array of bytes.
     * @exception IOException
     */
    byte[] get() throws IOException;

    /**
     * Returns the content of the file item as a ChannelBuffer
     *
     * @return the content of the file item as a ChannelBuffer
     * @throws IOException
     */
    ChannelBuffer getChannelBuffer() throws IOException;

    /**
     * Returns a ChannelBuffer for the content from the current position with at
     * most length read bytes, increasing the current position of the Bytes
     * read. Once it arrives at the end, it returns an EMPTY_BUFFER and it
     * resets the current position to 0.
     *
     * @return a ChannelBuffer for the content from the current position or an
     *         EMPTY_BUFFER if there is no more data to return
     */
    ChannelBuffer getChunk(int length) throws IOException;

    /**
     * Returns the contents of the file item as a String, using the default
     * character encoding.
     *
     * @return the contents of the file item as a String, using the default
     *         character encoding.
     */
    String getString() throws IOException;

    /**
     * Returns the contents of the file item as a String, using the specified
     * charset.
     *
     * @param encoding
     *            the charset to use
     * @return the contents of the file item as a String, using the specified
     *         charset.
     * @exception IOException
     */
    String getString(Charset encoding) throws IOException;

    /**
     * Set the Charset passed by the browser if defined
     *
     * @param charset
     *            Charset to set - must be not null
     */
    void setCharset(Charset charset);

    /**
     * Returns the Charset passed by the browser or null if not defined.
     *
     * @return the Charset passed by the browser or null if not defined.
     */
    Charset getCharset();

    /**
     * A convenience method to write an uploaded item to disk. If a previous one
     * exists, it will be deleted. Once this method is called, if successful,
     * the new file will be out of the cleaner of the factory that creates the
     * original InterfaceHttpData object.
     *
     * @param dest
     *            destination file - must be not null
     * @return True if the write is successful
     * @exception IOException
     */
    boolean renameTo(File dest) throws IOException;

    /**
     * Provides a hint as to whether or not the file contents will be read from
     * memory.
     *
     * @return True if the file contents is in memory.
     */
    boolean isInMemory();

    /**
     *
     * @return the associated File if this data is represented in a file
     * @exception IOException
     *                if this data is not represented by a file
     */
    File getFile() throws IOException;

}
