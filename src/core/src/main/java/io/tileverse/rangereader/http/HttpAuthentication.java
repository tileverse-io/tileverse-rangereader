/*
 * (c) Copyright 2025 Multiversio LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tileverse.rangereader.http;

import java.net.http.HttpRequest.Builder;

/**
 * Interface for HTTP authentication strategies used by HttpRangeReader.
 * <p>
 * Implementations of this interface provide authentication for HTTP requests
 * by adding appropriate headers or other authentication mechanisms to the
 * request builder.
 */
public interface HttpAuthentication {

    /**
     * Apply authentication to an HTTP request.
     *
     * @param requestBuilder The HTTP request builder to authenticate
     * @return The same request builder with authentication applied
     */
    Builder authenticate(Builder requestBuilder);
}
