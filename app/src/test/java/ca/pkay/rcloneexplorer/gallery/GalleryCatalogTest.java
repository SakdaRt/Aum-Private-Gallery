package ca.pkay.rcloneexplorer.gallery;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class GalleryCatalogTest {
    private static final String ID = "12345678-1234-1234-1234-123456789abc";
    private JSONObject video(String id) throws Exception {
        return new JSONObject().put("id", id).put("extension", ".mp4").put("title", "คลิป")
                .put("path", "PrivateLibrary/videos/" + id + ".mp4")
                .put("thumb", "PrivateLibrary/thumbnails/" + id + ".jpg")
                .put("durationMs", 1000).put("added", 1).put("size", 1024);
    }
    private JSONObject catalog() throws Exception {
        return new JSONObject().put("version", 1).put("videos", new JSONArray().put(video(ID)));
    }
    @Test public void readsLegacyWithoutChangingInput() throws Exception {
        JSONObject c = catalog();
        assertEquals(1, GalleryCatalog.parse(c).size());
        assertEquals(1, c.getInt("version"));
    }
    @Test public void mergesIndependentDeviceAdditions() throws Exception {
        JSONObject c = catalog();
        String other = "aaaaaaaa-1234-1234-1234-123456789abc";
        GalleryCatalog.appendRecord(c, other + ".json", new JSONObject().put("version", 2).put("video", video(other)));
        assertEquals(2, GalleryCatalog.parse(c).size());
    }
    @Test(expected = JSONException.class) public void rejectsDuplicateIdentity() throws Exception {
        JSONObject c = catalog();
        c.getJSONArray("videos").put(video(ID));
        GalleryCatalog.parse(c);
    }
    @Test(expected = JSONException.class) public void rejectsTraversal() throws Exception {
        JSONObject c = catalog();
        c.getJSONArray("videos").getJSONObject(0).put("path", "PrivateLibrary/videos/../secret.mp4");
        GalleryCatalog.parse(c);
    }
    @Test(expected = JSONException.class) public void rejectsUnknownSchema() throws Exception {
        GalleryCatalog.parse(catalog().put("version", 99));
    }
    @Test(expected = JSONException.class) public void rejectsMismatchedRecordName() throws Exception {
        GalleryCatalog.appendRecord(catalog(), "wrong.json", new JSONObject().put("version", 2).put("video", video(ID)));
    }
    @Test(expected = JSONException.class) public void rejectsMalformedEntries() throws Exception {
        JSONObject c = catalog();
        c.getJSONArray("videos").put(new JSONObject());
        GalleryCatalog.parse(c);
    }
    @Test public void pathValidationRejectsEncodedAndAlternatePaths() {
        assertTrue(GalleryPaths.videoPath("PrivateLibrary/videos/" + ID + ".mp4"));
        assertFalse(GalleryPaths.safeId("------------------------------------"));
        assertFalse(GalleryPaths.videoPath("PrivateLibrary/videos/%2e%2e/secret.mp4"));
        assertFalse(GalleryPaths.video(ID, ".exe", "PrivateLibrary/videos/" + ID + ".exe"));
    }
}
