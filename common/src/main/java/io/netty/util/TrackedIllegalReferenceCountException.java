/*
 * Copyright 2019 The Netty Project
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
package io.netty.util;

import io.netty.util.internal.StringUtil;

/**
 * A special {@link IllegalReferenceCountException} with the ability to track access records
 */
public final class TrackedIllegalReferenceCountException extends IllegalReferenceCountException {

    private static final long serialVersionUID = 1374377399979428484L;

    public TrackedIllegalReferenceCountException(String message,
                                                 String accessRecords,
                                                 IllegalReferenceCountException origin) {
        super(message + StringUtil.NEWLINE + accessRecords, origin);
    }
}
