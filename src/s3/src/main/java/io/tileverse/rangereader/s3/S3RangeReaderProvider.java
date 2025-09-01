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

import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.s3.S3RangeReader.Builder;
import io.tileverse.rangereader.spi.AbstractRangeReaderProvider;
import io.tileverse.rangereader.spi.RangeReaderConfig;
import io.tileverse.rangereader.spi.RangeReaderParameter;
import io.tileverse.rangereader.spi.RangeReaderProvider;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * {@link RangeReaderProvider} implementation for AWS S3.
 * <p>
 * The {@link RangeReaderConfig#uri() URI} is used to extract the bucket and object name from an S3 URI.
 * <p>
 * An S3 URL, or Amazon Simple Storage Service Uniform Resource Locator, refers to the
 * address used to access resources stored within AWS S3. There
 * are several forms of S3 URLs, depending on the context and desired access
 * method:
 *
 * <ul>
 * <li>{@code s3://} URI: This is the canonical URI format for referencing
 * objects within S3. It is commonly used within AWS
 * services, tools, and libraries for internal referencing. For example:
 * <pre>
 * {@literal s3://your-bucket-name/your-object-name}
 * </pre>
 * <li>Public HTTP/HTTPS URLs: If an object is configured for public access, it
 * can be accessed directly via a standard HTTP or HTTPS URL. These URLs are
 * typically in the format:
 * <pre>
 * {@literal https://your-bucket-name.s3.your-aws-region.amazonaws.com/your-object-name}
 * </pre>
 * </ul>
 * When {@code http/s} URL schemes are used, {@link #canProcessHeaders(Map)} disambiguates
 * by checking if a header starting with {@code x-amz-} was returned from the HEAD request.
 */
public class S3RangeReaderProvider extends AbstractRangeReaderProvider {

    /**
     * Key used as environment variable name to disable this range reader provider
     * <pre>
     * {@code export IO_TILEVERSE_RANGEREADER_S3=false}
     * </pre>
     */
    public static final String ENABLED_KEY = "IO_TILEVERSE_RANGEREADER_S3";
    /**
     * This range reader implementation's {@link #getId() unique identifier}
     */
    public static final String ID = "s3";

    /**
     * Create a new S3RangeReaderProvider with support for caching decorator
     */
    public S3RangeReaderProvider() {
        super(true);
    }

    /**
     * A {@link RangeReaderParameter} to enable or disable S3 path style access. When enabled, requests will use
     * path-style addressing (e.g., {@code https://s3.amazonaws.com/bucket/key}). When disabled, virtual-hosted-style
     * addressing will be used instead (e.g., {@code https://bucket.s3.amazonaws.com/key}). This can be useful for
     * compatibility with S3-compatible storage systems that do not support virtual-hosted-style requests.
     */
    public static final RangeReaderParameter<Boolean> FORCE_PATH_STYLE = RangeReaderParameter.builder()
            .key("io.tileverse.rangereader.s3.force-path-style")
            .title("Enable S3 path style access")
            .description(
                    """
                    Enables S3 path style access. When enabled, requests will use path-style addressing
                    (e.g., https://s3.amazonaws.com/bucket/key). When disabled, virtual-hosted-style addressing
                    will be used instead (e.g., https://bucket.s3.amazonaws.com/key). This can be useful for
                    compatibility with S3-compatible storage systems that do not support virtual-hosted-style requests.
                    """)
            .type(Boolean.class)
            .group(ID)
            .defaultValue(true)
            .build();

    /**
     * The AWS access key ID to use for authentication. If not provided, the client will use the default credentials
     * provider chain to find credentials. The default credential provider chain looks for credentials in this order:
     * <ol>
     *     <li>Java System Properties - {@code aws.accessKeyId} and {@code aws.secretAccessKey}</li>
     *     <li>Environment Variables - {@code AWS_ACCESS_KEY_ID} and {@code AWS_SECRET_ACCESS_KEY}</li>
     *     <li>Web Identity Token File - from the path specified in the {@code AWS_WEB_IDENTITY_TOKEN_FILE} environment variable</li>
     *     <li>Shared Credentials File - at {@code ~/.aws/credentials}</li>
     *     <li>Amazon ECS Container Credentials - loaded from the endpoint specified in the {@code AWS_CONTAINER_CREDENTIALS_RELATIVE_URI} environment variable</li>
     *     <li>Amazon EC2 Instance Profile Credentials - loaded from the Amazon EC2 metadata service</li>
     * </ol>
     */
    public static final RangeReaderParameter<String> AWS_ACCESS_KEY_ID = RangeReaderParameter.builder()
            .key("io.tileverse.rangereader.s3.aws-access-key-id")
            .title("AWS Access Key ID")
            .description(
                    """
                    The AWS access key ID to use for authentication. If not provided, the client will use \
                    the default credentials provider chain to find credentials.
                    """)
            .type(String.class)
            .group(ID)
            .build();

    /**
     * The AWS secret access key to use for authentication. If not provided, the client will use the default credentials
     * provider chain to find credentials. The default credential provider chain looks for credentials in this order:
     * <ol>
     *     <li>Java System Properties - {@code aws.accessKeyId} and {@code aws.secretAccessKey}</li>
     *     <li>Environment Variables - {@code AWS_ACCESS_KEY_ID} and {@code AWS_SECRET_ACCESS_KEY}</li>
     *     <li>Web Identity Token File - from the path specified in the {@code AWS_WEB_IDENTITY_TOKEN_FILE} environment variable</li>
     *     <li>Shared Credentials File - at {@code ~/.aws/credentials}</li>
     *     <li>Amazon ECS Container Credentials - loaded from the endpoint specified in the {@code AWS_CONTAINER_CREDENTIALS_RELATIVE_URI} environment variable</li>
     *     <li>Amazon EC2 Instance Profile Credentials - loaded from the Amazon EC2 metadata service</li>
     * </ol>
     */
    public static final RangeReaderParameter<String> AWS_SECRET_ACCESS_KEY = RangeReaderParameter.builder()
            .key("io.tileverse.rangereader.s3.aws-secret-access-key")
            .title("AWS Secret Access Key")
            .description(
                    """
                    The AWS secret access key to use for authentication. If not provided, the client will use \
                    the default credentials provider chain to find credentials.
                    """)
            .type(String.class)
            .group(ID)
            .build();

    private static final List<RangeReaderParameter<?>> PARAMS =
            List.of(FORCE_PATH_STYLE, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return RangeReaderProvider.isEnabled(ENABLED_KEY);
    }

    @Override
    public String getDescription() {
        return "AWS S3 Range Reader";
    }

    @Override
    public int getOrder() {
        return -100; // High priority
    }

    @Override
    protected List<RangeReaderParameter<?>> buildParameters() {
        return PARAMS;
    }

    @Override
    protected RangeReader createInternal(RangeReaderConfig opts) throws IOException {
        URI uri = opts.uri();
        Builder builder = S3RangeReader.builder().uri(uri);
        opts.getParameter(FORCE_PATH_STYLE).ifPresent(builder::forcePathStyle);
        opts.getParameter(AWS_ACCESS_KEY_ID).ifPresent(builder::awsAccessKeyId);
        opts.getParameter(AWS_SECRET_ACCESS_KEY).ifPresent(builder::awsSecretAccessKey);
        return builder.build();
    }

    @Override
    public boolean canProcess(RangeReaderConfig config) {
        if (!RangeReaderConfig.matches(config, getId(), "s3", "http", "https")) {
            return false;
        }

        URI uri = config.uri();
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String query = uri.getQuery();

        if ("s3".equalsIgnoreCase(scheme)) {
            return true;
        }

        if (host != null) {
            if (host.endsWith(".s3.amazonaws.com")
                    || host.endsWith(".s3-accesspoint.amazonaws.com")
                    || host.endsWith(".s3-accelerate.amazonaws.com")) {
                return true;
            }
        }

        // Check for pre-signed URL parameters
        if (query != null) {
            return query.toUpperCase().contains("X-AMZ-ALGORITHM=")
                    && query.contains("X-AMZ-CREDENTIAL=")
                    && query.contains("X-AMZ-SIGNATURE=");
        }

        return false;
    }

    @Override
    public boolean canProcessHeaders(Map<String, List<String>> headers) {
        return headers.keySet().stream().anyMatch(h -> h.equalsIgnoreCase("x-amz-request-id"));
    }
}
