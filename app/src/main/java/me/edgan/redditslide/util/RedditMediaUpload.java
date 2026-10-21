package me.edgan.redditslide.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.markdown.UploadedImage;
import net.dean.jraw.http.HttpRequest;
import net.dean.jraw.http.RestResponse;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jspecify.annotations.NullMarked;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Uploads an image to Reddit's own media bucket so it can be referenced inline in a comment or
 * self-post body.
 *
 * <p>The flow mirrors the reddit.com web client:
 *
 * <ol>
 *   <li>POST {@code /api/media/asset.json} (authenticated, via JRAW) to obtain a presigned S3
 *       upload target and form fields.
 *   <li>POST the raw bytes to that S3 endpoint as multipart form data.
 *   <li>Parse the S3 XML response for the object {@code <Key>}; that key becomes the {@code id} of
 *       the {@code img} element in {@code richtext_json}.
 * </ol>
 */
@NullMarked
public final class RedditMediaUpload {

    private RedditMediaUpload() {}

    /**
     * Uploads the image at {@code imageUri} to Reddit for inline use in a comment/self-post body.
     * Must be called off the main thread.
     *
     * @return the uploaded image (name + S3 key), never null on success
     * @throws IOException on any network/parse failure
     */
    @NonNull
    public static UploadedImage upload(Context context, Uri imageUri) throws IOException {
        String[] xmlAndName = doUpload(context, imageUri);
        String key = parseTagFromXml(xmlAndName[0], "Key");
        if (key == null) {
            throw new IOException("Could not parse uploaded image key");
        }
        return new UploadedImage(xmlAndName[1] == null ? key : xmlAndName[1], key);
    }

    /**
     * Uploads the image at {@code imageUri} to Reddit and returns the public media URL, suitable for
     * submitting a native Reddit image post ({@code kind=image}). Must be called off the main
     * thread.
     *
     * @return the S3 {@code Location} URL of the uploaded image
     * @throws IOException on any network/parse failure
     */
    @NonNull
    public static String uploadForPostUrl(Context context, Uri imageUri) throws IOException {
        String[] xmlAndName = doUpload(context, imageUri);
        String location = parseTagFromXml(xmlAndName[0], "Location");
        if (location == null) {
            throw new IOException("Could not parse uploaded image URL");
        }
        return location;
    }

    /**
     * Uploads the image at {@code imageUri} to Reddit and returns its media id, suitable for a
     * gallery post item ({@code media_id}). Must be called off the main thread.
     *
     * @return the S3 {@code Key} of the uploaded image, which Reddit uses as its media id
     * @throws IOException on any network/parse failure
     */
    @NonNull
    public static String uploadForGalleryMediaId(Context context, Uri imageUri) throws IOException {
        return galleryMediaId(doUpload(context, imageUri)[0]);
    }

    /** Pulls the gallery {@code media_id} (the object's S3 {@code Key}) out of the S3 response. */
    @VisibleForTesting
    public static String galleryMediaId(String s3ResponseXml) throws IOException {
        String key = parseTagFromXml(s3ResponseXml, "Key");
        if (key == null || key.isEmpty()) {
            throw new IOException("Could not parse uploaded image key");
        }
        return key;
    }

    /**
     * Form fields for the {@code /api/media/asset.json} lease. Only {@code mimetype} is sent: a
     * lease that also carries {@code filepath} uploads fine, but every post that uses it reports
     * the image as {@code "status": "failed"} in {@code media_metadata}, so galleries show as text
     * and inline images stay as "Processing img" outside reddit.com.
     */
    @VisibleForTesting
    public static Map<String, String> leaseParams(String mimeType) {
        Map<String, String> params = new HashMap<>();
        params.put("mimetype", mimeType);
        return params;
    }

    /**
     * Returns the node holding the S3 {@code action} and {@code fields}: the top level of a bare
     * lease reply, or {@code args} of the older wrapped one ({@code {"args": ..., "asset": ...}}).
     */
    @VisibleForTesting
    public static @Nullable JsonNode uploadForm(@Nullable JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode form = root.has("args") ? root.path("args") : root;
        return form.path("action").asText().isEmpty() ? null : form;
    }

    /**
     * Runs the two-step upload and returns {@code [s3ResponseXml, fileName]}.
     */
    private static String[] doUpload(Context context, Uri imageUri) throws IOException {
        if (Authentication.reddit == null) {
            throw new IOException("Not signed in");
        }

        ContentResolver contentResolver = context.getContentResolver();

        String mimeType = contentResolver.getType(imageUri);
        String extension = "jpg";
        if (mimeType != null) {
            String extensionFromMimeType =
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extensionFromMimeType != null) {
                extension = extensionFromMimeType;
            }
        } else {
            mimeType = "image/jpeg";
        }

        // Step 1: ask Reddit for an upload lease.
        Map<String, String> assetParams = leaseParams(mimeType);

        HttpRequest assetRequest =
                Authentication.reddit
                        .request()
                        .path("/api/media/asset.json")
                        .query("raw_json", "1")
                        .post(assetParams)
                        .build();

        RestResponse assetResponse;
        try {
            assetResponse = Authentication.reddit.execute(assetRequest);
        } catch (Exception e) {
            throw new IOException("asset.json request failed", e);
        }

        JsonNode form = uploadForm(assetResponse.getJson());
        if (form == null) {
            throw new IOException("Unexpected asset.json response: " + assetResponse.getRaw());
        }

        String action = form.path("action").asText();
        String s3Url = action.startsWith("http") ? action : "https:" + action;

        MultipartBody.Builder multipartBuilder =
                new MultipartBody.Builder().setType(MultipartBody.FORM);
        for (JsonNode field : form.path("fields")) {
            multipartBuilder.addFormDataPart(
                    field.path("name").asText(), field.path("value").asText());
        }

        // Step 2: read the image bytes and push them to S3.
        byte[] bytes;
        try (InputStream inputStream = contentResolver.openInputStream(imageUri)) {
            if (inputStream == null) {
                throw new IOException("Could not open image stream");
            }
            bytes = readAllBytes(inputStream);
        }

        RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mimeType));
        multipartBuilder.addFormDataPart("file", "post_image." + extension, fileBody);

        Request s3Request = new Request.Builder().url(s3Url).post(multipartBuilder.build()).build();

        try (Response s3Response = Reddit.client.newCall(s3Request).execute()) {
            if (!s3Response.isSuccessful()) {
                throw new IOException("S3 upload failed: " + s3Response.code());
            }
            String xml = s3Response.body().string();
            String name = getFileName(context, imageUri);
            return new String[] {xml, name};
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    /** Pulls the text of the first {@code <tagName>} element out of the S3 XML response. */
    private static @Nullable String parseTagFromXml(String response, String tagName)
            throws IOException {
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new StringReader(response));

            boolean inTag = false;
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (tagName.equals(parser.getName())) {
                        inTag = true;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (inTag) {
                        return parser.getText();
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            throw new IOException("Failed parsing S3 response", e);
        }
        return null;
    }

    private static @Nullable String getFileName(Context context, Uri uri) {
        ContentResolver contentResolver = context.getContentResolver();
        if (contentResolver == null) return null;
        try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    String fileName = cursor.getString(nameIndex);
                    if (fileName != null && fileName.contains(".")) {
                        fileName = fileName.substring(0, fileName.lastIndexOf('.'));
                    }
                    return fileName;
                }
            }
        } catch (Exception ignored) {
            // A content provider that will not answer leaves the name null, which upload() at line
            // 65 substitutes with the S3 key.
        }
        return null;
    }
}
