/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class WebSocketExtensionUtil {

    private static final String EXTENSION_SEPARATOR = ",";
    private static final String PARAMETER_SEPARATOR = ";";
    private static final String PARAMETER_EQUAL = "=";

    private WebSocketExtensionUtil() {
        // TODO Auto-generated constructor stub
    }

    public static Map<String, Map<String, String>> extractExtensions(String extensionHeader) {
        String[] rawExtensions = extensionHeader.split(EXTENSION_SEPARATOR);
        if (rawExtensions.length > 0) {
            Map<String, Map<String, String>> extensions =
                    new HashMap<String, Map<String, String>>(rawExtensions.length);
            for (String rawExtension : rawExtensions) {
                String[] extensionParameters = rawExtension.split(PARAMETER_SEPARATOR);
                String name = extensionParameters[0].trim();
                HashMap<String, String> parameters =
                        new HashMap<String, String>(extensionParameters.length - 1);
                if (extensionParameters.length > 1) {
                    for (int i = 1; i < extensionParameters.length; i++) {
                        String[] parameterSplited = extensionParameters[i].split(PARAMETER_EQUAL);
                        parameters.put(parameterSplited[0].trim(),
                                parameterSplited.length > 1 ? parameterSplited[1].trim() : null);
                    }
                }
                extensions.put(name, parameters);
            }
            return extensions;
        } else {
            return Collections.emptyMap();
        }
    }

    public static String appendExtension(String currentHeaderValue, String extensionName,
            Map<String, String> extensionParameters) {

        StringBuilder newHeaderValue = new StringBuilder();
        if (currentHeaderValue != null && !currentHeaderValue.trim().isEmpty()) {
            newHeaderValue.append(currentHeaderValue);
            newHeaderValue.append(EXTENSION_SEPARATOR);
        }
        newHeaderValue.append(extensionName);
        boolean isFirst = true;
        for (Entry<String, String> extensionParameter : extensionParameters.entrySet()) {
            if (isFirst) {
                newHeaderValue.append(PARAMETER_SEPARATOR);
            } else {
                isFirst = false;
            }
            newHeaderValue.append(extensionParameter.getKey());
            if (extensionParameter.getValue() != null) {
                newHeaderValue.append(PARAMETER_EQUAL);
                newHeaderValue.append(extensionParameter.getValue());
            }
        }
        return newHeaderValue.toString();
    }

}
