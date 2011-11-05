/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.local;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;

/**
 * The default {@link LocalServerChannelFactory} implementation.
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class DefaultLocalServerChannelFactory implements LocalServerChannelFactory {

    private final ChannelSink sink = new LocalServerChannelSink();

    /**
     * Creates a new instance.
     */
    public DefaultLocalServerChannelFactory() {
        super();
    }

    @Override
    public LocalServerChannel newChannel(ChannelPipeline pipeline) {
        return DefaultLocalServerChannel.create(this, pipeline, sink);
    }

    /**
     * Does nothing because this implementation does not require any external
     * resources.
     */
    @Override
    public void releaseExternalResources() {
        // Unused
    }
}
