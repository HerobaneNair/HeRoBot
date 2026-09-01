package hero.bane.herobot.paper.networking;

import java.util.List;

public final class HeroBotChannels {

    public static final int PROTOCOL = 1;

    public static final String SYNC = "herobot:sync";
    public static final String CONTROL = "herobot:control";
    public static final String PATH_DONE = "herobot:path_done";

    public static final String AI_LIST_REQUEST = "herobot:ai_list_request";
    public static final String AI_LIST = "herobot:ai_list";
    public static final String AI_DOWNLOAD_REQUEST = "herobot:ai_download_request";
    public static final String AI_DOWNLOAD = "herobot:ai_download";
    public static final String AI_DOWNLOAD_FAILED = "herobot:ai_download_failed";
    public static final String AI_UPLOAD = "herobot:ai_upload";
    public static final String AI_DELETE_REQUEST = "herobot:ai_delete_request";

    public static final List<String> INCOMING = List.of(
            PATH_DONE, AI_LIST_REQUEST, AI_DOWNLOAD_REQUEST, AI_UPLOAD, AI_DELETE_REQUEST);

    public static final List<String> OUTGOING = List.of(
            SYNC, CONTROL, AI_LIST, AI_DOWNLOAD, AI_DOWNLOAD_FAILED);

    private HeroBotChannels() {
    }
}
