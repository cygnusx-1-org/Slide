package me.edgan.redditslide.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import me.edgan.redditslide.Fragments.SubredditListView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MaterialInputDialog;
import me.edgan.redditslide.util.MiscUtil;

/** Created by ccrama on 9/17/2015. */
public class SubredditSearch extends BaseActivityAnim {
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getOnBackPressedDispatcher().onBackPressed();
                return true;
            case R.id.edit:
                {
                    new MaterialInputDialog.Builder(SubredditSearch.this)
                            .inputType(
                                    InputType.TYPE_CLASS_TEXT
                                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                            .inputRange(3, 100)
                            .input(getString(R.string.discover_search), term, null)
                            .positiveText(R.string.search_all)
                            .onPositive(
                                    dialog -> {
                                        Intent inte =
                                                new Intent(
                                                        SubredditSearch.this,
                                                        SubredditSearch.class);
                                        inte.putExtra(
                                                "term",
                                                dialog.getInputEditText().getText().toString());
                                        SubredditSearch.this.startActivity(inte);
                                        finish();
                                    })
                            .negativeText(R.string.btn_cancel)
                            .show();
                }
                return true;
            default:
                return false;
        }
    }

    String term;

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
            return;
        }
        term = extras.getString("term");
        applyColorTheme("");
        setContentView(R.layout.activity_fragmentinner);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, term, true, true);

        Fragment f = new SubredditListView();
        Bundle args = new Bundle();
        args.putString("id", term);
        f.setArguments(args);
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragmentcontent, f);
        fragmentTransaction.commit();
    }
}
