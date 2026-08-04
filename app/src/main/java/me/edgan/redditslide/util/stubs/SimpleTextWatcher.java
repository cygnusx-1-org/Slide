package me.edgan.redditslide.util.stubs;

import android.text.Editable;
import android.text.TextWatcher;
import org.jspecify.annotations.NullMarked;

/**
 * Base class for scenarios where user wants to implement only one method of {@link TextWatcher}.
 */
@NullMarked
public class SimpleTextWatcher implements TextWatcher {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {}
}
