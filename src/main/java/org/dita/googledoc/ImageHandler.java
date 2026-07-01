package org.dita.googledoc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class ImageHandler {

    private static final double PX_TO_PT = 0.75;

    private final GoogleDocApiClient apiClient;
    private final String tempDir;
    private final String folderId;
    private final double maxWidthPt;

    public record ImageInfo(String uri, double widthPt, double heightPt) {}

    public ImageHandler(GoogleDocApiClient apiClient, String tempDir,
                        String folderId, double maxWidthPt) {
        this.apiClient = apiClient;
        this.tempDir = tempDir;
        this.folderId = folderId;
        this.maxWidthPt = maxWidthPt;
    }

    public ImageInfo resolveImage(String href) throws IOException {
        String absolutePath = Path.of(tempDir).resolve(href).toString();

        double[] dims = calculateDimensions(absolutePath, maxWidthPt);
        String uri = apiClient.uploadImage(absolutePath, folderId);

        return new ImageInfo(uri, dims[0], dims[1]);
    }

    static double[] calculateDimensions(String imagePath, double maxWidthPt) throws IOException {
        BufferedImage img = ImageIO.read(new java.io.File(imagePath));
        if (img == null) {
            return new double[]{maxWidthPt, maxWidthPt};
        }

        double widthPt = img.getWidth() * PX_TO_PT;
        double heightPt = img.getHeight() * PX_TO_PT;

        if (widthPt > maxWidthPt) {
            double scale = maxWidthPt / widthPt;
            widthPt = maxWidthPt;
            heightPt = heightPt * scale;
        }

        return new double[]{widthPt, heightPt};
    }
}
