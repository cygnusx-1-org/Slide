package me.edgan.redditslide.Activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.widget.Toolbar;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerDialog;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.Views.CanvasView;
import me.edgan.redditslide.Views.DoEditorActions;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.FileUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;

/** Created by ccrama on 5/27/2015. */
public class Draw extends BaseActivity {

    public static Uri uri;
    public static DoEditorActions editor;
    CanvasView drawView;
    View color;
    Bitmap bitmap;
    boolean enabled;
    private final ActivityResultLauncher<CropImageContractOptions> cropImageLauncher =
            registerForActivityResult(new CropImageContract(), this::cropImageResult);

    @Override
    public void onCreate(Bundle savedInstance) {
        overrideSwipeFromAnywhere();
        disableSwipeBackLayout();
        super.onCreate(savedInstance);
        applyColorTheme("");
        setContentView(R.layout.activity_draw);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        drawView = (CanvasView) findViewById(R.id.paintView);
        drawView.setBaseColor(Color.parseColor("#303030"));
        color = findViewById(R.id.color);
        CropImageOptions cropImageOptions = new CropImageOptions();
        cropImageOptions.guidelines = CropImageView.Guidelines.ON;
        final CropImageContractOptions options =
                new CropImageContractOptions(uri, cropImageOptions);
        cropImageLauncher.launch(options);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        setupAppBar(R.id.toolbar, "", true, Color.parseColor("#212121"), R.id.toolbar);
    }

    public int getLastColor() {
        return Reddit.colors.getInt("drawColor", Palette.getDefaultAccent());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
        }
        if (id == R.id.done && enabled) {
            File image; // image to share
            // check to see if the cache/shared_images directory is present
            final File imagesDir =
                    new File(Draw.this.getCacheDir().toString() + File.separator + "shared_image");
            if (!imagesDir.exists()) {
                imagesDir.mkdir(); // create the folder if it doesn't exist
            } else {
                FileUtil.deleteFilesInDir(imagesDir);
            }

            try {
                // creates a file in the cache; filename will be prefixed with "img" and end with
                // ".png"
                image = File.createTempFile("img", ".png", imagesDir);
                FileOutputStream out = null;

                try {
                    // convert image to png
                    out = new FileOutputStream(image);
                    Bitmap.createBitmap(
                                    drawView.getBitmap(),
                                    0,
                                    (int) drawView.height,
                                    (int) drawView.right,
                                    (int) (drawView.bottom - drawView.height))
                            .compress(Bitmap.CompressFormat.JPEG, 100, out);
                } finally {
                    if (out != null) {
                        out.close();

                        final Uri contentUri = FileUtil.getFileUri(image, this);
                        if (contentUri != null) {
                            Intent intent = FileUtil.getFileIntent(image, new Intent(), this);
                            setResult(RESULT_OK, intent);
                        } else {
                            // todo error Toast.makeText(this, getString(R.string.err_share_image),
                            // Toast.LENGTH_LONG).show();
                        }
                        finish();
                    }
                }
            } catch (IOException | NullPointerException e) {
                LogUtil.e(e, "Draw.onOptionsItemSelected failed");
                // todo error Toast.makeText(this, getString(R.string.err_share_image),
                // Toast.LENGTH_LONG).show();
            }
        }
        if (id == R.id.undo) {
            drawView.undo();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.draw_menu, menu);
        return true;
    }

    private void cropImageResult(final CropImageView.CropResult result) {
        if (result.isSuccessful()) {
            bitmap = result.getBitmap(this).copy(Bitmap.Config.RGB_565, true);
            BlendModeUtil.tintDrawableAsModulate(color.getBackground(), getLastColor());
            color.setOnClickListener(v -> showColorPicker());
            drawView.drawBitmap(bitmap);
            drawView.setPaintStrokeColor(getLastColor());
            drawView.setPaintStrokeWidth(20f);
            enabled = true;
        } else {
            finish();
        }
    }

    private void showColorPicker() {
        ColorPickerDialog.Builder builder =
                new ColorPickerDialog.Builder(Draw.this)
                        .setTitle(R.string.choose_color_title)
                        .setPositiveButton(
                                getString(R.string.btn_ok),
                                (ColorEnvelopeListener)
                                        (ColorEnvelope envelope, boolean fromUser) -> {
                                            int selectedColor = envelope.getColor();
                                            drawView.setPaintStrokeColor(selectedColor);
                                            BlendModeUtil.tintDrawableAsModulate(
                                                    color.getBackground(), selectedColor);
                                            Reddit.colors
                                                    .edit()
                                                    .putInt("drawColor", selectedColor)
                                                    .commit();
                                        })
                        .setNegativeButton(
                                getString(R.string.btn_cancel),
                                (dialogInterface, i) -> dialogInterface.dismiss())
                        .attachAlphaSlideBar(false)
                        .attachBrightnessSlideBar(true);
        builder.getColorPickerView().setInitialColor(getLastColor());
        builder.show();
    }
}
