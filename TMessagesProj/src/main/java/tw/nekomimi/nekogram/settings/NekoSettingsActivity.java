package tw.nekomimi.nekogram.settings;

import static android.view.View.OVER_SCROLL_NEVER;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import android.widget.EditText;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.DocumentSelectActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.helpers.CloudSettingsHelper;
import tw.nekomimi.nekogram.helpers.PasscodeHelper;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.helpers.SettingsHelper;
import tw.nekomimi.nekogram.helpers.SettingsSearchResult;
import tw.nekomimi.nekogram.utils.AlertUtil;

public class NekoSettingsActivity extends BaseNekoSettingsActivity {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_SYNC = 2;

    private int generalRow;
    private int appearanceRow;
    private int ayuMomentsRow;
    private int translatorRow;
    private int pluginsRow;
    private int chatRow;
    private int passcodeRow;
    private int experimentRow;
    private int categoriesEndRow;

    private int importSettingsRow;
    private int exportSettingsRow;
    private int resetSettingsRow;
    private int appRestartRow;
    private int nSettingsEndRow;


    private int exteraInfoRow;

    private ActionBarMenuItem searchItem;
    private BlurredRecyclerView searchListView;
    private ArrayList<SettingsSearchResult> allSearchResults;
    private final ArrayList<SettingsSearchResult> filteredSearchResults = new ArrayList<>();
    private String currentSearchQuery = "";
    private RecyclerListView.SelectionAdapter searchAdapter;
    private StickerEmptyView searchEmptyView;

    @Override
    protected void updateRows() {
        super.updateRows();

        generalRow = addRow();
        appearanceRow = addRow();
        ayuMomentsRow = addRow();
        translatorRow = addRow();
        pluginsRow = addRow();
        chatRow = addRow();
        if (!PasscodeHelper.isSettingsHidden()) {
            passcodeRow = addRow();
        } else {
            passcodeRow = -1;
        }
        experimentRow = addRow();
        categoriesEndRow = addRow();

        exportSettingsRow = addRow();
        importSettingsRow = addRow();
        resetSettingsRow = addRow();
        appRestartRow = addRow();
        nSettingsEndRow = addRow();

        exteraInfoRow = addRow();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search, resourcesProvider).setIsSearchField(true);
        searchItem.setSearchFieldHint(getString(R.string.Search));
        searchItem.setContentDescription(getString(R.string.Search));
        searchItem.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                if (allSearchResults == null) {
                    allSearchResults = SettingsHelper.onCreateSearchArray(fragment -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            presentFragment(fragment);
                        } catch (Exception ignore) {
                        }
                    }));
                }
                if (searchListView != null) {
                    searchListView.setVisibility(View.VISIBLE);
                }
                if (listView != null) {
                    listView.setVisibility(View.GONE);
                }
                updateSearch("");
            }

            @Override
            public void onSearchCollapse() {
                if (searchListView != null) {
                    searchListView.setVisibility(View.GONE);
                }
                if (searchEmptyView != null) {
                    searchEmptyView.setVisibility(View.GONE);
                }
                if (listView != null) {
                    listView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                updateSearch(editText.getText().toString());
            }
        });

        menu.addItem(MENU_SYNC, R.drawable.cloud_sync, resourcesProvider);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SYNC) {
                    CloudSettingsHelper.getInstance().showDialog(NekoSettingsActivity.this);
                }
            }
        });

        SizeNotifierFrameLayout frameLayout = (SizeNotifierFrameLayout) fragmentView;
        searchListView = new BlurredRecyclerView(context);
        searchListView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        searchListView.setVerticalScrollBarEnabled(false);
        searchListView.setClipToPadding(false);
        searchListView.setPadding(0, getM3HeaderTopPadding(), 0, dp(80));
        searchListView.setVisibility(View.GONE);

        searchEmptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_SEARCH);
        searchEmptyView.title.setText(getString(R.string.NoResult));
        searchEmptyView.subtitle.setVisibility(View.GONE);
        searchEmptyView.setVisibility(View.GONE);
        searchEmptyView.setPadding(0, getM3HeaderTopPadding(), 0, dp(80));
        frameLayout.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive()) {
            searchListView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            searchListView.setSections(
                v -> v instanceof SettingsSearchCell,
                t -> t == 0,
                dp(12),
                dp(16),
                searchListView::drawBackgroundRect,
                true
            );
        } else if (xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass()) {
            searchListView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            searchListView.setSections(
                v -> v instanceof SettingsSearchCell,
                t -> t == 0,
                dp(16),
                xyz.nextalone.nagram.ui.UIStyleEngine.getCardCornerRadius(),
                searchListView::drawBackgroundRect,
                true
            );
        }

        searchAdapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @NonNull
            @Override
            public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent1, int viewType) {
                View view1 = new SettingsSearchCell(context);
                view1.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view1);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                SettingsSearchCell cell = (SettingsSearchCell) holder.itemView;
                SettingsSearchResult r = filteredSearchResults.get(position);
                String[] path = r.path2 != null ? new String[]{r.path1, r.path2} : new String[]{r.path1};
                CharSequence titleToSet = r.searchTitle == null ? "" : r.searchTitle;
                String q = currentSearchQuery;
                if (q != null && !q.isEmpty() && titleToSet.length() > 0) {
                    SpannableStringBuilder ss = new SpannableStringBuilder(titleToSet);
                    String lower = titleToSet.toString().toLowerCase();
                    String[] parts = q.split("\\s+");
                    int highlightColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
                    for (String p : parts) {
                        if (p.isEmpty()) continue;
                        int idx = 0;
                        while (true) {
                            int found = lower.indexOf(p, idx);
                            if (found == -1) break;
                            try {
                                ss.setSpan(new ForegroundColorSpan(highlightColor), found, found + p.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            } catch (Exception ignore) {
                            }
                            idx = found + p.length();
                        }
                    }
                    titleToSet = ss;
                }
                cell.setTextAndValueAndIcon(titleToSet, path, r.iconResId, position < filteredSearchResults.size() - 1);
            }

            @Override
            public int getItemCount() {
                return filteredSearchResults.size();
            }
        };

        searchListView.setAdapter(searchAdapter);
        searchListView.setOnItemClickListener((v, position) -> {
            if (position < 0 || position >= filteredSearchResults.size()) return;
            SettingsSearchResult r = filteredSearchResults.get(position);
            if (actionBar != null) {
                actionBar.closeSearchField();
            }
            try {
                if (r.openRunnable != null) {
                    r.openRunnable.run();
                }
            } catch (Exception ignore) {
            }
        });

        frameLayout.addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return view;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateSearch(String query) {
        if (allSearchResults == null) {
            allSearchResults = SettingsHelper.onCreateSearchArray(fragment -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    presentFragment(fragment);
                } catch (Exception ignore) {
                }
            }));
        }
        String q = query == null ? "" : query.toLowerCase().trim();
        currentSearchQuery = q;
        filteredSearchResults.clear();
        if (q.isEmpty()) {
            filteredSearchResults.addAll(allSearchResults);
        } else {
            String[] parts = q.split("\\s+");
            for (SettingsSearchResult item : allSearchResults) {
                String title = item.searchTitle == null ? "" : item.searchTitle.toLowerCase();
                boolean ok = true;
                for (String p : parts) {
                    if (!title.contains(p)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    filteredSearchResults.add(item);
                }
            }
        }
        if (searchAdapter != null) {
            searchAdapter.notifyDataSetChanged();
        }
        if (searchEmptyView != null) {
            boolean isSearching = searchItem != null && searchItem.isSearchFieldVisible();
            searchEmptyView.setVisibility(isSearching && filteredSearchResults.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.NekoSettings);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == chatRow) {
            presentFragment(new NekoChatSettingsActivity());
        } else if (position == generalRow) {
            presentFragment(new NekoGeneralSettingsActivity());
        } else if (position == appearanceRow) {
            presentFragment(new NekoAppearanceSettingsActivity());
        } else if (position == ayuMomentsRow) {
            presentFragment(new NekoAyuMomentsSettingsActivity());
        } else if (position == passcodeRow) {
            presentFragment(new NekoPasscodeSettingsActivity());
        } else if (position == experimentRow) {
            presentFragment(new NekoExperimentalSettingsActivity());
        } else if (position == translatorRow) {
            presentFragment(new NekoTranslatorSettingsActivity());
        } else if (position == pluginsRow) {
            presentFragment(new com.exteragram.messenger.plugins.ui.PluginsActivity());
        } else if (position == exteraInfoRow) {
            presentFragment(new NagramExteraAboutActivity());
        } else if (position == importSettingsRow) {
            if (Build.VERSION.SDK_INT >= 33) {
                openFilePicker();
            } else {
                DocumentSelectActivity activity = getDocumentSelectActivity(getParentActivity());
                if (activity != null) {
                    presentFragment(activity);
                }
            }
        } else if (position == resetSettingsRow) {
            AlertUtil.showConfirm(getParentActivity(),
                    getString(R.string.ResetSettingsAlert),
                    R.drawable.msg_reset,
                    getString(R.string.Reset),
                    true,
                    () -> {
                        ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Activity.MODE_PRIVATE).edit().clear().commit();
                        ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Activity.MODE_PRIVATE).edit().clear().commit();
                        ApplicationLoader.applicationContext.getSharedPreferences("aichatconfig", Activity.MODE_PRIVATE).edit().clear().commit();
                        ApplicationLoader.applicationContext.getSharedPreferences("pillstackconfig", Activity.MODE_PRIVATE).edit().clear().commit();
                        NekoConfig.getPreferences().edit().clear().commit();
                        AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
                    });
        } else if (position == exportSettingsRow) {
            SettingsBackupHelper.backupSettings(getParentActivity(), resourceProvider);
        } else if (position == appRestartRow) {
            AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int viewType = holder.getItemViewType();
            switch (viewType) {
                case TYPE_SHADOW: {
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case TYPE_TEXT: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == chatRow) {
                        textCell.setTextAndIcon(getString(R.string.Chat), R.drawable.msg_discussion, true);
                    } else if (position == generalRow) {
                        textCell.setTextAndIcon(getString(R.string.General), R.drawable.msg_media, true);
                    } else if (position == appearanceRow) {
                        textCell.setTextAndIcon(getString(R.string.Appearance), R.drawable.msg_theme, true);
                    } else if (position == ayuMomentsRow) {
                        textCell.setTextAndIcon(getString(R.string.AyuMoments), R.drawable.ayu_ghost, true);
                    } else if (position == translatorRow) {
                        textCell.setTextAndIcon(getString(R.string.TranslatorSettings), R.drawable.ic_translate, true);
                    } else if (position == pluginsRow) {
                        textCell.setTextAndIcon("Plugin", R.drawable.plugins_filled_remix, true);
                    } else if (position == passcodeRow) {
                        textCell.setTextAndIcon(getString(R.string.PasscodeNeko), R.drawable.msg_permissions, true);
                    } else if (position == experimentRow) {
                        textCell.setTextAndIcon(getString(R.string.Experimental), R.drawable.msg_fave, true);
                    } else if (position == importSettingsRow) {
                        textCell.setTextAndIcon(getString(R.string.ImportSettings), R.drawable.import_solar, true);
                    } else if (position == exportSettingsRow) {
                        textCell.setTextAndIcon(getString(R.string.BackupSettings), R.drawable.export_solar, true);
                    } else if (position == resetSettingsRow) {
                        textCell.setTextAndIcon(getString(R.string.ResetSettings), R.drawable.msg_reset_solar, true);
                    } else if (position == appRestartRow) {
                        textCell.setTextAndIcon(getString(R.string.RestartApp), R.drawable.msg_retry_solar, true);
                    } else if (position == exteraInfoRow) {
                        textCell.setTextAndIcon(getString(R.string.NagramExteraInfo), R.drawable.msg_info, false);
                    }
                    break;
                }

            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == categoriesEndRow || position == nSettingsEndRow) {
                return TYPE_SHADOW;
            } else if (position == chatRow || position == generalRow || position == appearanceRow || position == ayuMomentsRow || position == passcodeRow || position == experimentRow || position == translatorRow || position == pluginsRow ||
                    position == importSettingsRow || position == exportSettingsRow || position == resetSettingsRow || position == appRestartRow ||
                    position == exteraInfoRow) {
                return TYPE_TEXT;
            }
            return TYPE_SHADOW;
        }
    }

    private DocumentSelectActivity getDocumentSelectActivity(Activity parent) {
        try {
            if (parent.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parent.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                return null;
            }
        } catch (Throwable ignore) {
        }
        DocumentSelectActivity fragment = new DocumentSelectActivity(false);
        fragment.setMaxSelectedFiles(1);
        fragment.setAllowPhoto(false);
        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                activity.finishFragment();
                SettingsBackupHelper.importSettings(parent, new File(files.get(0)));
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
            }

            @Override
            public void startDocumentSelectActivity() {
            }
        });
        return fragment;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(intent, 21);
        } catch (android.content.ActivityNotFoundException ex) {
            AlertUtil.showSimpleAlert(getParentActivity(), ex);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == 21 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                File cacheDir = AndroidUtilities.getCacheDir();
                String tempFile = UUID.randomUUID().toString().replace("-", "") + ".nekox-settings.json";
                File file = new File(cacheDir.getPath(), tempFile);
                try (InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                    if (inputStream == null) {
                        throw new IllegalStateException("Unable to open the selected settings backup");
                    }
                    try (OutputStream outputStream = new FileOutputStream(file)) {
                        final byte[] buffer = new byte[4 * 1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        outputStream.flush();
                    }
                    SettingsBackupHelper.importSettings(getParentActivity(), file);
                } catch (Exception e) {
                    AlertUtil.showSimpleAlert(getParentActivity(), e);
                } finally {
                    if (file.exists()) {
                        file.delete();
                    }
                }
            }
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }
}
