package app.revanced.integrations.patches;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.utils.LogHelper;
import app.revanced.integrations.utils.ReVancedUtils;

class BlockRule {
    final static class BlockResult {
        private final boolean blocked;
        private final SettingsEnum setting;

        public BlockResult(final SettingsEnum setting, final boolean blocked) {
            this.setting = setting;
            this.blocked = blocked;
        }

        public SettingsEnum getSetting() {
            return setting;
        }

        public boolean isBlocked() {
            return blocked;
        }
    }

    protected final SettingsEnum setting;
    private final String[] blocks;

    /**
     * Initialize a new rule for components.
     *
     * @param setting The setting which controls the blocking of this component.
     * @param blocks  The rules to block the component on.
     */
    public BlockRule(final SettingsEnum setting, final String... blocks) {
        this.setting = setting;
        this.blocks = blocks;
    }

    public boolean isEnabled() {
        return setting.getBoolean();
    }

    public BlockResult check(final String string) {
        return new BlockResult(setting, string != null && ReVancedUtils.containsAny(string, blocks));
    }
}

final class CustomBlockRule extends BlockRule {
    /**
     * Initialize a new rule for components.
     *
     * @param setting The setting which controls the blocking of the components.
     * @param filter  The setting which contains the list of component names.
     */
    public CustomBlockRule(final SettingsEnum setting, final SettingsEnum filter) {
        super(setting, filter.getString().split(","));
    }
}


abstract class Filter {
    final protected LithoBlockRegister pathRegister = new LithoBlockRegister();
    final protected LithoBlockRegister identifierRegister = new LithoBlockRegister();

    abstract boolean filter(final String path, final String identifier);
}

final class LithoBlockRegister implements Iterable<BlockRule> {
    private final ArrayList<BlockRule> blocks = new ArrayList<>();

    public void registerAll(BlockRule... blocks) {
        this.blocks.addAll(Arrays.asList(blocks));
    }

    @NonNull
    @Override
    public Iterator<BlockRule> iterator() {
        return blocks.iterator();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void forEach(@NonNull Consumer<? super BlockRule> action) {
        blocks.forEach(action);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @NonNull
    @Override
    public Spliterator<BlockRule> spliterator() {
        return blocks.spliterator();
    }

    public boolean contains(String path) {
        for (var rule : this) {
            if (!rule.isEnabled()) continue;

            var result = rule.check(path);
            if (result.isBlocked()) {
                return true;
            }
        }

        return false;
    }
}

public final class LithoFilterPatch {
    private static final Filter[] filters = new Filter[]{
            new GeneralBytecodeAdsPatch(),
            new ButtonsPatch(),
            new CommentsPatch(),
    };

    public static boolean filter(final StringBuilder pathBuilder, final String identifier) {
        var path = pathBuilder.toString();
        if (path.isEmpty()) return false;

        LogHelper.debug(LithoFilterPatch.class, String.format("Searching (ID: %s): %s", identifier, path));

        for (var filter : filters) {
            if (filter.filter(path, identifier)) return true;
        }

        return false;
    }
}

final class CommentsPatch extends Filter {

    public CommentsPatch() {
        var comments = new BlockRule(SettingsEnum.HIDE_COMMENTS_SECTION, "video_metadata_carousel", "_comments");
        var previewComment = new BlockRule(
                SettingsEnum.HIDE_PREVIEW_COMMENT,
                "carousel_item",
                "comments_entry_point_teaser",
                "comments_entry_point_simplebox"
        );

        this.pathRegister.registerAll(
                comments,
                previewComment
        );
    }

    @Override
    boolean filter(String path, String _identifier) {
        if (!pathRegister.contains(path)) return false;

        LogHelper.debug(CommentsPatch.class, "Blocked: " + path);

        return true;
    }
}

final class ButtonsPatch extends Filter {
    private final BlockRule actionButtonsRule;
    private final BlockRule dislikeRule;
    private final BlockRule actionBarRule;

    private final BlockRule[] rules;

    public ButtonsPatch() {
        BlockRule like = new BlockRule(SettingsEnum.HIDE_LIKE_BUTTON, "|like_button");
        dislikeRule = new BlockRule(SettingsEnum.HIDE_DISLIKE_BUTTON, "dislike_button");
        BlockRule download = new BlockRule(SettingsEnum.HIDE_DOWNLOAD_BUTTON, "download_button");
        actionButtonsRule = new BlockRule(SettingsEnum.HIDE_ACTION_BUTTON, "ContainerType|video_action_button");
        BlockRule playlist = new BlockRule(SettingsEnum.HIDE_PLAYLIST_BUTTON, "save_to_playlist_button");
        rules = new BlockRule[]{like, dislikeRule, download, actionButtonsRule, playlist};

        actionBarRule = new BlockRule(null, "video_action_bar");

        this.pathRegister.registerAll(
                like,
                dislikeRule,
                download,
                playlist
        );
    }

    private boolean hideActionBar() {
        for (BlockRule rule : rules) if (!rule.isEnabled()) return false;
        return true;
    }

    @Override
    public boolean filter(final String path, final String identifier) {
        if (hideActionBar() && actionBarRule.check(identifier).isBlocked()) return true;

        var currentIsActionButton = actionButtonsRule.check(path).isBlocked();

        if (dislikeRule.check(path).isBlocked()) ActionButton.doNotBlockCounter = 4;

        if (currentIsActionButton && ActionButton.doNotBlockCounter-- > 0) {
            if (SettingsEnum.HIDE_SHARE_BUTTON.getBoolean()) {
                LogHelper.debug(ButtonsPatch.class, "Hiding share button");
                return true;
            } else return false;
        }

        if ((currentIsActionButton && ActionButton.doNotBlockCounter <= 0 && actionButtonsRule.isEnabled()) || pathRegister.contains(path)) {
            LogHelper.debug(ButtonsPatch.class, "Blocked: " + path);
            return true;
        } else return false;
    }

    static class ActionButton {
        public static int doNotBlockCounter = 4;
    }
}

final class GeneralBytecodeAdsPatch extends Filter {
    public GeneralBytecodeAdsPatch() {
        var communityPosts = new BlockRule(SettingsEnum.ADREMOVER_COMMUNITY_POSTS_REMOVAL, "post_base_wrapper");
        var communityGuidelines = new BlockRule(SettingsEnum.ADREMOVER_COMMUNITY_GUIDELINES_REMOVAL, "community_guidelines");
        var compactBanner = new BlockRule(SettingsEnum.ADREMOVER_COMPACT_BANNER_REMOVAL, "compact_banner");
        var inFeedSurvey = new BlockRule(SettingsEnum.ADREMOVER_FEED_SURVEY_REMOVAL, "in_feed_survey");
        var medicalPanel = new BlockRule(SettingsEnum.ADREMOVER_MEDICAL_PANEL_REMOVAL, "medical_panel");
        var paidContent = new BlockRule(SettingsEnum.ADREMOVER_PAID_CONTENT_REMOVAL, "paid_content_overlay");
        var merchandise = new BlockRule(SettingsEnum.ADREMOVER_MERCHANDISE_REMOVAL, "product_carousel");
        var infoPanel = new BlockRule(SettingsEnum.ADREMOVER_INFO_PANEL_REMOVAL, "publisher_transparency_panel", "single_item_information_panel");
        var suggestions = new BlockRule(SettingsEnum.ADREMOVER_SUGGESTIONS_REMOVAL, "horizontal_video_shelf");
        var latestPosts = new BlockRule(SettingsEnum.ADREMOVER_HIDE_LATEST_POSTS, "post_shelf");
        var channelGuidelines = new BlockRule(SettingsEnum.ADREMOVER_HIDE_CHANNEL_GUIDELINES, "channel_guidelines_entry_banner");
        var artistCard = new BlockRule(SettingsEnum.HIDE_ARTIST_CARD, "official_card");
        var selfSponsor = new BlockRule(SettingsEnum.ADREMOVER_SELF_SPONSOR_REMOVAL, "cta_shelf_card");
        var chapterTeaser = new BlockRule(SettingsEnum.ADREMOVER_CHAPTER_TEASER_REMOVAL, "expandable_metadata");

        var generalAds = new BlockRule(
                SettingsEnum.ADREMOVER_GENERAL_ADS_REMOVAL,
                // could be required
                //"full_width_square_image_layout",
                "video_display_full_buttoned_layout",
                "_ad",
                "ad_",
                "ads_video_with_context",
                "banner_text_icon",
                "cell_divider",
                "reels_player_overlay",
                "watch_metadata_app_promo",
                "video_display_full_layout"
        );
        var movieAds = new BlockRule(
                SettingsEnum.ADREMOVER_MOVIE_REMOVAL,
                "browsy_bar",
                "compact_movie",
                "horizontal_movie_shelf",
                "movie_and_show_upsell_card"
        );

        this.pathRegister.registerAll(
                generalAds,
                communityPosts,
                paidContent,
                suggestions,
                latestPosts,
                movieAds,
                chapterTeaser,
                communityGuidelines,
                compactBanner,
                inFeedSurvey,
                medicalPanel,
                merchandise,
                infoPanel,
                channelGuidelines,
                artistCard,
                selfSponsor
        );

        var carouselAd = new BlockRule(SettingsEnum.ADREMOVER_GENERAL_ADS_REMOVAL, "carousel_ad");
        var shorts = new BlockRule(SettingsEnum.ADREMOVER_SHORTS_REMOVAL, "shorts_shelf", "inline_shorts");

        this.identifierRegister.registerAll(
                shorts,
                carouselAd
        );
    }

    public boolean filter(final String path, final String identifier) {
        // Do not block on these
        if (ReVancedUtils.containsAny(path,
                "home_video_with_context",
                "related_video_with_context",
                "comment_thread", // skip blocking anything in the comments
                "download_",
                "library_recent_shelf",
                "menu",
                "root",
                "-count",
                "-space",
                "-button",
                "playlist_add_to_option_wrapper" // do not block on "add to playlist" flyout menu
        )) return false;

        if (!(Extensions.any(pathRegister, path) || Extensions.any(identifierRegister, identifier)))
            return false;

        LogHelper.debug(GeneralBytecodeAdsPatch.class, String.format("Blocked (ID: %s): %s", identifier, path));

        return true;
    }
}
