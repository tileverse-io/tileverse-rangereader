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
package io.tileverse.rangereader.s3;

import java.net.URI;

/**
 * Represents the components of an S3-compatible URL.
 *
 * @param endpoint the service endpoint URI (null for AWS S3 URIs using default endpoints)
 * @param bucket the bucket name
 * @param key the object key (empty string for bucket root)
 */
record S3Reference(URI endpoint, String bucket, String key) {

    S3Reference() {
        this(null, null, null);
    }
    /**
     * Returns true if this location uses the default AWS S3 endpoint.
     */
    public boolean isDefaultAwsEndpoint() {
        return endpoint == null;
    }

    /**
     * Returns true if this location requires path-style access.
     * Non-AWS endpoints typically require path-style access.
     */
    public boolean requiresPathStyle() {
        return endpoint != null;
    }

    @Override
    public String toString() {
        return "%s/%s/%s".formatted(endpoint, bucket, key);
    }

    S3Reference withEndpoint(URI endpoint) {
        return new S3Reference(endpoint, bucket(), key());
    }

    S3Reference withBucket(String bucket) {
        return new S3Reference(endpoint(), bucket, key());
    }

    S3Reference withKey(String key) {
        return new S3Reference(endpoint(), bucket(), key);
    }
}
