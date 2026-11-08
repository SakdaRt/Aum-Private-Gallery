package ca.pkay.rcloneexplorer.gallery;

/** Strict wire-format validation, shared by the catalog and player. */
public final class GalleryPaths {
    private GalleryPaths() { }
    public static boolean safeId(String id) {
        return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    public static boolean extension(String ext) {
        return ext != null && ext.matches("\\.(mp4|m4v|mov|webm|mkv|3gp)");
    }
    public static boolean video(String id, String ext, String path) {
        return safeId(id) && extension(ext) && ("PrivateLibrary/videos/" + id + ext).equals(path);
    }
    public static boolean videoPath(String path) {
        return path != null && path.matches("PrivateLibrary/videos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(mp4|m4v|mov|webm|mkv|3gp)");
    }
}
