package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.LaunchActivity;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellConnectedButtonGroup;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.ui.cells.AvatarCornersPreviewCell;
import tw.nekomimi.nekogram.ui.cells.ChatListPreviewCell;
import tw.nekomimi.nekogram.ui.cells.FabShapePreviewCell;
import tw.nekomimi.nekogram.ui.cells.FilterTabsPreviewCell;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class NekoAppearanceSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;
    private AvatarCornersPreviewCell avatarCornersPreviewCell;
    private FabShapePreviewCell fabShapePreviewCell;
    private ChatListPreviewCell chatListPreviewCell;
    private FilterTabsPreviewCell filterTabsPreviewCell;
    private Parcelable recyclerViewState = null;

    private boolean wasCentered = false;
    private boolean wasCenteredAtBeginning = false;
    private float centeredMeasure = -1;

    private final CellGroup cellGroup = new CellGroup(this);

    private final AbstractConfigCell headerAppearance = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Appearance)));
    private final AbstractConfigCell uiStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("UiStyle", NaConfig.INSTANCE.getUiStyle(), new String[]{
            getString(R.string.UiStyleClassic),
            getString(R.string.UiStyleMaterial3Expressive),
            getString(R.string.UiStyleIosLiquidGlass)
    }, null));
    private final AbstractConfigCell uiStyleConnectedGroupRow = cellGroup.appendCell(new ConfigCellConnectedButtonGroup("UiStyle", NaConfig.INSTANCE.getUiStyle(), new String[]{
            getString(R.string.UiStyleClassic),
            getString(R.string.UiStyleMaterial3Expressive),
            getString(R.string.UiStyleIosLiquidGlass)
    }));
    private final AbstractConfigCell typefaceRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.typeface));
    private final AbstractConfigCell hideDividersRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideDividers()));
    private final AbstractConfigCell sectionsSeparatedHeadersRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSectionsSeparatedHeaders(), null, getString(R.string.SeparateHeaders)));
    private final AbstractConfigCell fabShapePreviewRow = cellGroup.appendCell(new ConfigCellCustom("FabShapePreview", ConfigCellCustom.CUSTOM_ITEM_FabShapePreview, false));
    private final AbstractConfigCell alwaysShowDownloadIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getAlwaysShowDownloadIcon()));
    private final AbstractConfigCell showStickersInTopLevelRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowStickersRowToplevel()));
    private final AbstractConfigCell hidePremiumSectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHidePremiumSection()));
    private final AbstractConfigCell hideHelpSectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideHelpSection()));
    private final AbstractConfigCell disableAvatarBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableAvatarBlur()));
    private final AbstractConfigCell iconReplacementsRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "IconReplacements", getString(R.string.IconReplacements), R.drawable.msg_theme, false, () ->
            presentFragment(new com.exteragram.messenger.icons.ui.IconPacksActivity())
    ));
    private final AbstractConfigCell switchStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("SwitchStyle", NaConfig.INSTANCE.getSwitchStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleMaterialDesign3),
            getString(R.string.StyleOneUI),
            getString(R.string.StyleIosLiquidGlass)
    }, null));
    private final AbstractConfigCell switchStyleConnectedGroupRow = cellGroup.appendCell(new ConfigCellConnectedButtonGroup("SwitchStyle", NaConfig.INSTANCE.getSwitchStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleMaterialDesign3),
            getString(R.string.StyleOneUI),
            getString(R.string.StyleIos)
    }));
    private final AbstractConfigCell sliderStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("SliderStyle", NaConfig.INSTANCE.getSliderStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleModern),
            getString(R.string.StyleMaterialDesign3),
            getString(R.string.StyleIosLiquidGlass)
    }, null));
    private final AbstractConfigCell sliderStyleConnectedGroupRow = cellGroup.appendCell(new ConfigCellConnectedButtonGroup("SliderStyle", NaConfig.INSTANCE.getSliderStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleModern),
            getString(R.string.StyleMaterialDesign3),
            getString(R.string.StyleIos)
    }));
    private final AbstractConfigCell notificationIconRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getNotificationIcon(), new String[]{
            getString(R.string.MapPreviewProviderTelegram),
            getString(R.string.NagramX),
            getString(R.string.Nagram),
            getString(R.string.NekoX)
    }, null));
    private final AbstractConfigCell tabletModeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.tabletMode, new String[]{
            getString(R.string.TabletModeDefault),
            getString(R.string.Enable),
            getString(R.string.Disable)
    }, null));
    private final AbstractConfigCell tabletModeConnectedGroupRow = cellGroup.appendCell(new ConfigCellConnectedButtonGroup(null, NekoConfig.tabletMode, new String[]{
            getString(R.string.TabletModeDefault),
            getString(R.string.Enable),
            getString(R.string.Disable)
    }));
    private final AbstractConfigCell dividerAppearance = cellGroup.appendCell(new ConfigCellDivider());
    private final AbstractConfigCell avatarCornersPreviewRow = cellGroup.appendCell(new ConfigCellCustom("AvatarCorners", ConfigCellCustom.CUSTOM_ITEM_AvatarCorners, false));
    private final AbstractConfigCell singleCornerRadiusRow = cellGroup.appendCell(
            new ConfigCellTextCheck(
                    NaConfig.INSTANCE.getSingleCornerRadius(),
                    null,
                    getString(R.string.SingleCornerRadius)
            )
    );
    private final AbstractConfigCell avatarCornersInfoRow = cellGroup.appendCell(new ConfigCellCustom("SingleCornerRadiusInfo", CellGroup.ITEM_TYPE_TEXT, false));
    private final AbstractConfigCell headerDialogs = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.DialogsSettings)));
    private final AbstractConfigCell chatListPreviewRow = cellGroup.appendCell(new ConfigCellCustom("ChatListPreview", ConfigCellCustom.CUSTOM_ITEM_ChatListPreview, false));
    private final AbstractConfigCell openProfileByAvatarRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.openProfileByAvatar));
    private final AbstractConfigCell forceSnowfallRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getForceSnowfall(), getString(R.string.ForceSnowfallInfo), getString(R.string.ForceSnowfall)));
    private final AbstractConfigCell centerActionBarTitleRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getCenterActionBarTitle(), null, getString(R.string.CenterActionBarTitleType)));
    private final AbstractConfigCell folderNameAsTitleRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getFolderNameAsTitle()));
    private final AbstractConfigCell customTitleUserNameRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getCustomTitleUserName()));
    private final AbstractConfigCell customTitleRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomTitle(),
            getString(R.string.CustomTitleHint), null,
            (input) -> input.isEmpty() ? (String) NaConfig.INSTANCE.getCustomTitle().defaultValue : input));
    private final AbstractConfigCell sortByUnreadRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSortByUnread()));
    private final AbstractConfigCell disableDialogsFloatingButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableDialogsFloatingButton()));
    private final AbstractConfigCell hideHomeSearchFieldRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideHomeSearchField()));
    private final AbstractConfigCell disableBotOpenButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableBotOpenButton()));
    private final AbstractConfigCell mediaPreviewRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.mediaPreview));
    private final AbstractConfigCell userAvatarsInMessagePreviewRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getUserAvatarsInMessagePreview()));
    private final AbstractConfigCell dividerDialogs = cellGroup.appendCell(new ConfigCellDivider());
    private final AbstractConfigCell headerFolder = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Folder)));
    private final AbstractConfigCell filterTabsPreviewRow = cellGroup.appendCell(new ConfigCellCustom("FilterTabsPreview", ConfigCellCustom.CUSTOM_ITEM_FilterTabsPreview, false));
    private final AbstractConfigCell hideAllTabRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideAllTab, getString(R.string.HideAllTabAbout)));
    private final ConfigCellTextCheck foldersAtBottomRow = (ConfigCellTextCheck) cellGroup.appendCell(
            new ConfigCellTextCheck(NaConfig.INSTANCE.getFoldersAtBottom())
    );
    private final AbstractConfigCell doNotUnarchiveBySwipeRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDoNotUnarchiveBySwipe()));
    private final AbstractConfigCell openArchiveOnPullRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.openArchiveOnPull));
    private final AbstractConfigCell hideArchiveRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideArchive()));
    private final AbstractConfigCell tabsTitleTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.tabsTitleType, new String[]{
            getString(R.string.TabTitleTypeText),
            getString(R.string.TabTitleTypeIcon),
            getString(R.string.TabTitleTypeMix)
    }, null));
    private final AbstractConfigCell tabsTitleTypeConnectedGroupRow = cellGroup.appendCell(new ConfigCellConnectedButtonGroup(null, NekoConfig.tabsTitleType, new String[]{
            getString(R.string.TabTitleTypeText),
            getString(R.string.TabTitleTypeIcon),
            getString(R.string.TabTitleTypeMix)
    }));
    private final AbstractConfigCell ignoreUnreadCountRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getIgnoreUnreadCount()));
    private final AbstractConfigCell dividerNavigationTop = cellGroup.appendCell(new ConfigCellDivider());
    private final AbstractConfigCell headerNavigation = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AppNavigation)));
    private final AbstractConfigCell navigationDrawerRow = cellGroup.appendCell(
            new ConfigCellTextCheck(NekoConfig.navigationDrawerEnabled, null, getString(R.string.HomeDrawer))
    );
    private final AbstractConfigCell drawerElementsRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "DrawerElements", getString(R.string.DrawerElements), R.drawable.menu_newfilter, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.DrawerElements, new java.util.ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemMyProfile(), getString(R.string.MyProfile), R.drawable.left_status_profile));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSetEmojiStatus(), getString(R.string.SetEmojiStatus), R.drawable.msg_status_set_solar, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemArchivedChats(), getString(R.string.ArchivedChats), R.drawable.msg_archive, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNewGroup(), getString(R.string.NewGroup), R.drawable.msg_groups));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNewChannel(), getString(R.string.NewChannel), R.drawable.msg_channel));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemContacts(), getString(R.string.Contacts), R.drawable.msg_contacts));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemCalls(), getString(R.string.Calls), R.drawable.msg_calls));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemRecentChats(), getString(R.string.RecentChats), R.drawable.msg_recent_solar));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSaved(), getString(R.string.SavedMessages), R.drawable.msg_saved));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSettings(), getString(R.string.Settings), R.drawable.msg_settings_old, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNSettings(), getString(R.string.NekoSettings), R.drawable.nagramx_outline));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemBrowser(), getString(R.string.InappBrowser), R.drawable.web_browser));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemQrLogin(), getString(R.string.ImportLogin), R.drawable.msg_qrcode));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSessions(), getString(R.string.Devices), R.drawable.msg2_devices, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemRestartApp(), getString(R.string.RestartApp), R.drawable.msg_retry));
            }}))
    ));
    private final AbstractConfigCell mainTabsCustomizeRow = cellGroup.appendCell(
            new ConfigCellTextCheckIcon(null, "MainTabsCustomize", getString(R.string.MainTabsCustomize), R.drawable.tabs_reorder, false, () ->
                    presentFragment(new MainTabsCustomizeActivity()))
    );
    private final AbstractConfigCell dividerFolder = cellGroup.appendCell(new ConfigCellDivider());
    private final AbstractConfigCell headerBlurOptions = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.BlurOptions)));

    private final AbstractConfigCell blurBehindDrawerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.blurBehindDrawer, null, getString(R.string.BlurBehindDrawer)));
    private final AbstractConfigCell blurRadiusDrawerRow = cellGroup.appendCell(new ConfigCellCustom("blurRadiusDrawer", ConfigCellCustom.CUSTOM_ITEM_BlurRadiusDrawer, true));

    private final AbstractConfigCell forceChatBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.forceChatBlur, null, getString(R.string.ForceChatBlur)));
    private final AbstractConfigCell blurRadiusGlobalRow = cellGroup.appendCell(new ConfigCellCustom("blurRadiusGlobal", ConfigCellCustom.CUSTOM_ITEM_BlurRadiusGlobal, true));

    private final AbstractConfigCell forceMainTabsBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.forceMainTabsBlur, null, getString(R.string.ForceMainTabsBlur)));
    private final AbstractConfigCell mainTabsGlassAlphaRow = cellGroup.appendCell(new ConfigCellCustom("mainTabsGlassAlpha", ConfigCellCustom.CUSTOM_ITEM_MainTabsGlassAlpha, true));
    private final AbstractConfigCell mainTabsBlurRadiusRow = cellGroup.appendCell(new ConfigCellCustom("mainTabsBlurRadius", ConfigCellCustom.CUSTOM_ITEM_MainTabsBlurRadius, true));

    private final AbstractConfigCell forceActionBarBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.forceActionBarBlur, null, getString(R.string.ForceActionBarBlur)));
    private final AbstractConfigCell actionBarGlassAlphaRow = cellGroup.appendCell(new ConfigCellCustom("actionBarGlassAlpha", ConfigCellCustom.CUSTOM_ITEM_ActionBarGlassAlpha, true));
    private final AbstractConfigCell actionBarBlurRadiusRow = cellGroup.appendCell(new ConfigCellCustom("actionBarBlurRadius", ConfigCellCustom.CUSTOM_ITEM_ActionBarBlurRadius, true));

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "appearance";
    }

    @Override
    protected void styleTextInfoPrivacyCell(TextInfoPrivacyCell cell) {
        cell.setBackground(Theme.getThemedDrawable(cell.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
    }

    public NekoAppearanceSettingsActivity() {
        cellGroup.rows.remove(headerAppearance);
        cellGroup.rows.remove(uiStyleRow);
        cellGroup.rows.remove(uiStyleConnectedGroupRow);
        cellGroup.rows.remove(switchStyleConnectedGroupRow);
        cellGroup.rows.remove(sliderStyleConnectedGroupRow);
        cellGroup.rows.remove(tabletModeConnectedGroupRow);
        cellGroup.rows.remove(tabsTitleTypeConnectedGroupRow);
        cellGroup.rows.remove(fabShapePreviewRow);
        cellGroup.rows.remove(typefaceRow);
        cellGroup.rows.remove(avatarCornersPreviewRow);
        cellGroup.rows.remove(singleCornerRadiusRow);
        cellGroup.rows.remove(avatarCornersInfoRow);
        cellGroup.rows.add(0, avatarCornersPreviewRow);
        cellGroup.rows.add(1, singleCornerRadiusRow);
        cellGroup.rows.add(2, avatarCornersInfoRow);
        cellGroup.rows.add(3, headerAppearance);
        cellGroup.rows.add(4, getUiStyleModeRow());
        cellGroup.rows.add(5, fabShapePreviewRow);
        cellGroup.rows.add(6, typefaceRow);
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive()) {
            replaceRow(switchStyleRow, switchStyleConnectedGroupRow);
            replaceRow(sliderStyleRow, sliderStyleConnectedGroupRow);
            replaceRow(tabletModeRow, tabletModeConnectedGroupRow);
        }
        // Hoist the entire "Chat List" (Dialogs) section in front of the Appearance subheader.
        List<AbstractConfigCell> dialogsBlock = Arrays.asList(
                headerDialogs,
                chatListPreviewRow,
                openProfileByAvatarRow,
                forceSnowfallRow,
                centerActionBarTitleRow,
                folderNameAsTitleRow,
                customTitleUserNameRow,
                customTitleRow,
                sortByUnreadRow,
                disableDialogsFloatingButtonRow,
                hideHomeSearchFieldRow,
                disableBotOpenButtonRow,
                mediaPreviewRow,
                userAvatarsInMessagePreviewRow,
                dividerDialogs
        );
        cellGroup.rows.removeAll(dialogsBlock);
        int appearanceIdx = cellGroup.rows.indexOf(headerAppearance);
        cellGroup.rows.addAll(appearanceIdx, dialogsBlock);
        // Surface the two folder-tab tweaks that immediately affect the preview
        // (Show on Tabs / Ignore Unread Count) right under the preview, before
        // the destructive "Hide All Chats" row.
        cellGroup.rows.remove(ignoreUnreadCountRow);
        cellGroup.rows.remove(tabsTitleTypeRow);
        cellGroup.rows.remove(tabsTitleTypeConnectedGroupRow);
        int hideAllTabIdx = cellGroup.rows.indexOf(hideAllTabRow);
        if (hideAllTabIdx >= 0) {
            cellGroup.rows.add(hideAllTabIdx, ignoreUnreadCountRow);
            cellGroup.rows.add(hideAllTabIdx, getTabsTitleTypeModeRow());
        }
        wasCentered = isCentered();
        wasCenteredAtBeginning = wasCentered;
        checkOpenArchiveOnPullRows();
        checkCustomTitleRows();

        addRowsToMap(cellGroup);
    }

    private AbstractConfigCell getUiStyleModeRow() {
        return xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive() ? uiStyleConnectedGroupRow : uiStyleRow;
    }

    private AbstractConfigCell getTabsTitleTypeModeRow() {
        return xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive() ? tabsTitleTypeConnectedGroupRow : tabsTitleTypeRow;
    }

    private void replaceRow(AbstractConfigCell oldRow, AbstractConfigCell newRow) {
        int index = cellGroup.rows.indexOf(oldRow);
        if (index >= 0) {
            cellGroup.rows.remove(index);
            cellGroup.rows.add(index, newRow);
        }
    }

    private void updateSliderEnabledState(AbstractConfigCell row) {
        if (listView != null && cellGroup != null) {
            int index = cellGroup.rows.indexOf(row);
            if (index >= 0) {
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(index);
                if (holder != null && holder.itemView instanceof BlurSliderCell blurSliderCell) {
                    blurSliderCell.updateEnabledState(true);
                } else if (listAdapter != null) {
                    listAdapter.notifyItemChanged(index);
                }
            }
        }
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        setupDefaultListeners();

        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NaConfig.INSTANCE.getUiStyle().getKey())) {
                if (newValue instanceof Integer
                        && (Integer) newValue == xyz.nextalone.nagram.NaConfig.UI_STYLE_MATERIAL3_EXPRESSIVE
                        && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    boolean isNight = Theme.isCurrentThemeDark();
                    Theme.ThemeInfo targetTheme = Theme.getTheme(isNight ? "Monet Dark" : "Monet Light");
                    if (targetTheme != null) {
                        Theme.applyTheme(targetTheme, isNight);
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    tw.nekomimi.nekogram.helpers.AppRestartHelper.triggerRebirth(
                            getParentActivity() != null ? getParentActivity() : org.telegram.messenger.ApplicationLoader.applicationContext,
                            new android.content.Intent(org.telegram.messenger.ApplicationLoader.applicationContext, org.telegram.ui.LaunchActivity.class)
                    );
                }, 200);
                return;
            }

            // Folder-tab tweaks that should never trigger a restart tooltip and
            // never require a restart to take effect:
            //   - hideAllTab, ignoreUnreadCount, tabsTitleType
            // are all handled below by broadcasting dialogFiltersUpdated, which
            // makes DialogsActivity rebuild its filterTabsView (updateFilterTabs)
            // and reaches the preview cell via its own NotificationCenter listener.
            //
            // foldersAtBottom only affects layout placement (top vs. bottom) inside
            // DialogsActivity and there's no live hook for that, so it stays in the
            // restart-tooltip branch below; we still refresh the preview here so the
            // user at least sees the visual marker change immediately.
            if (filterTabsPreviewCell != null
                    && key.equals(NaConfig.INSTANCE.getFoldersAtBottom().getKey())) {
                filterTabsPreviewCell.refresh();
            }
            if (key.equals(NaConfig.INSTANCE.getForceSnowfall().getKey())) {
                if (chatListPreviewCell != null) {
                    chatListPreviewCell.invalidate();
                }
                if (getActionBar() != null) {
                    getActionBar().invalidate();
                }
                if (listView != null) {
                    listView.invalidate();
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.invalidateMotionBackground);
            } else if (key.equals(NekoConfig.hideAllTab.getKey())
                    || key.equals(NaConfig.INSTANCE.getIgnoreUnreadCount().getKey())
                    || key.equals(NekoConfig.tabsTitleType.getKey())) {
                // Apply live: post dialogFiltersUpdated so DialogsActivity rebuilds
                // its filterTabsView (calls updateFilterTabs(true, true)) which picks
                // up the new hideAllTab / ignoreUnreadCount / tabsTitleType values
                // without a restart. The preview cell observes the same broadcast
                // and rebuilds itself, so no explicit refresh() is needed.
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            } else if (key.equals(NekoConfig.blurBehindDrawer.getKey())) {
                updateSliderEnabledState(blurRadiusDrawerRow);
            } else if (key.equals(NekoConfig.forceChatBlur.getKey())) {
                updateSliderEnabledState(blurRadiusGlobalRow);
            } else if (key.equals(NekoConfig.forceMainTabsBlur.getKey())) {
                updateSliderEnabledState(mainTabsGlassAlphaRow);
                updateSliderEnabledState(mainTabsBlurRadiusRow);
            } else if (key.equals(NekoConfig.forceActionBarBlur.getKey())) {
                updateSliderEnabledState(actionBarGlassAlphaRow);
                updateSliderEnabledState(actionBarBlurRadiusRow);
            } else if (key.equals(NaConfig.INSTANCE.getNotificationIcon().getKey())
                    || key.equals(NekoConfig.tabletMode.getKey())
                    || key.equals(NaConfig.INSTANCE.getHideDividers().getKey())
                    || key.equals(NekoConfig.typeface.getKey())
                    || key.equals(NaConfig.INSTANCE.getHidePremiumSection().getKey())
                    || key.equals(NaConfig.INSTANCE.getHideHelpSection().getKey())
                    || key.equals(NaConfig.INSTANCE.getAlwaysShowDownloadIcon().getKey())
                    || key.equals(NaConfig.INSTANCE.getShowStickersRowToplevel().getKey())
                    || key.equals(NaConfig.INSTANCE.getFoldersAtBottom().getKey())
                    || key.equals(NaConfig.INSTANCE.getDisableDialogsFloatingButton().getKey())
                    || key.equals(NaConfig.INSTANCE.getDisableBotOpenButton().getKey())
                    || key.equals(NekoConfig.navigationDrawerEnabled.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSectionsSeparatedHeaders().getKey())) {
                // Force RecyclerView to re-evaluate ListSectionsDecoration.getItemOffsets for
                // every child. The section lambda picks up the new flag immediately, but child
                // views keep the old left/right insets (HeaderCell was 0..listWidth when
                // separated=true, becomes 12dp..listWidth-12dp when separated=false). Without
                // this call, drawSectionBackground reads from.getLeft()/getRight() from the
                // stale layout and paints the MD3 container edge-to-edge until the next layout
                // pass. invalidateItemDecorations marks insets dirty and requestLayouts so the
                // very next traversal lays children out correctly before drawing.
                if (listView != null) {
                    // Re-apply HeaderCell padding/margins on the visible HeaderCell instances so
                    // the 3dp bottom-padding gap (under the title, above the MD3 container) shows
                    // up immediately instead of waiting until cells are recycled.
                    for (int i = 0, n = listView.getChildCount(); i < n; i++) {
                        View child = listView.getChildAt(i);
                        if (child instanceof HeaderCell) {
                            ((HeaderCell) child).applySeparatedHeadersStyle();
                        }
                    }
                    // Drop pooled HeaderCell instances so off-screen cached cells are not reused
                    // with the stale, constructor-baked padding when scrolled back in.
                    listView.getRecycledViewPool().clear();
                    listView.invalidateItemDecorations();
                }
                reloadUI(0);

            } else if (key.equals(NaConfig.INSTANCE.getSwitchStyle().getKey()) || key.equals(NaConfig.INSTANCE.getSliderStyle().getKey())) {
                if (listView.getLayoutManager() != null) {
                    recyclerViewState = listView.getLayoutManager().onSaveInstanceState();
                    parentLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    listView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
                }
            } else if (key.equals(NaConfig.INSTANCE.getCenterActionBarTitle().getKey())) {
                animateActionBarUpdate(this);
                if (chatListPreviewCell != null) {
                    chatListPreviewCell.refresh();
                }
            } else if (key.equals(NaConfig.INSTANCE.getSingleCornerRadius().getKey())) {
                reloadAvatarCorners();
            } else if (key.equals(NaConfig.INSTANCE.getSortByUnread().getKey())) {
                getMessagesController().sortDialogs(null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
            } else if (key.equals(NaConfig.INSTANCE.getHideArchive().getKey())) {
                checkOpenArchiveOnPullRows();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getUserAvatarsInMessagePreview().getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
            } else if (key.equals(NaConfig.INSTANCE.getHideHomeSearchField().getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.updateSearchSettings);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
            } else if (key.equals(NaConfig.INSTANCE.getCustomTitleUserName().getKey())) {
                checkCustomTitleRows();
                if (chatListPreviewCell != null) {
                    chatListPreviewCell.refresh();
                }
            } else if (key.equals(NaConfig.INSTANCE.getCustomTitle().getKey())
                    || key.equals(NaConfig.INSTANCE.getFolderNameAsTitle().getKey())) {
                if (chatListPreviewCell != null) {
                    chatListPreviewCell.refresh();
                }
            }
        };

        return superView;
    }

    @Override
    public int getBaseGuid() {
        return 14000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_theme;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Appearance);
    }

    private void reloadAvatarCorners() {
        if (avatarCornersPreviewCell != null) {
            avatarCornersPreviewCell.invalidate();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }

    private void checkOpenArchiveOnPullRows() {
        boolean hideArchive = NaConfig.INSTANCE.getHideArchive().Bool();
        if (listAdapter == null) {
            if (hideArchive) {
                cellGroup.rows.remove(openArchiveOnPullRow);
            }
            return;
        }
        if (!hideArchive) {
            final int index = cellGroup.rows.indexOf(hideArchiveRow);
            if (!cellGroup.rows.contains(openArchiveOnPullRow)) {
                cellGroup.rows.add(index, openArchiveOnPullRow);
                listAdapter.notifyItemInserted(index);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(openArchiveOnPullRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(openArchiveOnPullRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }

    private void checkCustomTitleRows() {
        boolean useUserName = NaConfig.INSTANCE.getCustomTitleUserName().Bool();
        if (listAdapter == null) {
            if (useUserName) {
                cellGroup.rows.remove(customTitleRow);
            }
            return;
        }
        if (!useUserName) {
            final int index = cellGroup.rows.indexOf(customTitleUserNameRow);
            if (!cellGroup.rows.contains(customTitleRow)) {
                cellGroup.rows.add(index + 1, customTitleRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(customTitleRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(customTitleRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }

    private boolean isCentered() {
        return NaConfig.INSTANCE.getCenterActionBarTitle().Bool();
    }

    private void animateActionBarUpdate(BaseNekoXSettingsActivity fragment) {
        boolean centered = isCentered();
        ActionBar actionBar = fragment.getActionBar();
        if (wasCentered == centered) {
            return;
        }
        if (actionBar != null) {
            SimpleTextView titleTextView = actionBar.getTitleTextView();
            if (centeredMeasure == -1) {
                centeredMeasure = actionBar.getMeasuredWidth() / 2f - titleTextView.getTextWidth() / 2f - dp((AndroidUtilities.isTablet() ? 80 : 72));
            }
            titleTextView.animate()
                    .translationX(centeredMeasure * (centered ? 1 : 0) - (wasCenteredAtBeginning ? Math.abs(centeredMeasure) : 0))
                    .setDuration(150)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            wasCentered = centered;
                            reloadUI(0);
                            LaunchActivity.makeRipple(centered ? (actionBar.getMeasuredWidth() / 2f) : 0, 0, centered ? 1.3f : 0.1f);
                        }
                    })
                    .start();
        } else {
            reloadUI(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
        }
    }

    private void reloadUI(int flags) {
        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
        if (layoutManager != null) {
            recyclerViewState = layoutManager.onSaveInstanceState();
            parentLayout.rebuildFragments(flags);
            layoutManager.onRestoreInstanceState(recyclerViewState);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected void onBindCustomViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AbstractConfigCell row = cellGroup.rows.get(position);
            if (row == avatarCornersInfoRow) {
                TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                textInfoPrivacyCell.setText(getString(R.string.SingleCornerRadiusInfo));
            } else if (holder.itemView instanceof BlurSliderCell blurSliderCell) {
                blurSliderCell.updateEnabledState();
                blurSliderCell.setNeedDivider(cellGroup.needSetDivider(row));
            }
        }

        @Override
        protected View onCreateCustomViewHolder(@NonNull ViewGroup parent, int viewType) {
            return switch (viewType) {
                case ConfigCellCustom.CUSTOM_ITEM_AvatarCorners -> avatarCornersPreviewCell = new AvatarCornersPreviewCell(
                        mContext,
                        NekoAppearanceSettingsActivity.this::reloadAvatarCorners
                );
                case ConfigCellCustom.CUSTOM_ITEM_FabShapePreview -> fabShapePreviewCell = new FabShapePreviewCell(
                        mContext,
                        null
                );
                case ConfigCellCustom.CUSTOM_ITEM_ChatListPreview -> chatListPreviewCell = new ChatListPreviewCell(mContext);
                case ConfigCellCustom.CUSTOM_ITEM_FilterTabsPreview -> filterTabsPreviewCell = new FilterTabsPreviewCell(mContext);
                case ConfigCellCustom.CUSTOM_ITEM_BlurRadiusDrawer -> new BlurSliderCell(mContext, getString(R.string.BlurRadiusDrawer), "dp", 4, 32, NekoConfig.blurRadiusDrawer, NekoConfig.blurBehindDrawer, null);
                case ConfigCellCustom.CUSTOM_ITEM_BlurRadiusGlobal -> new BlurSliderCell(mContext, getString(R.string.BlurRadiusGlobal), "dp", 4, 32, NekoConfig.blurRadiusGlobal, NekoConfig.forceChatBlur, null);
                case ConfigCellCustom.CUSTOM_ITEM_MainTabsGlassAlpha -> new BlurSliderCell(mContext, getString(R.string.MainTabsGlassAlpha), "%", 20, 95, NekoConfig.mainTabsGlassAlpha, NekoConfig.forceMainTabsBlur, null);
                case ConfigCellCustom.CUSTOM_ITEM_MainTabsBlurRadius -> new BlurSliderCell(mContext, getString(R.string.MainTabsBlurRadius), "dp", 4, 32, NekoConfig.mainTabsBlurRadius, NekoConfig.forceMainTabsBlur, null);
                case ConfigCellCustom.CUSTOM_ITEM_ActionBarGlassAlpha -> new BlurSliderCell(mContext, getString(R.string.ActionBarGlassAlpha), "%", 20, 95, NekoConfig.actionBarGlassAlpha, NekoConfig.forceActionBarBlur, null);
                case ConfigCellCustom.CUSTOM_ITEM_ActionBarBlurRadius -> new BlurSliderCell(mContext, getString(R.string.ActionBarBlurRadius), "dp", 4, 32, NekoConfig.actionBarBlurRadius, NekoConfig.forceActionBarBlur, null);
                default -> null;
            };
        }
    }

    public static class BlurSliderCell extends FrameLayout {
        private final TextView titleTextView;
        private final TextView valueTextView;
        private final ImageView resetButton;
        private final SeekBarView seekBarView;
        private final String unit;
        private final int min;
        private final int max;
        private final int defaultValue;
        private final ConfigItem configItem;
        private final ConfigItem parentSwitch;

        public BlurSliderCell(Context context, String title, String unit, int min, int max, ConfigItem configItem, ConfigItem parentSwitch, Runnable onValueChange) {
            super(context);
            boolean m3Expressive = xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive();
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.configItem = configItem;
            this.parentSwitch = parentSwitch;
            this.defaultValue = configItem.defaultValue instanceof Integer ? (Integer) configItem.defaultValue : min;

            setFocusable(false);
            setFocusableInTouchMode(false);

            titleTextView = new TextView(context);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, m3Expressive ? 15.5f : 14.5f);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTextView.setText(title);
            addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 21, m3Expressive ? 11 : 8, m3Expressive ? 126 : 110, 0));

            LinearLayout rightContainer = new LinearLayout(context);
            rightContainer.setOrientation(LinearLayout.HORIZONTAL);
            rightContainer.setGravity(Gravity.CENTER_VERTICAL);
            seekBarView = new SeekBarView(context);
            seekBarView.setReportChanges(true);
            seekBarView.setDelegate((stop, progress) -> {
                if (parentSwitch != null && !parentSwitch.Bool()) {
                    return;
                }
                int val = min + Math.round(progress * (max - min));
                configItem.setConfigInt(val);
                updateText(val);
                if (onValueChange != null) {
                    onValueChange.run();
                }
            });
            addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, m3Expressive ? 44 : 36, Gravity.TOP | Gravity.LEFT, 9, m3Expressive ? 31 : 26, 9, 6));

            resetButton = new ImageView(context);
            resetButton.setFocusable(false);
            resetButton.setImageResource(R.drawable.msg_retry);
            resetButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            resetButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
            resetButton.setBackground(m3Expressive
                    ? Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14), 0, Theme.getColor(Theme.key_listSelector))
                    : Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
            resetButton.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2));
            resetButton.setOnClickListener(v -> {
                if (parentSwitch != null && !parentSwitch.Bool()) {
                    return;
                }
                configItem.setConfigInt(defaultValue);
                seekBarView.setProgress((defaultValue - min) / (float) (max - min));
                updateText(defaultValue);
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (onValueChange != null) {
                    onValueChange.run();
                }
            });
            LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(m3Expressive ? 28 : 20), AndroidUtilities.dp(m3Expressive ? 28 : 20));
            resetParams.rightMargin = AndroidUtilities.dp(m3Expressive ? 8 : 6);
            rightContainer.addView(resetButton, resetParams);

            valueTextView = new TextView(context);
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, m3Expressive ? 15f : 14.5f);
            valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            rightContainer.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            addView(rightContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, m3Expressive ? 32 : LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, m3Expressive ? 7 : 7, 21, 0));

            int currentVal = configItem.Int();
            if (currentVal < min) currentVal = min;
            if (currentVal > max) currentVal = max;
            seekBarView.setProgress((currentVal - min) / (float) (max - min));
            updateText(currentVal);
            updateEnabledState(false);
        }

        public void updateEnabledState(boolean animate) {
            boolean enabled = parentSwitch == null || parentSwitch.Bool();
            setEnabled(enabled);
            seekBarView.setEnabled(enabled);
            resetButton.setEnabled(enabled);
            float targetAlpha = enabled ? 1.0f : 0.38f;
            if (animate) {
                titleTextView.animate().alpha(targetAlpha).setDuration(180).start();
                valueTextView.animate().alpha(targetAlpha).setDuration(180).start();
                seekBarView.animate().alpha(targetAlpha).setDuration(180).start();
                resetButton.animate().alpha(targetAlpha).setDuration(180).start();
            } else {
                titleTextView.setAlpha(targetAlpha);
                valueTextView.setAlpha(targetAlpha);
                seekBarView.setAlpha(targetAlpha);
                resetButton.setAlpha(targetAlpha);
            }
            int currentVal = configItem.Int();
            resetButton.setVisibility(currentVal != defaultValue ? VISIBLE : INVISIBLE);
        }

        public void updateEnabledState() {
            updateEnabledState(false);
        }

        private void updateText(int val) {
            valueTextView.setText(val + " " + unit);
            resetButton.setVisibility(val != defaultValue ? VISIBLE : INVISIBLE);
        }

        private boolean needDivider;

        public void setNeedDivider(boolean needDivider) {
            if (this.needDivider != needDivider) {
                this.needDivider = needDivider;
                setWillNotDraw(!needDivider);
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (parentSwitch != null && !parentSwitch.Bool()) {
                return true;
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (parentSwitch != null && !parentSwitch.Bool()) {
                return false;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive() ? 78 : 66), MeasureSpec.EXACTLY));
        }
    }
}
