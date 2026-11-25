/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.jsonschema;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;

/**
 * Holds the current Ballerina module and creates module-defined errors.
 *
 * @since 0.1.0
 */
public final class ModuleUtils {
    private static final String ERROR_TYPE_NAME = "Error";

    private static Module module;

    private ModuleUtils() {
    }

    @SuppressWarnings("unused")
    public static void setModule(Environment env) {
        module = env.getCurrentModule();
    }

    public static BError createError(String errorMessage) {
        return ErrorCreator.createError(module, ERROR_TYPE_NAME, StringUtils.fromString(errorMessage), null, null);
    }
}
