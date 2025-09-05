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
import java.net.URISyntaxException;

/**
 * Parser for S3-compatible URLs that extracts bucket and key components from various URL formats.
 *
 * <p>This parser is required because AWS S3 SDK's {@link software.amazon.awssdk.services.s3.S3Client.Builder}
 * and similar S3-compatible SDKs do not accept complete URLs. Instead, they require separate configuration
 * of endpoints, bucket names, and object keys. The SDK builders typically accept:
 * <ul>
 * <li>{@code endpointOverride(URI)} - for custom S3-compatible service endpoints</li>
 * <li>{@code forcePathStyle(boolean)} - to control URL style preference</li>
 * </ul>
 * while operations like {@link software.amazon.awssdk.services.s3.model.GetObjectRequest} require:
 * <ul>
 * <li>{@code bucket(String)} - the bucket name component</li>
 * <li>{@code key(String)} - the object key component</li>
 * </ul>
 *
 * <p>This parser bridges the gap between complete S3 URLs (commonly used in configuration files,
 * command-line tools, and data pipelines) and the decomposed parameters required by S3 SDKs.
 *
 * <h2>SDK Integration Example</h2>
 * <pre>{@code
 * // Instead of trying to pass a complete URL to the SDK (which doesn't work):
 * // S3Client.builder().endpointOverride("http://localhost:9000/my-bucket/file.txt") // Invalid
 *
 * // Parse the URL to extract components:
 * String s3Url = "http://localhost:9000/my-bucket/path/to/file.txt";
 * S3Location location = S3CompatibleUrlParser.parseS3Url(s3Url);
 *
 * // Configure S3Client with endpoint only:
 * S3Client client = S3Client.builder()
 *     .endpointOverride(URI.create("http://localhost:9000"))  // Endpoint only
 *     .forcePathStyle(true)                                   // Required for MinIO
 *     .build();
 *
 * // Use extracted bucket and key in requests:
 * GetObjectRequest request = GetObjectRequest.builder()
 *     .bucket(location.bucket())  // "my-bucket"
 *     .key(location.key())        // "path/to/file.txt"
 *     .range("bytes=0-1023")
 *     .build();
 *
 * GetObjectResponse response = client.getObject(request);
 * }</pre>
 *
 * <p>This parser supports multiple S3 URL formats used across different S3-compatible services
 * including Amazon S3, MinIO, Google Cloud Storage, DigitalOcean Spaces, Wasabi, and other
 * S3 API-compatible storage providers.
 *
 * <h2>Supported URL Formats</h2>
 *
 * <h3>S3 URI Format</h3>
 * <p>The canonical S3 URI format used by AWS CLI and SDKs:
 * <pre>{@code
 * s3://bucket-name/path/to/object
 * s3://my-bucket/folder/file.txt
 * s3://my-bucket/                    // bucket root
 * s3://my-bucket                     // bucket only
 * }</pre>
 *
 * <h3>AWS Virtual Hosted-Style URLs</h3>
 * <p>AWS S3's preferred HTTP URL format where the bucket name is part of the hostname:
 * <pre>{@code
 * https://bucket-name.s3.amazonaws.com/path/to/object
 * https://bucket-name.s3.region.amazonaws.com/path/to/object
 *
 * // Examples:
 * https://my-bucket.s3.amazonaws.com/folder/file.txt
 * https://my-bucket.s3.us-west-2.amazonaws.com/folder/file.txt
 * https://my-bucket.s3-us-west-2.amazonaws.com/folder/file.txt
 * }</pre>
 * <p><strong>Note:</strong> This is AWS's recommended format and required for new regions.
 *
 * <h3>AWS Path-Style URLs</h3>
 * <p>Legacy AWS S3 format where bucket name is part of the path:
 * <pre>{@code
 * https://s3.amazonaws.com/bucket-name/path/to/object
 * https://s3.region.amazonaws.com/bucket-name/path/to/object
 *
 * // Examples:
 * https://s3.amazonaws.com/my-bucket/folder/file.txt
 * https://s3.us-west-2.amazonaws.com/my-bucket/folder/file.txt
 * }</pre>
 * <p><strong>Note:</strong> AWS is deprecating path-style URLs for new buckets and regions.
 *
 * <h3>MinIO and Local Development</h3>
 * <p>MinIO and other local S3-compatible services typically use path-style URLs with custom endpoints:
 * <pre>{@code
 * http://localhost:9000/bucket-name/path/to/object
 * https://minio.example.com/bucket-name/path/to/object
 * http://192.168.1.100:9000/bucket-name/path/to/object
 *
 * // Examples:
 * http://localhost:9000/my-bucket/folder/file.txt
 * https://minio.company.internal/my-bucket/folder/file.txt
 * }</pre>
 *
 * <h3>Other S3-Compatible Services</h3>
 * <p>Various cloud storage providers offer S3-compatible APIs with their own endpoint formats:
 * <pre>{@code
 * // Google Cloud Storage (when using S3 compatibility)
 * https://storage.googleapis.com/bucket-name/path/to/object
 *
 * // DigitalOcean Spaces
 * https://nyc3.digitaloceanspaces.com/bucket-name/path/to/object
 *
 * // Wasabi
 * https://s3.wasabisys.com/bucket-name/path/to/object
 *
 * // Custom enterprise endpoints
 * https://s3.company.internal/bucket-name/path/to/object
 * }</pre>
 *
 * <h2>Parsing Behavior</h2>
 *
 * <h3>Automatic URL Decoding</h3>
 * <p>The parser automatically decodes percent-encoded characters in object keys:
 * <pre>{@code
 * "file%20with%20spaces.txt" → "file with spaces.txt"
 * "path%2Bwith%26symbols.txt" → "path+with&symbols.txt"
 * }</pre>
 *
 * <h3>Empty Keys</h3>
 * <p>URLs pointing to bucket roots return empty string keys:
 * <pre>{@code
 * s3://my-bucket/     → bucket="my-bucket", key=""
 * s3://my-bucket      → bucket="my-bucket", key=""
 * }</pre>
 *
 * <h3>Detection Strategy</h3>
 * <ol>
 * <li><strong>S3 URI scheme:</strong> URLs starting with {@code s3://} are parsed as S3 URIs</li>
 * <li><strong>AWS Virtual Hosted-Style:</strong> HTTPS URLs with {@code .s3.} and {@code amazonaws.com} in hostname</li>
 * <li><strong>Path-Style Fallback:</strong> All other HTTP/HTTPS URLs are treated as path-style</li>
 * </ol>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Parse various URL formats
 * S3Location awsUri = S3CompatibleUrlParser.parseS3Url("s3://my-bucket/path/file.txt");
 * S3Location awsVirtual = S3CompatibleUrlParser.parseS3Url("https://my-bucket.s3.amazonaws.com/path/file.txt");
 * S3Location minioLocal = S3CompatibleUrlParser.parseS3Url("http://localhost:9000/my-bucket/path/file.txt");
 *
 * // Extract components for S3 SDK usage
 * String bucket = awsUri.bucket(); // "my-bucket"
 * String key = awsUri.key();       // "path/file.txt"
 *
 * // Configure S3Client for the endpoint
 * URI endpointUri = URI.create("http://localhost:9000");
 * S3Client client = S3Client.builder()
 *     .endpointOverride(endpointUri)
 *     .forcePathStyle(true)  // Required for most non-AWS S3-compatible services
 *     .build();
 *
 * // Use with AWS S3 SDK
 * GetObjectRequest request = GetObjectRequest.builder()
 *     .bucket(bucket)
 *     .key(key)
 *     .range("bytes=0-1023")
 *     .build();
 *
 * GetObjectResponse response = client.getObject(request);
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * <p>The parser throws {@link IllegalArgumentException} for:
 * <ul>
 * <li>Unsupported URL schemes (not s3, http, or https)</li>
 * <li>Malformed URLs that cannot be parsed</li>
 * <li>HTTP/HTTPS URLs without bucket information in the path</li>
 * <li>Invalid URI syntax</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class contains only static methods and is thread-safe.
 *
 * <h2>Limitations</h2>
 * <ul>
 * <li>Does not validate bucket name compliance with S3 naming rules</li>
 * <li>Does not validate object key length or character restrictions</li>
 * <li>Assumes path-style for ambiguous non-AWS endpoints</li>
 * <li>Does not handle S3 access point ARNs</li>
 * <li>Does not support S3 Transfer Acceleration endpoints</li>
 * <li>Does not automatically configure S3Client - endpoint extraction requires separate logic</li>
 * </ul>
 *
 * @see software.amazon.awssdk.services.s3.S3Client.Builder
 * @see software.amazon.awssdk.services.s3.model.GetObjectRequest.Builder
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/VirtualHosting.html">AWS S3 Virtual Hosting</a>
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/RESTAPI.html">AWS S3 REST API</a>
 * @see <a href="https://min.io/docs/minio/linux/developers/minio-drivers.html">MinIO S3 Compatibility</a>
 */
class S3CompatibleUrlParser {

    public static S3Reference parseS3Url(String uri) {
        try {
            return parseS3Url(new URI(uri));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid S3 URL: " + uri, e);
        }
    }

    public static S3Reference parseS3Url(URI uri) {
        if ("s3".equals(uri.getScheme())) {
            return parseS3Uri(uri);
        }

        if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
            return parseHttpS3Url(uri);
        }

        throw new IllegalArgumentException("Unsupported scheme: " + uri.getScheme() + " in " + uri);
    }

    private static S3Reference parseS3Uri(URI uri) {
        String bucket = uri.getHost();
        String key = uri.getPath();

        if (key != null && key.startsWith("/")) {
            key = key.substring(1);
        }

        // S3 URIs use default AWS endpoints
        return new S3Reference(null, bucket, key != null ? key : "");
    }

    private static S3Reference parseHttpS3Url(URI uri) {
        String host = uri.getHost();
        String path = uri.getPath();

        if (isAwsVirtualHostedStyle(host)) {
            // AWS virtual hosted-style
            String bucket = host.substring(0, host.indexOf(".s3"));
            String key = path != null && path.startsWith("/") ? path.substring(1) : (path != null ? path : "");

            // Use null endpoint for default AWS (or extract region-specific endpoint)
            URI endpoint = extractAwsEndpoint(host);
            return new S3Reference(endpoint, bucket, key);
        }

        if (isAwsPathStyle(host)) {
            // AWS path-style
            S3Reference pathStyleLocation = parsePathStyleComponents(uri);
            URI endpoint = extractAwsEndpoint(host);
            return new S3Reference(endpoint, pathStyleLocation.bucket(), pathStyleLocation.key());
        }

        // Non-AWS S3-compatible service
        S3Reference pathStyleLocation = parsePathStyleComponents(uri);
        URI customEndpoint = buildCustomEndpoint(uri);
        return new S3Reference(customEndpoint, pathStyleLocation.bucket(), pathStyleLocation.key());
    }

    private static boolean isAwsVirtualHostedStyle(String host) {
        return host != null
                && ((host.contains(".s3.") && host.contains("amazonaws.com"))
                        || (host.contains(".s3-") && host.contains("amazonaws.com")));
    }

    private static boolean isAwsPathStyle(String host) {
        return host != null
                && (host.equals("s3.amazonaws.com") || (host.startsWith("s3.") && host.endsWith(".amazonaws.com")));
    }

    private static URI extractAwsEndpoint(String host) {
        // For most AWS regions, we can use null to let SDK use default endpoints
        // But if you want explicit region endpoints:
        if (host.equals("s3.amazonaws.com")) {
            return null; // Default us-east-1
        }

        // Extract region and build regional endpoint
        String region = extractAwsRegion(host);
        if (region != null && !region.equals("us-east-1")) {
            return URI.create("https://s3." + region + ".amazonaws.com");
        }

        return null; // Use SDK default
    }

    private static String extractAwsRegion(String host) {
        if (host.contains(".s3.") && host.contains("amazonaws.com")) {
            // Virtual hosted: bucket.s3.region.amazonaws.com
            String[] parts = host.split("\\.");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("s3".equals(parts[i]) && i + 1 < parts.length) {
                    String regionId = parts[i + 1];
                    return "amazonaws".equals(regionId) ? null : regionId;
                }
            }
        } else if (host.startsWith("s3.") && host.endsWith(".amazonaws.com")) {
            // Path-style: s3.region.amazonaws.com
            String regionPart = host.substring(3, host.length() - ".amazonaws.com".length());
            return regionPart.isEmpty() ? null : regionPart;
        }
        return null;
    }

    private static URI buildCustomEndpoint(URI uri) {
        StringBuilder endpoint = new StringBuilder();
        endpoint.append(uri.getScheme()).append("://").append(uri.getHost());

        if (uri.getPort() != -1) {
            endpoint.append(":").append(uri.getPort());
        }

        return URI.create(endpoint.toString());
    }

    private static S3Reference parsePathStyleComponents(URI uri) {
        String path = uri.getPath();

        if (path == null || path.length() <= 1) {
            // Return endpoint-only location - bucket/key can be set later
            return new S3Reference(buildCustomEndpoint(uri), null, null);
        }

        String pathWithoutLeadingSlash = path.substring(1);
        int firstSlash = pathWithoutLeadingSlash.indexOf('/');

        if (firstSlash == -1) {
            return new S3Reference(null, pathWithoutLeadingSlash, "");
        }

        String bucket = pathWithoutLeadingSlash.substring(0, firstSlash);
        String key = pathWithoutLeadingSlash.substring(firstSlash + 1);

        return new S3Reference(null, bucket, key); // endpoint will be set by caller
    }
}
