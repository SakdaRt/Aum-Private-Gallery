package ca.pkay.rcloneexplorer.gallery;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validate completely before publishing a snapshot, so failures retain the previous list. */
public final class GalleryCatalog {
    private GalleryCatalog() { }
    public static List<JSONObject> parse(JSONObject catalog) throws JSONException {
        int version = catalog.optInt("version");
        if (version != 1 && version != 2) throw new JSONException("Unsupported catalog version");
        JSONArray videos = catalog.getJSONArray("videos");
        if (videos.length() > 10000) throw new JSONException("Too many videos");
        List<JSONObject> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.getJSONObject(i);
            String id = video.getString("id");
            if (!GalleryPaths.video(id, video.getString("extension"), video.getString("path"))
                    || !("PrivateLibrary/thumbnails/" + id + ".jpg").equals(video.getString("thumb"))
                    || !ids.add(id) || video.getString("title").length() > 1024
                    || video.getLong("durationMs") < 0 || video.optLong("size", 0) < 0
                    || video.getLong("added") < 0)
                throw new JSONException("Invalid or duplicate catalog entry");
            result.add(video);
        }
        return result;
    }
    public static void appendRecord(JSONObject catalog, String fileName, JSONObject record) throws JSONException {
        JSONObject video = record.getJSONObject("video");
        if (record.getInt("version") != 2 || !fileName.equals(video.getString("id") + ".json"))
            throw new JSONException("Invalid metadata version or ID");
        parse(new JSONObject().put("version", 2).put("videos", new JSONArray().put(video)));
        catalog.getJSONArray("videos").put(video);
    }
}
