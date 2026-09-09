package me.edgan.redditslide.util;

/** Created by Carlos on 9/12/2016. */
import android.content.Context;
import com.nostra13.universalimageloader.core.assist.ContentLengthInputStream;
import com.nostra13.universalimageloader.core.download.BaseImageDownloader;
import java.io.IOException;
import java.io.InputStream;
import me.edgan.redditslide.Reddit;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jspecify.annotations.NullMarked;

/**
 * Implementation of ImageDownloader which uses {@link com.squareup.okhttp.OkHttpClient} for image
 * stream retrieving.
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @author Leo Link (mr[dot]leolink[at]gmail[dot]com)
 */
@NullMarked
public class OkHttpImageDownloader extends BaseImageDownloader {

    public OkHttpImageDownloader(Context context) {
        super(context);
    }

    @Override
    protected InputStream getStreamFromNetwork(String imageUri, Object extra) throws IOException {
        Request request = new Request.Builder().url(imageUri).build();
        Response response = Reddit.client.newCall(request).execute();
        ResponseBody responseBody = response.body();
        // An error response is not an image, however much its Content-Type insists otherwise:
        // Reddit's preview CDN answers a missing asset with a 404 whose body is a PNG reading
        // "If you are looking for an image, it was probably deleted.". Handing that stream back
        // would decode it as the post's picture and, worse, cache it on disk under the post's URL.
        // Throwing instead makes UIL report a load failure and write nothing.
        if (!response.isSuccessful() || responseBody == null) {
            final int code = response.code();
            response.close();
            final String message = "Image request failed: HTTP " + code + " " + imageUri;
            // 404/410 mean the asset is gone for good, which is worth remembering; anything else
            // (a rate limit, a proxy hiccup, no network) deserves another try later.
            if (code == 404 || code == 410) {
                throw new ImageNotFoundException(message);
            }
            throw new IOException(message);
        }
        InputStream inputStream = responseBody.byteStream();
        int contentLength = (int) responseBody.contentLength();
        return new ContentLengthInputStream(inputStream, contentLength);
    }
}
