package me.edgan.redditslide.ImgurAlbum;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

import com.afollestad.materialdialogs.MaterialDialog;

import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.util.ImgurUtils;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.ProgressRequestBody;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class UploadImgur extends AsyncTask<Uri, Integer, JSONObject> {
    public Context c;
    public MaterialDialog dialog;

    @Override
    protected JSONObject doInBackground(Uri... sub) {
        File bitmap = ImgurUtils.createFile(sub[0], c);

        final OkHttpClient client = Reddit.client;

        try {
            RequestBody formBody =
                    new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                    "image",
                                    bitmap.getName(),
                                    RequestBody.create(MediaType.parse("image/*"), bitmap))
                            .build();

            ProgressRequestBody body = new ProgressRequestBody(formBody, this::publishProgress);

            Request request =
                    new Request.Builder()
                            .header("Authorization", "Client-ID bef87913eb202e9")
                            .url("https://api.imgur.com/3/image")
                            .post(body)
                            .build();

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return new JSONObject(response.body().string());
        } catch (Exception e) {
            LogUtil.e(e, "UploadImgur.doInBackground failed");
        }
        return null;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        dialog.setProgress(values[0]);
        LogUtil.v("Progress:" + values[0]);
    }
}
